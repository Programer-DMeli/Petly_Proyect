package com.petly.solicitudes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.petly.notificaciones.PreferenciasNotificacionService;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Envía por correo los recordatorios pendientes, respetando la preferencia del adoptante. */
@Component
public class EnviarRecordatoriosSeguimiento {

    private static final Logger log = LoggerFactory.getLogger(EnviarRecordatoriosSeguimiento.class);
    private static final ZoneId ZONA_PETLY = ZoneId.of("America/Lima");
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

    @Scheduled(fixedDelayString = "${petly.recordatorios.intervalo-ms:60000}")
    public void enviarPendientes() {
        LocalDateTime ahora = LocalDateTime.now(ZONA_PETLY);
        List<RecordatorioSeguimiento> pendientes = recordatorios.findByEstadoAndFechaLimiteLessThanEqual(
                RecordatorioSeguimiento.Estado.PENDIENTE, ahora);

        for (RecordatorioSeguimiento recordatorio : pendientes) {
            try {
                enviar(recordatorio, ahora);
            } catch (RuntimeException ex) {
                // Sigue pendiente para reintentar en la siguiente ejecución.
                log.warn("No se pudo enviar el recordatorio del seguimiento {}.", recordatorio.getSeguimientoId(), ex);
            }
        }
    }

    private void enviar(RecordatorioSeguimiento recordatorio, LocalDateTime ahora) {
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
        mensaje.setSubject("Petly: envía tu reporte de bienestar postadopción");
        mensaje.setText("Hola" + nombre(adoptante) + ",\n\n"
                + "Tienes un seguimiento postadopción pendiente desde el "
                + recordatorio.getFechaLimite().format(FORMATO_FECHA) + ". "
                + "Cuéntanos cómo está tu mascota y adjunta una foto en Petly:\n"
                + urlReportes + "\n\n"
                + "Si ya enviaste tu reporte, puedes ignorar este mensaje.\n\nEl equipo de Petly");
        mailSender.send(mensaje);
        recordatorio.marcarEnviado(ahora);
        recordatorios.save(recordatorio);
    }

    private static String nombre(Usuario usuario) {
        String nombre = usuario.getFirstName();
        return nombre == null || nombre.isBlank() ? "" : " " + nombre.trim();
    }
}
