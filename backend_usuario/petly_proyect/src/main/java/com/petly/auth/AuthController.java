package com.petly.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import io.jsonwebtoken.Claims;
import java.util.Map;
import com.petly.auth.dto.AuthResponse;
import com.petly.common.exception.ApiException;
import com.petly.auth.dto.LoginRequest;
import com.petly.auth.dto.RegistroRequest;
import com.petly.auth.dto.PasswordResetRequest;
import com.petly.auth.dto.PasswordResetConfirmRequest;

import jakarta.validation.Valid;

/**
 * Registro pĂşblico de adoptantes (US-21).
 * RecuperaciĂłn de contraseĂąa por correo (US-24).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SesionRenovableService sesiones;

    @Autowired
    private SesionWeb web;

    public record Renovacion(String refreshToken, String requestId) {}
    public record CambioPassword(@NotBlank String passwordActual, @NotBlank String nuevaPassword,
                                 @NotBlank String nuevaPasswordConfirm) {}

    @PostMapping("/registro")
    public ResponseEntity<?> registro(@Valid @RequestBody RegistroRequest request) {
        AuthResponse creado = authService.registrar(request);
        return ResponseEntity.status(201).body(creado);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        boolean navegador = web.validarCanal(http);
        return entregar(authService.login(request), navegador);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Renovacion request, HttpServletRequest http) {
        boolean navegador = web.validarCanal(http);
        try {
            return entregar(sesiones.renovar(navegador ? web.token(http) : request.refreshToken(),
                    request.requestId()), navegador);
        } catch (ApiException error) {
            if (!navegador || error.status != 401) throw error;
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.SET_COOKIE, web.cookie("", Instant.EPOCH).toString())
                    .body(Map.of("error", error.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Renovacion request, HttpServletRequest http) {
        boolean navegador = web.validarCanal(http);
        sesiones.revocar(navegador ? web.token(http) : request.refreshToken());
        var salida = ResponseEntity.noContent().cacheControl(CacheControl.noStore());
        if (navegador) salida.header(HttpHeaders.SET_COOKIE, web.cookie("", Instant.EPOCH).toString());
        return salida.build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambioPassword cambio, Authentication auth) {
        Claims claims = (Claims) auth.getPrincipal();
        authService.cambiarPassword(Long.valueOf(claims.getSubject()), cambio.passwordActual(),
                cambio.nuevaPassword(), cambio.nuevaPasswordConfirm());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> entregar(SesionRenovableService.Emision emision, boolean navegador) {
        var acceso = emision.acceso();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("token", acceso.getToken());
        cuerpo.put("tokenType", acceso.getTokenType());
        cuerpo.put("expiresIn", acceso.getExpiresIn());
        cuerpo.put("user", acceso.getUser());
        var salida = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (navegador) {
            salida.header(HttpHeaders.SET_COOKIE, web.cookie(emision.renovacion(), emision.expiraEn()).toString());
        } else {
            cuerpo.put("refreshToken", emision.renovacion());
        }
        return salida.body(cuerpo);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Claims claims = (Claims) auth.getPrincipal();
        Map<String, Object> cuerpo = authService.perfilActual(Long.valueOf(claims.getSubject()));
        return ResponseEntity.ok(cuerpo);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> solicitarReset(@Valid @RequestBody PasswordResetRequest request,
            @RequestParam(defaultValue = "android") String canal) {
        if (!canal.equals("android") && !canal.equals("web")) {
            throw new com.petly.common.exception.ApiException(400, "canal inválido");
        }
        authService.solicitarResetPassword(request.getCorreo(), canal.equals("web"));
        return ResponseEntity.ok(Map.of("detail", "Si el correo existe, se enviaron instrucciones."));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<?> confirmarReset(@Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest http) {
        boolean navegador = http.getHeader(SesionWeb.CANAL) != null && web.validarCanal(http);
        authService.confirmarResetPassword(
                request.getToken(),
                request.getNuevaPassword(),
                request.getNuevaPasswordConfirm());
        var salida = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (navegador) salida.header(HttpHeaders.SET_COOKIE, web.cookie("", Instant.EPOCH).toString());
        return salida.body(Map.of("detail", "Contraseña actualizada correctamente."));
    }
}