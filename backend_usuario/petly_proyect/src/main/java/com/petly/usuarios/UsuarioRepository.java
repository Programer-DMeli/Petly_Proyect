package com.petly.usuarios;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a cuentas (US-21). Sin lógica de negocio aquí.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);
}
