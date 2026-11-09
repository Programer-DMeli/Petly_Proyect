package com.petly.auditoria;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;

import com.petly.common.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaConsultaService {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final EventoAuditoriaRepository eventos;

    public AuditoriaConsultaService(EventoAuditoriaRepository eventos) {
        this.eventos = eventos;
    }

    @Transactional(readOnly = true)
    public Page<EventoAuditoriaResponse> buscar(
            Long actorId,
            String accion,
            LocalDate desde,
            LocalDate hasta,
            int pagina,
            int tamano) {
        if (actorId != null && actorId <= 0) {
            throw new ApiException(400, "El identificador del actor debe ser positivo.");
        }
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw new ApiException(400, "La página debe ser positiva y el tamaño debe estar entre 1 y 100.");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ApiException(400, "La fecha inicial no puede ser posterior a la fecha final.");
        }

        String accionNormalizada = accion == null ? null : accion.trim();
        if (accionNormalizada != null && accionNormalizada.isEmpty()) accionNormalizada = null;
        if (accionNormalizada != null && accionNormalizada.length() > 80) {
            throw new ApiException(400, "La acción no puede superar 80 caracteres.");
        }

        LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
        LocalDateTime finExclusivo = finExclusivo(hasta);
        PageRequest solicitudPagina = PageRequest.of(
                pagina,
                tamano,
                Sort.by(Sort.Order.desc("ocurridoEn"), Sort.Order.desc("id")));

        return eventos.buscar(actorId, accionNormalizada, inicio, finExclusivo, solicitudPagina)
                .map(EventoAuditoriaResponse::desde);
    }

    private LocalDateTime finExclusivo(LocalDate fecha) {
        if (fecha == null) return null;
        try {
            return fecha.plusDays(1).atStartOfDay();
        } catch (DateTimeException ex) {
            throw new ApiException(400, "La fecha final no es válida.");
        }
    }
}
