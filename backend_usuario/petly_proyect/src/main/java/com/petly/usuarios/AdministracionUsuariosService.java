package com.petly.usuarios;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.petly.common.exception.ApiException;
import com.petly.usuarios.dto.ActualizarRolRequest;
import com.petly.usuarios.dto.UsuarioAdministracionResponse;

@Service
public class AdministracionUsuariosService {

    private final UsuarioRepository usuarios;

    public AdministracionUsuariosService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdministracionResponse> listar() {
        return usuarios.findAllByOrderByIdAsc().stream()
                .map(UsuarioAdministracionResponse::desde)
                .toList();
    }

    @Transactional
    public UsuarioAdministracionResponse actualizarRol(long actorId, long usuarioId, ActualizarRolRequest request) {
        if (usuarioId <= 0) {
            throw new ApiException(400, "El identificador del usuario debe ser positivo.");
        }

        // Serializa los cambios de administradores para evitar que dos peticiones
        // simultáneas retiren el último rol ADMIN.
        List<Usuario> administradores = usuarios.bloquearPorRol(Usuario.Rol.ADMIN);
        Usuario objetivo = usuarios.bloquearPorId(usuarioId)
                .orElseThrow(() -> new ApiException(404, "No se encontró la cuenta."));
        Usuario.Rol rolActual = objetivo.getRol();
        Usuario.Rol rolNuevo = request.rol();

        if (actorId == usuarioId && rolActual != rolNuevo) {
            throw new ApiException(409, "No puedes cambiar tu propio rol.");
        }
        if (rolActual == Usuario.Rol.ADMIN && rolNuevo != Usuario.Rol.ADMIN
                && administradores.size() <= 1) {
            throw new ApiException(409, "Debe permanecer al menos una cuenta ADMIN.");
        }

        if (rolActual != rolNuevo) {
            objetivo.setRol(rolNuevo);
            objetivo.setTokenVersion(objetivo.getTokenVersion() + 1);
            usuarios.save(objetivo);
        }
        return UsuarioAdministracionResponse.desde(objetivo);
    }
}
