package com.petly.notificaciones;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaCompatibilidadAlbergueRepository
        extends JpaRepository<AlertaCompatibilidadAlbergue, Long> {
    List<AlertaCompatibilidadAlbergue> findAllByAlbergueUsuarioIdOrderByOcurridoEnDescIdDesc(Long albergueUsuarioId);
    Optional<AlertaCompatibilidadAlbergue> findBySolicitudId(Long solicitudId);
    Optional<AlertaCompatibilidadAlbergue> findByIdAndAlbergueUsuarioId(Long id, Long albergueUsuarioId);
    long countByAlbergueUsuarioIdAndLeidoEnIsNull(Long albergueUsuarioId);
}
