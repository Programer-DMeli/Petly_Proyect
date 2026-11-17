package com.petly.auth;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SesionRenovableRepository extends JpaRepository<SesionRenovable, String> {
    @Query("select s.usuarioId from SesionRenovable s where s.id = :id")
    Optional<Long> buscarUsuario(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionRenovable s where s.id = :id")
    Optional<SesionRenovable> bloquear(String id);

    boolean existsByIdAndUsuarioIdAndRevocadaEnIsNull(String id, Long usuarioId);

    @Modifying
    @Query("update SesionRenovable s set s.revocadaEn = :ahora where s.usuarioId = :usuarioId and s.revocadaEn is null")
    void revocarTodas(Long usuarioId, Instant ahora);
}
