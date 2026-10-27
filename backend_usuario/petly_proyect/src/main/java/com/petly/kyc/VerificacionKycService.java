package com.petly.kyc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.petly.common.exception.ApiException;

/** Coordina RENIEC y conserva el resultado en el registro KYC de Django. */
@Service
public class VerificacionKycService {

    private static final Set<String> ESTADOS_TERMINALES = Set.of(
            "VERIFICADO", "APROBADO_MANUAL", "RECHAZADO_MANUAL");
    private static final String ESTADOS_BLOQUEADOS_SQL = "'VERIFICADO','APROBADO_MANUAL','RECHAZADO_MANUAL','PENDIENTE_REVISION'";

    private final JdbcTemplate jdbc;
    private final ReniecClient reniec;

    public VerificacionKycService(JdbcTemplate jdbc, ReniecClient reniec) {
        this.jdbc = jdbc;
        this.reniec = reniec;
    }

    public Map<String, Object> verificar(long verificacionId, long adoptanteSpringId) {
        Candidato candidato = jdbc.query(
                "SELECT k.dni,k.nombres,k.apellidos,k.estado,k.coincide,k.referencia_reniec,k.fecha_consulta,"
                        + "k.documento_anverso,k.documento_reverso "
                        + "FROM acceso_verificacionkyc k JOIN acceso_user u ON u.id=k.adoptante_id "
                        + "WHERE k.id=? AND u.spring_id=?",
                (rs, row) -> candidato(rs), verificacionId, adoptanteSpringId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(404, "No se encontró la verificación KYC del adoptante."));

        if (estadoTerminal(candidato.estado())) {
            return respuesta(verificacionId, candidato.estado(), candidato.coincide(), candidato.referencia(), candidato.fechaConsulta());
        }
        if (estadoPendienteRevision(candidato.estado())) {
            throw new ApiException(409, "La verificación está pendiente de revisión administrativa.");
        }
        if (!StringUtils.hasText(candidato.anverso()) || !StringUtils.hasText(candidato.reverso())) {
            throw new ApiException(409, "Debes cargar el anverso y reverso del DNI antes de verificarlo.");
        }

        final ReniecResponse resultado;
        try {
            resultado = reniec.consultar(candidato.dni(), candidato.nombres(), candidato.apellidos());
        } catch (ReniecNoConfiguradoException | ReniecNoDisponibleException | ReniecRespuestaInvalidaException ex) {
            registrarFallo(verificacionId, adoptanteSpringId, candidato, ex.getMessage());
            throw new ApiException(503, "No se pudo consultar RENIEC. La identidad permanece pendiente.");
        }

        // ApiPeruDev uses public sources and is not an official RENIEC identity check.
        // Keep its matches for a human review instead of marking identity as verified.
        boolean fuenteNoOficial = "ApiPeruDev".equalsIgnoreCase(reniec.getProvider());
        String estado = resultado.coincide() && !fuenteNoOficial
                ? "VERIFICADO"
                : "PENDIENTE_REVISION";
        int actualizados = jdbc.update(
                "UPDATE acceso_verificacionkyc k JOIN acceso_user u ON u.id=k.adoptante_id "
                        + "SET k.estado=?,k.coincide=?,k.proveedor=?,k.referencia_reniec=?,"
                        + "k.fecha_consulta=CURRENT_TIMESTAMP,k.ultimo_intento_en=CURRENT_TIMESTAMP,"
                        + "k.error_servicio='',k.actualizado_en=CURRENT_TIMESTAMP "
                        + "WHERE k.id=? AND u.spring_id=? AND k.estado NOT IN (" + ESTADOS_BLOQUEADOS_SQL + ") "
                        + "AND k.dni=? AND k.nombres=? AND k.apellidos=?",
                estado, resultado.coincide(), reniec.getProvider(), resultado.referencia(),
                verificacionId, adoptanteSpringId, candidato.dni(), candidato.nombres(), candidato.apellidos());
        if (actualizados != 1) {
            throw new ApiException(409, "La verificación cambió mientras se consultaba RENIEC. Actualiza el estado.");
        }
        return respuesta(verificacionId, estado, resultado.coincide(), resultado.referencia(),
                jdbc.queryForObject(
                        "SELECT fecha_consulta FROM acceso_verificacionkyc WHERE id=?",
                        (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), verificacionId));
    }

    static boolean estadoTerminal(String estado) {
        return ESTADOS_TERMINALES.contains(estado);
    }

    static boolean estadoPendienteRevision(String estado) {
        return "PENDIENTE_REVISION".equals(estado);
    }

    private void registrarFallo(long id, long springId, Candidato candidato, String detalle) {
        jdbc.update(
                "UPDATE acceso_verificacionkyc k JOIN acceso_user u ON u.id=k.adoptante_id "
                        + "SET k.ultimo_intento_en=CURRENT_TIMESTAMP,k.error_servicio=?,"
                        + "k.actualizado_en=CURRENT_TIMESTAMP "
                        + "WHERE k.id=? AND u.spring_id=? AND k.estado NOT IN (" + ESTADOS_BLOQUEADOS_SQL + ") "
                        + "AND k.dni=? AND k.nombres=? AND k.apellidos=?",
                detalle, id, springId, candidato.dni(), candidato.nombres(), candidato.apellidos());
    }

    private Candidato candidato(ResultSet rs) throws SQLException {
        var consultadoEn = rs.getTimestamp("fecha_consulta");
        return new Candidato(
                rs.getString("dni"), rs.getString("nombres"), rs.getString("apellidos"),
                rs.getString("estado"), (Boolean) rs.getObject("coincide"),
                rs.getString("referencia_reniec"), consultadoEn == null ? null : consultadoEn.toLocalDateTime(),
                rs.getString("documento_anverso"), rs.getString("documento_reverso"));
    }

    private Map<String, Object> respuesta(long id, String estado, Boolean coincide, String referencia, Object consultadoEn) {
        var respuesta = new java.util.LinkedHashMap<String, Object>();
        respuesta.put("id", id);
        respuesta.put("estado", estado);
        respuesta.put("coincide", coincide);
        respuesta.put("referencia", referencia == null ? "" : referencia);
        respuesta.put("fechaConsulta", consultadoEn == null ? "" : consultadoEn.toString());
        return respuesta;
    }

    private record Candidato(
            String dni, String nombres, String apellidos, String estado, Boolean coincide,
            String referencia, java.time.LocalDateTime fechaConsulta, String anverso, String reverso) {}
}
