package com.petly.notificaciones;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AlertaCompatibilidadAlbergueRepository
        extends JpaRepository<AlertaCompatibilidadAlbergue, Long> {
    List<AlertaCompatibilidadAlbergue> findAllByAlbergueUsuarioIdOrderByOcurridoEnDescIdDesc(Long albergueUsuarioId);
    Optional<AlertaCompatibilidadAlbergue> findBySolicitudId(Long solicitudId);
    Optional<AlertaCompatibilidadAlbergue> findByIdAndAlbergueUsuarioId(Long id, Long albergueUsuarioId);
    long countByAlbergueUsuarioIdAndLeidoEnIsNull(Long albergueUsuarioId);

    @Modifying
    @Query("update AlertaCompatibilidadAlbergue a set a.leidoEn = :fecha "
            + "where a.albergueUsuarioId = :albergueId and a.leidoEn is null")
    int marcarTodasLeidas(Long albergueId, LocalDateTime fecha);
}
