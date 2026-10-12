package com.petly.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a tokens de recuperacion (US-24). Sin logica de negocio aqui.
 */
public interface RecuperacionTokenRepository extends JpaRepository<RecuperacionToken, Long> {

    Optional<RecuperacionToken> findByTokenHash(String tokenHash);

    List<RecuperacionToken> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    /**
     * Marca un token como usado de forma atomica (US-24, un solo uso).
     * Devuelve 1 solo si el token existia y seguia sin usar; 0 si ya fue
     * consumido, lo que evita que dos confirmaciones simultaneas lo acepten.
     */
    @Modifying
    @Query("UPDATE RecuperacionToken t SET t.usadoEn = :usadoEn "
            + "WHERE t.tokenHash = :tokenHash AND t.usadoEn IS NULL")
    int marcarComoUsado(@Param("tokenHash") String tokenHash, @Param("usadoEn") LocalDateTime usadoEn);

    /**
     * Invalida los enlaces pendientes de un usuario al generar uno nuevo
     * (US-24): solo el ultimo enlace enviado queda activo.
     */
    @Modifying
    @Query("UPDATE RecuperacionToken t SET t.usadoEn = :usadoEn "
            + "WHERE t.usuarioId = :usuarioId AND t.usadoEn IS NULL")
    int invalidarPendientes(@Param("usuarioId") Long usuarioId, @Param("usadoEn") LocalDateTime usadoEn);
}
