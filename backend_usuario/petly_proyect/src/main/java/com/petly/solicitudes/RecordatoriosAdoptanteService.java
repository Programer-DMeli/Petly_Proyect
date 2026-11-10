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
    public List<RecordatorioAdoptanteResponse> listar(long adoptanteId) {
        return recordatorios.findByAdoptanteIdOrderByFechaLimiteAsc(adoptanteId).stream()
                .map(RecordatorioAdoptanteResponse::desde)
                .toList();
    }
}
