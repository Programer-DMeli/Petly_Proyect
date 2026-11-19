package com.petly.privacidad;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.petly.auth.RecuperacionTokenRepository;
import com.petly.auth.SesionRenovableService;
import com.petly.common.exception.ApiException;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;

@Service
public class PrivacidadService {

    private static final String RUTA_EXPORTACION = "/api/acceso/interno/privacidad/exportacion/";
    private static final String RUTA_BAJA = "/api/acceso/interno/privacidad/baja/";

    private final SolicitudPrivacidadRepository operaciones;
    private final UsuarioRepository usuarios;
    private final RecuperacionTokenRepository tokens;
    private final SesionRenovableService sesiones;
    private final RestTemplate http;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;
    private final String djangoBaseUrl;
    private final String secretoInterno;

    public PrivacidadService(
            SolicitudPrivacidadRepository operaciones,
            UsuarioRepository usuarios,
            RecuperacionTokenRepository tokens,
            SesionRenovableService sesiones,
            RestTemplate http,
            JdbcTemplate jdbc,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            @Value("${petly.django-api-base-url}") String djangoBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.operaciones = operaciones;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.sesiones = sesiones;
        this.http = http;
        this.jdbc = jdbc;
        this.transacciones = new TransactionTemplate(transactionManager);
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    public Map<String, Object> exportar(long usuarioId, UUID operacionId) {
        Usuario usuario = adoptanteActivo(usuarioId);
        SolicitudPrivacidad operacion = obtenerOCrear(operacionId, usuarioId, SolicitudPrivacidad.Tipo.EXPORTACION);
        Map<String, Object> datosDjango;
        try {
            Map<String, Object> solicitud = Map.of("usuario_spring_id", usuarioId);
            datosDjango = llamarDjango(RUTA_EXPORTACION, solicitud, Map.class);
        } catch (HttpStatusCodeException ex) {
            operacion.marcarError("Django respondió HTTP " + ex.getStatusCode().value() + " al exportar.");
            operaciones.save(operacion);
            throw new ApiException(502, "No se pudo preparar la exportación de datos.");
        } catch (ResourceAccessException ex) {
            operacion.marcarError("Django no está disponible para exportar.");
            operaciones.save(operacion);
            throw new ApiException(503, "El servicio de datos no está disponible. Intenta nuevamente.");
        } catch (RestClientException ex) {
            operacion.marcarError("Django no devolvió una exportación válida.");
            operaciones.save(operacion);
            throw new ApiException(502, "Django devolvió una respuesta inválida para la exportación.");
        }
        if (datosDjango == null) throw new ApiException(502, "Django devolvió una exportación vacía.");

        operacion.completar();
        operaciones.save(operacion);
        Map<String, Object> exportacion = new LinkedHashMap<>();
        exportacion.put("formato", "petly-portabilidad-v1");
        exportacion.put("operacion_id", operacionId);
        exportacion.put("generado_en", LocalDateTime.now());
        exportacion.put("spring_boot", exportarDatosSpring(usuario));
        exportacion.put("django", datosDjango);
        return exportacion;
    }

    public Map<String, Object> solicitarBaja(long usuarioId, UUID operacionId) {
        adoptanteActivo(usuarioId);
        SolicitudPrivacidad operacion = obtenerOCrear(operacionId, usuarioId, SolicitudPrivacidad.Tipo.BAJA);
        if (operacion.getEstado() == SolicitudPrivacidad.Estado.COMPLETADA) return respuesta(operacion);
        procesarBaja(operacion);
        return respuesta(operaciones.findById(operacionId.toString()).orElse(operacion));
    }

    public Map<String, Object> estado(long usuarioId, UUID operacionId) {
        SolicitudPrivacidad operacion = operaciones.findByOperacionIdAndUsuarioId(operacionId.toString(), usuarioId)
                .orElseThrow(() -> new ApiException(404, "No se encontró la operación de privacidad."));
        return respuesta(operacion);
    }

    @Scheduled(fixedDelayString = "${petly.privacidad.reintento-ms:60000}")
    public void reintentarBajasPendientes() {
        List<SolicitudPrivacidad> pendientes = operaciones.findTop100ByTipoAndEstadoInOrderByCreadoEnAsc(
                SolicitudPrivacidad.Tipo.BAJA,
                List.of(SolicitudPrivacidad.Estado.PENDIENTE_DJANGO, SolicitudPrivacidad.Estado.PENDIENTE_ARCHIVOS));
        for (SolicitudPrivacidad operacion : pendientes) {
            try {
                procesarBaja(operacion);
            } catch (RuntimeException ex) {
                operacion.marcarPendienteDjango("No se pudo completar la coordinación; se reintentará.");
                operaciones.save(operacion);
            }
        }
    }

    private void procesarBaja(SolicitudPrivacidad operacion) {
        try {
            Map<String, Object> body = Map.of(
                    "usuario_spring_id", operacion.getUsuarioId(),
                    "operacion_id", operacion.getOperacionId(),
                    "confirmar", true);
            Map<String, Object> resultado = llamarDjango(RUTA_BAJA, body, Map.class);
            String estadoDjango = Objects.toString(resultado.get("estado"), "");
            List<String> retenciones = convertirRetenciones(resultado.get("retenciones"));
            if ("COMPLETADA".equals(estadoDjango)) {
                finalizarCuenta(operacion, true, retenciones);
            } else if ("ARCHIVOS_PENDIENTES".equals(estadoDjango)) {
                finalizarCuenta(operacion, false, retenciones);
            } else {
                operacion.marcarPendienteDjango("Django aún no confirmó la baja.");
                operacion.guardarRetenciones(retenciones);
                operaciones.save(operacion);
            }
        } catch (HttpStatusCodeException ex) {
            operacion.marcarPendienteDjango("Django respondió HTTP " + ex.getStatusCode().value() + "; se reintentará.");
            operaciones.save(operacion);
        } catch (ResourceAccessException ex) {
            operacion.marcarPendienteDjango("Django no está disponible; se reintentará.");
            operaciones.save(operacion);
        } catch (RestClientException ex) {
            operacion.marcarPendienteDjango("Django devolvió una respuesta inválida; se reintentará.");
            operaciones.save(operacion);
        }
    }

    private void finalizarCuenta(SolicitudPrivacidad operacion, boolean djangoCompleto, List<String> retenciones) {
        transacciones.executeWithoutResult(estado -> {
            Usuario usuario = usuarios.bloquearPorId(operacion.getUsuarioId()).orElse(null);
            if (usuario != null && !usuario.isCuentaEliminada()) {
                limpiarDatosPropiosSpring(usuario.getId());
                sesiones.revocarTodas(usuario.getId());
                sesiones.eliminarTodas(usuario.getId());
                tokens.deleteByUsuarioId(usuario.getId());
                usuario.setUsername("eliminado_" + usuario.getId() + "_" + UUID.randomUUID().toString().substring(0, 8));
                usuario.setCorreo("eliminado+" + usuario.getId() + "+" + UUID.randomUUID().toString().substring(0, 8) + "@petly.invalid");
                usuario.setPasswordHash("!" + UUID.randomUUID());
                usuario.setFirstName("");
                usuario.setLastName("");
                usuario.setTelefono("");
                usuario.setFechaNacimiento(null);
                usuario.setAceptoTerminos(false);
                usuario.setFechaAceptacionTerminos(null);
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(null);
                usuario.setCuentaEliminada(true);
                usuario.setTokenVersion(usuario.getTokenVersion() + 1);
                usuarios.save(usuario);
            }
            operacion.guardarRetenciones(retenciones);
            if (djangoCompleto) operacion.completar();
            else operacion.marcarPendienteArchivos();
            operaciones.save(operacion);
        });
    }

    private void limpiarDatosPropiosSpring(long usuarioId) {
        jdbc.update("DELETE FROM favoritos_mascotas WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM disponibilidad_adoptante WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM preferencias_notificacion WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM autorizaciones_consulta WHERE adoptante_id = ?", usuarioId);
        jdbc.update("DELETE FROM recordatorios_seguimiento WHERE adoptante_id = ?", usuarioId);
        jdbc.update("DELETE FROM entregas_aviso_coincidencia WHERE adoptante_id = ?", usuarioId);
        jdbc.update("DELETE FROM resenas_albergue WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM reacciones_caso_exito WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM comentarios_caso_exito WHERE usuario_id = ?", usuarioId);
        jdbc.update("DELETE FROM alertas_compatibilidad_albergue WHERE adoptante_id = ?", usuarioId);
    }

    private Map<String, Object> exportarDatosSpring(Usuario usuario) {
        long id = usuario.getId();
        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("id", id);
        cuenta.put("username", usuario.getUsername());
        cuenta.put("correo", usuario.getCorreo());
        cuenta.put("rol", usuario.getRol().name());
        cuenta.put("nombres", usuario.getFirstName());
        cuenta.put("apellidos", usuario.getLastName());
        cuenta.put("telefono", usuario.getTelefono());
        cuenta.put("fecha_nacimiento", usuario.getFechaNacimiento());
        cuenta.put("creada_en", usuario.getCreatedAt());
        cuenta.put("acepto_terminos", usuario.getAceptoTerminos());
        cuenta.put("fecha_aceptacion_terminos", usuario.getFechaAceptacionTerminos());

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("cuenta", cuenta);
        datos.put("favoritos", jdbc.queryForList(
                "SELECT mascota_id, creado_en FROM favoritos_mascotas WHERE usuario_id = ? ORDER BY creado_en, mascota_id", id));
        datos.put("disponibilidad", jdbc.queryForList(
                "SELECT dia_semana, hora_inicio, hora_fin, descripcion FROM disponibilidad_adoptante WHERE usuario_id = ? ORDER BY id", id));
        datos.put("preferencias_notificacion", jdbc.queryForList(
                "SELECT email, whatsapp, push, actualizado_en FROM preferencias_notificacion WHERE usuario_id = ?", id));
        datos.put("autorizaciones", jdbc.queryForList(
                "SELECT albergue_id, estado, fecha_autorizacion, fecha_revocacion FROM autorizaciones_consulta WHERE adoptante_id = ? ORDER BY id", id));
        datos.put("resenas", jdbc.queryForList(
                "SELECT albergue_id, calificacion, comentario, created_at, updated_at FROM resenas_albergue WHERE usuario_id = ? ORDER BY id", id));
        datos.put("comentarios_casos_exito", jdbc.queryForList(
                "SELECT caso_id, comentario, created_at FROM comentarios_caso_exito WHERE usuario_id = ? ORDER BY id", id));
        datos.put("reacciones_casos_exito", jdbc.queryForList(
                "SELECT caso_id, created_at FROM reacciones_caso_exito WHERE usuario_id = ? ORDER BY id", id));
        return datos;
    }

    private Usuario adoptanteActivo(long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ApiException(401, "La sesión no corresponde a una cuenta activa."));
        if (usuario.getRol() != Usuario.Rol.ADOPTANTE || usuario.isCuentaEliminada()) {
            throw new ApiException(403, "Esta operación está disponible solo para una cuenta adoptante activa.");
        }
        return usuario;
    }

