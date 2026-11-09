package com.petly.auditoria;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    Optional<EventoAuditoria> findByEventoId(String eventoId);

    @Query(value = """
            SELECT e FROM EventoAuditoria e
            WHERE (:actorId IS NULL OR e.actorUsuarioId = :actorId)
              AND (:accion IS NULL OR LOWER(e.accion) LIKE LOWER(CONCAT('%', :accion, '%')))
              AND (:desde IS NULL OR e.ocurridoEn >= :desde)
              AND (:hastaExclusivo IS NULL OR e.ocurridoEn < :hastaExclusivo)
            """,
            countQuery = """
            SELECT COUNT(e) FROM EventoAuditoria e
            WHERE (:actorId IS NULL OR e.actorUsuarioId = :actorId)
              AND (:accion IS NULL OR LOWER(e.accion) LIKE LOWER(CONCAT('%', :accion, '%')))
              AND (:desde IS NULL OR e.ocurridoEn >= :desde)
              AND (:hastaExclusivo IS NULL OR e.ocurridoEn < :hastaExclusivo)
            """)
    Page<EventoAuditoria> buscar(
            @Param("actorId") Long actorId,
            @Param("accion") String accion,
            @Param("desde") java.time.LocalDateTime desde,
            @Param("hastaExclusivo") java.time.LocalDateTime hastaExclusivo,
            Pageable pageable);
}
