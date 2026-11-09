package com.petly.auditoria;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Importa de forma reintentable los eventos Django a la proyección de Spring. */
@Component
public class SincronizarAuditoriaDjango {

    private static final Logger log = LoggerFactory.getLogger(SincronizarAuditoriaDjango.class);
    private static final String RUTA = "/api/acceso/interno/auditoria/eventos/";
    private static final ZoneId ZONA_PETLY = ZoneId.of("America/Lima");

    private final RestTemplate http;
    private final EventoAuditoriaRepository eventos;
    private final String djangoBaseUrl;
    private final String secretoInterno;

    public SincronizarAuditoriaDjango(
            RestTemplate http,
            EventoAuditoriaRepository eventos,
            @Value("${petly.django-api-base-url}") String djangoBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.http = http;
        this.eventos = eventos;
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    @Scheduled(fixedDelayString = "${petly.auditoria.intervalo-ms:60000}")
    public void sincronizar() {
        try {
            List<EventoDjango> lote = obtenerPendientes();
            if (lote == null || lote.isEmpty()) return;

            List<String> ids = lote.stream()
                    .filter(this::eventoValido)
                    .map(EventoDjango::eventoId)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) return;

            guardarSiNuevos(lote);
            confirmar(ids);
        } catch (HttpStatusCodeException ex) {
            log.warn("Django rechazó la sincronización interna de auditoría: HTTP {}.", ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.warn("Django no está disponible para sincronizar auditoría; se reintentará.");
        } catch (RuntimeException ex) {
            log.error("No se pudo sincronizar el feed interno de auditoría; se reintentará.", ex);
        }
    }

    private List<EventoDjango> obtenerPendientes() {
        ResponseEntity<List<EventoDjango>> response = http.exchange(
                djangoBaseUrl + RUTA,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                new ParameterizedTypeReference<>() { });
        return response.getBody();
    }

    private void guardarSiNuevos(List<EventoDjango> lote) {
        for (EventoDjango evento : lote) {
            if (!eventoValido(evento)) {
                log.warn("Se omitió un evento de auditoría Django incompleto.");
                continue;
            }
            if (eventos.findByEventoId(evento.eventoId()).isEmpty()) {
                eventos.save(new EventoAuditoria(
                        evento.eventoId(),
                        evento.actorUsuarioId(),
                        evento.actorRol(),
                        evento.accion(),
                        "DJANGO",
                        evento.recursoTipo(),
                        evento.recursoId(),
                        evento.ocurridoEn().atZoneSameInstant(ZONA_PETLY).toLocalDateTime()));
            }
        }
    }

    private void confirmar(List<String> ids) {
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ConfirmacionDjango> entity = new HttpEntity<>(new ConfirmacionDjango(ids), headers);
        http.exchange(djangoBaseUrl + RUTA, HttpMethod.POST, entity, Void.class);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Secret", secretoInterno);
        return headers;
    }

    private boolean eventoValido(EventoDjango evento) {
        if (evento == null || evento.eventoId() == null || evento.actorRol() == null
                || evento.accion() == null || evento.ocurridoEn() == null) return false;
        try {
            UUID.fromString(evento.eventoId());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private record EventoDjango(
            @JsonProperty("evento_id") String eventoId,
            @JsonProperty("actor_usuario_id") Long actorUsuarioId,
            @JsonProperty("actor_rol") String actorRol,
            @JsonProperty("accion") String accion,
            @JsonProperty("origen") String origen,
            @JsonProperty("recurso_tipo") String recursoTipo,
            @JsonProperty("recurso_id") String recursoId,
            @JsonProperty("ocurrido_en") OffsetDateTime ocurridoEn) {
    }

    private record ConfirmacionDjango(@JsonProperty("evento_ids") List<String> eventoIds) {
    }
}
