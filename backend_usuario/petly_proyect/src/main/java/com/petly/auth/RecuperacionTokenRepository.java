package com.petly.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a tokens de recuperacion (US-24). Sin logica de negocio aqui.
 */
public interface RecuperacionTokenRepository extends JpaRepository<RecuperacionToken, Long> {

    Optional<RecuperacionToken> findByTokenHash(String tokenHash);

    List<RecuperacionToken> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);
}
