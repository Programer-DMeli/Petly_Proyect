package com.petly.solicitudes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

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

/** Sincroniza en Spring los seguimientos y reportes que Django mantiene como fuente de verdad. */
@Component
public class SincronizarRecordatoriosSeguimiento {

    private static final Logger log = LoggerFactory.getLogger(SincronizarRecordatoriosSeguimiento.class);
    private static final String RUTA_FEED = "/api/contratos/reportes/feed-recordatorios/";
    private static final ZoneId ZONA_PETLY = ZoneId.of("America/Lima");

    private final RestTemplate restTemplate;
    private final RecordatorioSeguimientoRepository recordatorios;
    private final String djangoApiBaseUrl;
    private final String secretoInterno;

    public SincronizarRecordatoriosSeguimiento(
            RestTemplate restTemplate,
            RecordatorioSeguimientoRepository recordatorios,
            @Value("${petly.django-api-base-url}") String djangoApiBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.restTemplate = restTemplate;
        this.recordatorios = recordatorios;
        this.djangoApiBaseUrl = djangoApiBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    @Scheduled(fixedDelayString = "${petly.recordatorios.intervalo-ms:60000}")
    public void sincronizar() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.set("X-Internal-Secret", secretoInterno);
            ResponseEntity<List<SeguimientoFeed>> response = restTemplate.exchange(
                    djangoApiBaseUrl + RUTA_FEED,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() { });

            List<SeguimientoFeed> filas = response.getBody();
            if (filas == null) {
                return;
            }
            for (SeguimientoFeed fila : filas) {
                try {
                    sincronizar(fila);
                } catch (RuntimeException ex) {
                    log.error("No se pudo sincronizar el recordatorio del seguimiento {}.", fila.id(), ex);
                }
            }
        } catch (HttpStatusCodeException ex) {
            log.warn("Django rechazó la consulta interna de recordatorios: HTTP {}.", ex.getStatusCode().value());
        } catch (ResourceAccessException ex) {
            log.warn("Django no está disponible para sincronizar recordatorios.");
        } catch (RuntimeException ex) {
            log.error("No se pudo completar la sincronización de recordatorios.", ex);
        }
    }

    private void sincronizar(SeguimientoFeed fila) {
        if (fila.id() == null || fila.adoptanteSpringId() == null || fila.programadoPara() == null) {
            log.warn("Se omitió un seguimiento incompleto en el feed interno de recordatorios.");
            return;
        }

        var existente = recordatorios.findBySeguimientoId(fila.id());
        if (Boolean.TRUE.equals(fila.reporteEnviado())) {
            existente.ifPresent(recordatorio -> {
                if (recordatorio.getEstado() != RecordatorioSeguimiento.Estado.COMPLETADO) {
                    recordatorio.completar();
                    recordatorios.save(recordatorio);
                }
            });
            return;
        }

        if ("CANCELADO".equals(fila.estado()) || Boolean.TRUE.equals(fila.expedienteCerrado())) {
            existente.ifPresent(recordatorio -> {
                if (recordatorio.getEstado() != RecordatorioSeguimiento.Estado.CANCELADO) {
                    recordatorio.cancelar();
                    recordatorios.save(recordatorio);
                }
            });
            return;
        }

        if (!"PROGRAMADO".equals(fila.estado()) && !"REALIZADO".equals(fila.estado())) {
            return;
        }

        LocalDateTime fechaLimite = fila.programadoPara()
                .atZoneSameInstant(ZONA_PETLY)
                .toLocalDateTime();
        if (existente.isPresent()) {
            RecordatorioSeguimiento recordatorio = existente.get();
            if (recordatorio.getEstado() == RecordatorioSeguimiento.Estado.PENDIENTE
                    || recordatorio.getEstado() == RecordatorioSeguimiento.Estado.ENVIADO) {
                if (!fila.adoptanteSpringId().equals(recordatorio.getAdoptanteId())
                        || !fechaLimite.equals(recordatorio.getFechaLimite())) {
                    recordatorio.actualizarDatos(fila.adoptanteSpringId(), fechaLimite);
                    recordatorios.save(recordatorio);
                }
            }
            return;
        }

        recordatorios.save(new RecordatorioSeguimiento(fila.id(), fila.adoptanteSpringId(), fechaLimite));
    }

    private record SeguimientoFeed(
            Long id,
            @JsonProperty("adoptante_spring_id")
            Long adoptanteSpringId,
            @JsonProperty("programado_para")
            OffsetDateTime programadoPara,
            String estado,
            @JsonProperty("reporte_enviado")
            Boolean reporteEnviado,
            @JsonProperty("expediente_cerrado")
            Boolean expedienteCerrado) {
    }
}
