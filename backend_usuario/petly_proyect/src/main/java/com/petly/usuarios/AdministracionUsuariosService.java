package com.petly.usuarios;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.petly.auth.SesionRenovableService;
import com.petly.common.exception.ApiException;
import com.petly.auditoria.AuditoriaService;
import com.petly.usuarios.dto.ActualizarRolRequest;
import com.petly.usuarios.dto.AplicarSuspensionRequest;
import com.petly.usuarios.dto.UsuarioAdministracionResponse;

@Service
public class AdministracionUsuariosService {

    private final UsuarioRepository usuarios;
    private final SincronizarRolDjango sincronizadorDjango;
    private final AuditoriaService auditoria;
    private final SesionRenovableService sesiones;

    public AdministracionUsuariosService(
            UsuarioRepository usuarios,
            SincronizarRolDjango sincronizadorDjango,
            AuditoriaService auditoria,
            SesionRenovableService sesiones) {
        this.usuarios = usuarios;
        this.sincronizadorDjango = sincronizadorDjango;
        this.auditoria = auditoria;
        this.sesiones = sesiones;
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdministracionResponse> listar() {
        return usuarios.findAllByOrderByIdAsc().stream()
                .map(UsuarioAdministracionResponse::desde)
                .toList();
    }

    @Transactional
    public UsuarioAdministracionResponse actualizarRol(long actorId, long usuarioId, ActualizarRolRequest request) {
        if (actorId <= 0) {
            throw new ApiException(401, "La sesión no identifica una cuenta válida.");
        }
        if (usuarioId <= 0) {
            throw new ApiException(400, "El identificador del usuario debe ser positivo.");
        }
        if (request == null || request.rol() == null) {
            throw new ApiException(400, "Selecciona un rol válido para la cuenta.");
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
            usuarios.saveAndFlush(objetivo);
            sincronizadorDjango.sincronizar(objetivo);
            Usuario actor = usuarios.findById(actorId)
                    .orElseThrow(() -> new ApiException(401, "La sesión no identifica una cuenta válida."));
            auditoria.registrar(
                    actorId,
                    actor.getRol().name(),
                    "USUARIO_ROL_ACTUALIZADO",
                    "USUARIO",
                    Long.toString(usuarioId));
        }
        return UsuarioAdministracionResponse.desde(objetivo);
    }

    @Transactional
    public UsuarioAdministracionResponse suspenderCuenta(
            long actorId, long usuarioId, AplicarSuspensionRequest request) {
        if (actorId <= 0 || usuarioId <= 0) {
            throw new ApiException(400, "Los identificadores de cuenta deben ser positivos.");
        }
        if (request == null || request.motivo() == null || request.motivo().isBlank()
                || request.motivo().length() > 500) {
            throw new ApiException(400, "Indica un motivo de suspensión de hasta 500 caracteres.");
        }

        var ahora = java.time.LocalDateTime.now();
        if (request.hasta() != null && !request.hasta().isAfter(ahora)) {
            throw new ApiException(400, "La fecha de vencimiento debe ser futura.");
        }
        Usuario actor = usuarios.findById(actorId)
                .orElseThrow(() -> new ApiException(401, "La sesión no identifica una cuenta válida."));
        if (actor.getRol() != Usuario.Rol.ADMIN) {
            throw new ApiException(403, "Solo ADMIN puede suspender cuentas.");
        }
        if (actorId == usuarioId) {
            throw new ApiException(409, "No puedes suspender tu propia cuenta.");
        }

        // Serializa las suspensiones de administradores y evita dejar la plataforma sin uno activo.
        List<Usuario> administradores = usuarios.bloquearPorRol(Usuario.Rol.ADMIN);
        Usuario objetivo = usuarios.bloquearPorId(usuarioId)
                .orElseThrow(() -> new ApiException(404, "No se encontró la cuenta."));
        if (objetivo.getRol() == Usuario.Rol.ADMIN && administradores.stream()
                .filter(admin -> !admin.getId().equals(usuarioId))
                .noneMatch(admin -> !admin.tieneSuspensionVigente(ahora))) {
            throw new ApiException(409, "Debe permanecer al menos una cuenta ADMIN activa.");
        }

        objetivo.setCuentaSuspendida(true);
        objetivo.setMotivoSuspension(request.motivo().trim());
        objetivo.setSuspendidaEn(ahora);
        objetivo.setSuspensionHasta(request.hasta());
        objetivo.setSuspendidaPorId(actorId);
        objetivo.setTokenVersion(objetivo.getTokenVersion() + 1);
        usuarios.saveAndFlush(objetivo);
        sesiones.revocarTodas(usuarioId);
        auditoria.registrar(actorId, actor.getRol().name(), "USUARIO_SUSPENSION_APLICADA", "USUARIO",
                Long.toString(usuarioId));
        return UsuarioAdministracionResponse.desde(objetivo);
    }
}
