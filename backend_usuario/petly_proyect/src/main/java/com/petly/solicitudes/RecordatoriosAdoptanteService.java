package com.petly.solicitudes;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta recordatorios filtrados por el adoptante autenticado. */
@Service
public class RecordatoriosAdoptanteService {

    private final RecordatorioSeguimientoRepository recordatorios;

    public RecordatoriosAdoptanteService(RecordatorioSeguimientoRepository recordatorios) {
        this.recordatorios = recordatorios;
    }

    @Transactional(readOnly = true)
    public List<RecordatorioAdoptanteResponse> listar(long adoptanteId, boolean pendientes) {
        var filas = pendientes
                ? recordatorios.findByAdoptanteIdAndEstadoInOrderByFechaLimiteAsc(adoptanteId,
                        List.of(RecordatorioSeguimiento.Estado.PENDIENTE, RecordatorioSeguimiento.Estado.ENVIADO))
                : recordatorios.findByAdoptanteIdOrderByFechaLimiteAsc(adoptanteId);
        return filas.stream()
                .map(RecordatorioAdoptanteResponse::desde)
                .toList();
    }
}
