package com.petly.notificaciones;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

import com.petly.catalogo.CompatibilidadService;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.petly.common.exception.ApiException;

/** Genera y persiste el aviso de US-42 después de registrar una solicitud. */
@Service
public class AlertaCompatibilidadService {
    private static final Logger log = LoggerFactory.getLogger(AlertaCompatibilidadService.class);
    private static final int UMBRAL_ESTRICTO = 90;

    private final CompatibilidadService compatibilidad;
    private final AlertaCompatibilidadAlbergueRepository alertas;
    private final JdbcTemplate db;
    private final UsuarioRepository usuarios;
    private final PreferenciasNotificacionService preferencias;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public AlertaCompatibilidadService(CompatibilidadService compatibilidad,
            AlertaCompatibilidadAlbergueRepository alertas, JdbcTemplate db,
            UsuarioRepository usuarios, PreferenciasNotificacionService preferencias,
            JavaMailSender mailSender,
            @Value("${spring.mail.from:noreply@petly.local}") String mailFrom) {
        this.compatibilidad = compatibilidad;
        this.alertas = alertas;
        this.db = db;
        this.usuarios = usuarios;
        this.preferencias = preferencias;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    public void registrarSiSuperaUmbral(long solicitudId, long adoptanteId, long mascotaId) {
        if (alertas.findBySolicitudId(solicitudId).isPresent()) return;

        Map<String, Object> detalle = compatibilidad.detalle(adoptanteId, mascotaId);
        Object rawScore = ((Map<?, ?>) detalle.get("compatibilidad")).get("porcentaje");
        if (!(rawScore instanceof Number number) || number.intValue() <= UMBRAL_ESTRICTO) return;

        Long albergueId = springIdDelAlbergue(mascotaId);
        if (albergueId == null) {
            log.warn("No se creó la alerta de compatibilidad: la cuenta del albergue no está vinculada a Spring.");
            return;
        }

        Usuario adoptante = usuarios.findById(adoptanteId).orElse(null);
        Usuario albergue = usuarios.findById(albergueId).orElse(null);
        if (adoptante == null || albergue == null || albergue.getRol() != Usuario.Rol.ALBERGUE) {
            log.warn("No se creó la alerta de compatibilidad: las cuentas vinculadas no están disponibles.");
            return;
        }

        String nombreAdoptante = nombreCompleto(adoptante);
        String nombreMascota = String.valueOf(detalle.getOrDefault("nombre", "Mascota"));
        AlertaCompatibilidadAlbergue alerta = new AlertaCompatibilidadAlbergue(
                solicitudId, albergueId, adoptanteId, mascotaId, nombreAdoptante,
                nombreMascota, number.intValue(), LocalDateTime.now());
        try {
            alertas.save(alerta);
        } catch (DataIntegrityViolationException ex) {
            // Una solicitud concurrente o un reintento no debe duplicar el aviso.
            if (alertas.findBySolicitudId(solicitudId).isEmpty()) throw ex;
            return;
        }
        enviarCorreoSiHabilitado(alerta, albergue);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listarParaAlbergue(long albergueUsuarioId) {
        List<AlertaCompatibilidadResponse> results = alertas
                .findAllByAlbergueUsuarioIdOrderByOcurridoEnDescIdDesc(albergueUsuarioId)
                .stream().map(AlertaCompatibilidadResponse::desde).toList();
        return Map.of("results", results,
                "no_leidas", alertas.countByAlbergueUsuarioIdAndLeidoEnIsNull(albergueUsuarioId));
    }

    @Transactional
    public AlertaCompatibilidadResponse marcarLeida(long albergueUsuarioId, long alertaId) {
        AlertaCompatibilidadAlbergue alerta = alertas.findByIdAndAlbergueUsuarioId(alertaId, albergueUsuarioId)
                .orElseThrow(() -> new ApiException(404, "No se encontró la alerta de este albergue."));
        alerta.marcarLeida(LocalDateTime.now());
        return AlertaCompatibilidadResponse.desde(alertas.save(alerta));
    }

    private Long springIdDelAlbergue(long mascotaId) {
        try {
            return db.queryForObject("SELECT u.spring_id FROM mascotas_mascota m "
                    + "JOIN albergues_albergue a ON a.id=m.albergue_id "
                    + "JOIN acceso_user u ON u.id=a.usuario_id WHERE m.id=?", Long.class, mascotaId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private void enviarCorreoSiHabilitado(AlertaCompatibilidadAlbergue alerta, Usuario albergue) {
        if (!Boolean.TRUE.equals(preferencias.obtener(alerta.getAlbergueUsuarioId()).email())) return;
        if (albergue.getCorreo() == null || albergue.getCorreo().isBlank()) return;
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(mailFrom);
            mensaje.setTo(albergue.getCorreo());
            mensaje.setSubject("Petly: candidato con alta compatibilidad");
            mensaje.setText("La solicitud de " + alerta.getAdoptanteNombre() + " para "
                    + alerta.getMascotaNombre() + " tiene una compatibilidad de "
                    + alerta.getPorcentaje() + "%. Revisa la solicitud en el panel de tu albergue.");
            mailSender.send(mensaje);
        } catch (RuntimeException ex) {
            // El aviso permanece en la bandeja aunque el proveedor de correo falle.
            log.warn("No se pudo enviar por correo la alerta de compatibilidad {}.", alerta.getId(), ex);
        }
    }

    private static String nombreCompleto(Usuario usuario) {
        String nombre = ((usuario.getFirstName() == null ? "" : usuario.getFirstName()) + " "
                + (usuario.getLastName() == null ? "" : usuario.getLastName())).trim();
        return nombre.isBlank() ? usuario.getCorreo() : nombre;
    }
}
