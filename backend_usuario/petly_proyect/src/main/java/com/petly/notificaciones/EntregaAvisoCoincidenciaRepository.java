package com.petly.notificaciones;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persistencia idempotente de notificaciones externas para US-44. */
public interface EntregaAvisoCoincidenciaRepository extends JpaRepository<EntregaAvisoCoincidencia, Long> {

    Optional<EntregaAvisoCoincidencia> findByEventoDjangoId(Long eventoDjangoId);

    List<EntregaAvisoCoincidencia> findByEstadoOrderByIdAsc(EntregaAvisoCoincidencia.Estado estado);

    @Query("SELECT COALESCE(MAX(entrega.eventoDjangoId), 0) FROM EntregaAvisoCoincidencia entrega")
    Long ultimoEventoSincronizado();
}
