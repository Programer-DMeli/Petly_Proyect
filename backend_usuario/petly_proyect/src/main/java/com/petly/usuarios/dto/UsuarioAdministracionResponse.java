package com.petly.usuarios.dto;

import java.time.LocalDateTime;

import com.petly.usuarios.Usuario;

public record UsuarioAdministracionResponse(
        long id,
        String username,
        String correo,
        String nombres,
        String apellidos,
        Usuario.Rol rol,
        LocalDateTime creadoEn,
        boolean cuentaSuspendida,
        String motivoSuspension,
        LocalDateTime suspendidaEn,
        LocalDateTime suspensionHasta,
        Long suspendidaPorId) {

    public static UsuarioAdministracionResponse desde(Usuario usuario) {
        return new UsuarioAdministracionResponse(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getCorreo(),
                usuario.getFirstName(),
                usuario.getLastName(),
                usuario.getRol(),
                usuario.getCreatedAt(),
                usuario.tieneSuspensionVigente(LocalDateTime.now()),
                usuario.getMotivoSuspension(),
                usuario.getSuspendidaEn(),
                usuario.getSuspensionHasta(),
                usuario.getSuspendidaPorId());
    }
}
