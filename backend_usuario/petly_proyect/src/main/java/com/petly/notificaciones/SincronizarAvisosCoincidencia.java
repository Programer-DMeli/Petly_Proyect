package com.petly.notificaciones;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Recibe coincidencias de Django y entrega correo respetando las preferencias de Spring. */
@Component
public class SincronizarAvisosCoincidencia {

    private static final Logger log = LoggerFactory.getLogger(SincronizarAvisosCoincidencia.class);
    private static final String RUTA_FEED = "/api/acceso/interno/notificaciones/coincidencias/";
    private static final ZoneId ZONA_PETLY = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private final RestTemplate restTemplate;
    private final EntregaAvisoCoincidenciaRepository entregas;
    private final UsuarioRepository usuarios;
    private final PreferenciasNotificacionService preferencias;
    private final JavaMailSender mailSender;
    private final String djangoApiBaseUrl;
    private final String secretoInterno;
    private final String mailFrom;
    private final String urlCatalogo;

    public SincronizarAvisosCoincidencia(
            RestTemplate restTemplate,
            EntregaAvisoCoincidenciaRepository entregas,
            UsuarioRepository usuarios,
            PreferenciasNotificacionService preferencias,
            JavaMailSender mailSender,
            @Value("${petly.django-api-base-url}") String djangoApiBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno,
            @Value("${spring.mail.from:noreply@petly.local}") String mailFrom,
            @Value("${petly.notificaciones-coincidencia.url-catalogo:http://localhost:5173/adoptante/catalogo}") String urlCatalogo) {
        this.restTemplate = restTemplate;
        this.entregas = entregas;
        this.usuarios = usuarios;
        this.preferencias = preferencias;
        this.mailSender = mailSender;
        this.djangoApiBaseUrl = djangoApiBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
        this.mailFrom = mailFrom;
        this.urlCatalogo = urlCatalogo.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${petly.notificaciones-coincidencia.intervalo-ms:60000}")
    public void sincronizarYEnviar() {
        sincronizarFeed();
        enviarPendientes();
    }

    private void sincronizarFeed() {
        try {
            long afterId = entregas.ultimoEventoSincronizado();
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("X-Internal-Secret", secretoInterno);
            ResponseEntity<List<EventoCoincidenciaFeed>> response = restTemplate.exchange(
                    djangoApiBaseUrl + RUTA_FEED + "?after_id=" + afterId + "&limit=100",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() { });

            List<EventoCoincidenciaFeed> eventos = response.getBody();
            if (eventos == null) return;
            for (EventoCoincidenciaFeed evento : eventos) {
                if (evento.eventoId() == null || evento.adoptanteId() == null || evento.mascotaId() == null
                        || evento.ocurridoEn() == null || evento.mascotaNombre() == null
                        || evento.albergueNombre() == null) {
                    log.warn("Se omitió una coincidencia incompleta del feed interno.");
                    continue;
                }
                if (entregas.findByEventoDjangoId(evento.eventoId()).isEmpty()) {
                    entregas.save(new EntregaAvisoCoincidencia(
                            evento.eventoId(), evento.adoptanteId(), evento.mascotaId(),
                            evento.mascotaNombre(), evento.albergueNombre(),
                            evento.ocurridoEn().atZoneSameInstant(ZONA_PETLY).toLocalDateTime()));
                }
            }
        } catch (HttpStatusCodeException ex) {
            log.warn("Django rechazó la consulta interna de coincidencias: HTTP {}.", ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.warn("Django no está disponible para sincronizar coincidencias.");
        } catch (RuntimeException ex) {
            log.error("No se pudo sincronizar el feed de coincidencias.", ex);
        }
    }

    private void enviarPendientes() {
        List<EntregaAvisoCoincidencia> pendientes = entregas.findByEstadoOrderByIdAsc(
                EntregaAvisoCoincidencia.Estado.PENDIENTE);
        for (EntregaAvisoCoincidencia entrega : pendientes) {
            try {
                enviar(entrega);
            } catch (RuntimeException ex) {
                // Conserva PENDIENTE para reintentar sin perder el evento.
                log.warn("No se pudo enviar el aviso de coincidencia {}.", entrega.getEventoDjangoId(), ex);
            }
        }
    }

    private void enviar(EntregaAvisoCoincidencia entrega) {
        PreferenciaNotificacion canales = preferencias.obtener(entrega.getAdoptanteId());
        if (!Boolean.TRUE.equals(canales.email())) {
            entrega.omitir();
            entregas.save(entrega);
            return;
        }

        Usuario adoptante = usuarios.findById(entrega.getAdoptanteId()).orElse(null);
        if (adoptante == null || adoptante.getCorreo() == null || adoptante.getCorreo().isBlank()) {
            log.warn("No se envió la coincidencia {} porque el adoptante no tiene una cuenta o correo válido.",
                    entrega.getEventoDjangoId());
            entrega.omitir();
            entregas.save(entrega);
            return;
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(mailFrom);
        mensaje.setTo(adoptante.getCorreo());
        mensaje.setSubject("Petly encontró una mascota compatible: " + entrega.getMascotaNombre());
        mensaje.setText("Hola" + nombre(adoptante) + ",\n\n"
                + "Una nueva mascota coincide con tus criterios guardados:\n\n"
                + entrega.getMascotaNombre() + " · Albergue: " + entrega.getAlbergueNombre() + "\n"
                + "Registrada el " + entrega.getOcurridoEn().format(FORMATO_FECHA) + ".\n\n"
                + "Conócela en Petly: " + urlCatalogo + "/" + entrega.getMascotaId() + "\n\n"
                + "Recibes este correo porque tienes habilitado el canal email en tus preferencias.");
        mailSender.send(mensaje);
        entrega.marcarEnviado(LocalDateTime.now(ZONA_PETLY));
        entregas.save(entrega);
    }

    private static String nombre(Usuario usuario) {
        String nombre = usuario.getFirstName();
        return nombre == null || nombre.isBlank() ? "" : " " + nombre.trim();
    }

    private record EventoCoincidenciaFeed(
            @JsonProperty("evento_id") Long eventoId,
            @JsonProperty("adoptante_spring_id") Long adoptanteId,
            @JsonProperty("mascota_id") Long mascotaId,
            @JsonProperty("mascota_nombre") String mascotaNombre,
            @JsonProperty("albergue_nombre") String albergueNombre,
            @JsonProperty("ocurrido_en") OffsetDateTime ocurridoEn) {
    }
}
