package com.petly.solicitudes;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/** Acceso a los recordatorios persistidos de seguimientos postadopción (US-43). */
public interface RecordatorioSeguimientoRepository extends JpaRepository<RecordatorioSeguimiento, Long> {

    Optional<RecordatorioSeguimiento> findBySeguimientoIdAndAnticipacionDias(
            Long seguimientoId, int anticipacionDias);

    List<RecordatorioSeguimiento> findByAdoptanteIdOrderByFechaLimiteAsc(Long adoptanteId);

    List<RecordatorioSeguimiento> findByAdoptanteIdAndEstadoInOrderByFechaLimiteAsc(
            Long adoptanteId, List<RecordatorioSeguimiento.Estado> estados);

    List<RecordatorioSeguimiento> findByAdoptanteIdAndEstadoOrderByFechaLimiteAsc(
            Long adoptanteId, RecordatorioSeguimiento.Estado estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecordatorioSeguimiento r where r.seguimientoId = :seguimientoId "
            + "and r.anticipacionDias = :anticipacionDias")
    Optional<RecordatorioSeguimiento> bloquearPorSeguimientoYAnticipacion(
            Long seguimientoId, int anticipacionDias);
}
