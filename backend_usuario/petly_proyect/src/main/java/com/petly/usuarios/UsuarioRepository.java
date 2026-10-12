package com.petly.usuarios;

import com.petly.usuarios.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de usuarios para Spring Boot.
 * Gestiona las cuentas de negocio (adoptantes, albergues, admins).
 */
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    java.util.List<Usuario> findByRolOrderByIdAsc(Usuario.Rol rol);

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByCorreo(String correo);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.correo = :correo")
    Optional<Usuario> bloquearPorCorreo(@Param("correo") String correo);

    boolean existsByUsername(String username);

    boolean existsByCorreo(String correo);

    @Query("SELECT u FROM Usuario u WHERE u.correo = :correo AND u.rol = :rol")
    Optional<Usuario> findByCorreoAndRol(@Param("correo") String correo, @Param("rol") Usuario.Rol rol);
}
