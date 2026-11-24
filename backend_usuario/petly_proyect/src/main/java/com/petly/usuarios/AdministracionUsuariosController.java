package com.petly.usuarios;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petly.usuarios.dto.ActualizarRolRequest;
import com.petly.usuarios.dto.AplicarSuspensionRequest;
import com.petly.usuarios.dto.UsuarioAdministracionResponse;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/usuarios")
public class AdministracionUsuariosController {

    private final AdministracionUsuariosService administracion;

    public AdministracionUsuariosController(AdministracionUsuariosService administracion) {
        this.administracion = administracion;
    }

    @GetMapping
    public List<UsuarioAdministracionResponse> listar() {
        return administracion.listar();
    }

    @PatchMapping("/{usuarioId}/rol")
    public UsuarioAdministracionResponse actualizarRol(
            @PathVariable long usuarioId,
            @Valid @RequestBody ActualizarRolRequest request,
            Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        long actorId = Long.parseLong(claims.getSubject());
        return administracion.actualizarRol(actorId, usuarioId, request);
    }

    @PatchMapping("/{usuarioId}/suspension")
    public UsuarioAdministracionResponse suspenderCuenta(
            @PathVariable long usuarioId,
            @Valid @RequestBody AplicarSuspensionRequest request,
            Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        long actorId = Long.parseLong(claims.getSubject());
        return administracion.suspenderCuenta(actorId, usuarioId, request);
    }
}
