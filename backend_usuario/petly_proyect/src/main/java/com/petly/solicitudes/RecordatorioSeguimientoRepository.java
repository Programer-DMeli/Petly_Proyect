package com.petly.solicitudes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a los recordatorios persistidos de seguimientos postadopción (US-43). */
public interface RecordatorioSeguimientoRepository extends JpaRepository<RecordatorioSeguimiento, Long> {

    Optional<RecordatorioSeguimiento> findBySeguimientoId(Long seguimientoId);

    List<RecordatorioSeguimiento> findByAdoptanteIdOrderByFechaLimiteAsc(Long adoptanteId);

    List<RecordatorioSeguimiento> findByAdoptanteIdAndEstadoOrderByFechaLimiteAsc(
            Long adoptanteId, RecordatorioSeguimiento.Estado estado);

    List<RecordatorioSeguimiento> findByEstadoAndFechaLimiteLessThanEqual(
            RecordatorioSeguimiento.Estado estado, LocalDateTime fechaLimite);
}