    private SolicitudPrivacidad obtenerOCrear(UUID operacionId, long usuarioId, SolicitudPrivacidad.Tipo tipo) {
        String id = operacionId.toString();
        SolicitudPrivacidad existente = operaciones.findById(id).orElse(null);
        if (existente != null) {
            if (!existente.getUsuarioId().equals(usuarioId) || existente.getTipo() != tipo) {
                throw new ApiException(409, "El identificador de operación ya está asociado a otra solicitud.");
            }
            return existente;
        }
        return operaciones.save(new SolicitudPrivacidad(id, usuarioId, tipo));
    }

    private <T> T llamarDjango(String ruta, Map<String, Object> body, Class<T> tipo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Secret", secretoInterno);
        ResponseEntity<T> response = http.exchange(
                djangoBaseUrl + ruta,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                tipo);
        return response.getBody();
    }

    private Map<String, Object> respuesta(SolicitudPrivacidad operacion) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("operacion_id", operacion.getOperacionId());
        respuesta.put("tipo", operacion.getTipo());
        respuesta.put("estado", operacion.getEstado());
        respuesta.put("creado_en", operacion.getCreadoEn());
        respuesta.put("actualizado_en", operacion.getActualizadoEn());
        respuesta.put("completado_en", operacion.getCompletadoEn());
        respuesta.put("mensaje", operacion.getUltimoError());
        respuesta.put("retenciones", operacion.getRetenciones().isBlank()
                ? List.of() : List.of(operacion.getRetenciones().split("\\n")));
        return respuesta;
    }

    private List<String> convertirRetenciones(Object valor) {
        if (!(valor instanceof List<?> elementos)) return List.of();
        return elementos.stream().map(elemento -> {
            if (elemento instanceof Map<?, ?> detalle) {
                String tipo = Objects.toString(detalle.get("tipo"), "datos históricos");
                String cantidad = Objects.toString(detalle.get("cantidad"), "");
                String motivo = Objects.toString(detalle.get("motivo"), "");
                return (cantidad.isBlank() ? "Se conserva " : "Se conservan " + cantidad + " registros de ")
                        + (cantidad.isBlank() ? tipo : tipo) + (motivo.isBlank() ? "." : ": " + motivo);
            }
            return Objects.toString(elemento, "");
        }).filter(texto -> !texto.isBlank()).toList();
    }
}
