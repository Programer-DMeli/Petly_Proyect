package com.petly.auditoria;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    Optional<EventoAuditoria> findByEventoId(String eventoId);
}
