package com.petly.solicitudes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.petly.notificaciones.PreferenciasNotificacionService;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Envía por correo los recordatorios pendientes, respetando la preferencia del adoptante. */
@Component
public class EnviarRecordatoriosSeguimiento {

    private static final Logger log = LoggerFactory.getLogger(EnviarRecordatoriosSeguimiento.class);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final RecordatorioSeguimientoRepository recordatorios;
    private final UsuarioRepository usuarios;
    private final PreferenciasNotificacionService preferencias;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final String urlReportes;

    public EnviarRecordatoriosSeguimiento(
            RecordatorioSeguimientoRepository recordatorios,
            UsuarioRepository usuarios,
            PreferenciasNotificacionService preferencias,
            JavaMailSender mailSender,
            @Value("${spring.mail.from:noreply@petly.local}") String mailFrom,
            @Value("${petly.recordatorios.url-reportes:http://localhost:5173/adoptante/seguimientos-postadopcion}") String urlReportes) {
        this.recordatorios = recordatorios;
        this.usuarios = usuarios;
        this.preferencias = preferencias;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
        this.urlReportes = urlReportes;
    }

    @Transactional
    public void enviarPendiente(Long seguimientoId, int anticipacionDias, String mascotaNombre, LocalDateTime ahora) {
        var recordatorio = recordatorios
                .bloquearPorSeguimientoYAnticipacion(seguimientoId, anticipacionDias).orElse(null);
        if (recordatorio == null || recordatorio.getEstado() != RecordatorioSeguimiento.Estado.PENDIENTE
                || !recordatorio.getFechaLimite().isAfter(ahora)) {
            return;
        }
        LocalDateTime inicioAviso = recordatorio.getFechaLimite().minusDays(anticipacionDias);
        LocalDateTime finAviso = recordatorio.getFechaLimite().minusDays(anticipacionDias - 1L);
        if (ahora.isBefore(inicioAviso)) {
            return;
        }
        if (!ahora.isBefore(finAviso)) {
            recordatorio.cancelar();
            recordatorios.save(recordatorio);
            return;
        }
        var canales = preferencias.obtener(recordatorio.getAdoptanteId());
        if (!Boolean.TRUE.equals(canales.email())) {
            // WhatsApp y push requieren proveedores que no están configurados en el proyecto.
            log.debug("No se envió por correo el recordatorio {} porque el canal está desactivado.",
                    recordatorio.getSeguimientoId());
            return;
        }

        Usuario adoptante = usuarios.findById(recordatorio.getAdoptanteId())
                .orElseThrow(() -> new IllegalStateException("No existe el adoptante del recordatorio."));
        if (adoptante.getCorreo() == null || adoptante.getCorreo().isBlank()) {
            log.warn("El adoptante {} no tiene correo para recibir el recordatorio.", adoptante.getId());
            return;
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(mailFrom);
        mensaje.setTo(adoptante.getCorreo());
        mensaje.setSubject(anticipacionDias == 3
                ? "Petly: tu reporte vence en los próximos días"
                : "Petly: tu reporte vence mañana");
        mensaje.setText("Hola" + nombre(adoptante) + ",\n\n"
                + "El reporte de bienestar de " + mascotaNombre
                + (anticipacionDias == 3 ? " vence en aproximadamente tres días, el " : " vence mañana, el ")
                + recordatorio.getFechaLimite().format(FORMATO_FECHA) + " (hora de Lima). "
                + "Cuéntanos cómo está tu mascota y adjunta una foto en Petly:\n"
                + urlReportes + (urlReportes.contains("?") ? "&" : "?")
                + "entrega=" + seguimientoId + "#seguimiento-" + seguimientoId + "\n\n"
                + "Si ya enviaste tu reporte, puedes ignorar este mensaje.\n\nEl equipo de Petly");
        mailSender.send(mensaje);
        recordatorio.marcarEnviado(ahora);
    }

    private static String nombre(Usuario usuario) {
        String nombre = usuario.getFirstName();
        return nombre == null || nombre.isBlank() ? "" : " " + nombre.trim();
    }
}
