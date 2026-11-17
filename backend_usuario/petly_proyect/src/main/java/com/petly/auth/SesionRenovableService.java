package com.petly.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.petly.auth.dto.AuthResponse;
import com.petly.auth.dto.LoginResponse;
import com.petly.common.exception.ApiException;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SesionRenovableService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final String TOKEN_PATTERN = "[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}";

    public record Emision(LoginResponse acceso, String renovacion, Instant expiraEn) {}

    private final SesionRenovableRepository sesiones;
    private final UsuarioRepository usuarios;
    private final JwtService jwt;
    private final Duration duracion;
    private final SecretKeySpec claveRotacion;

    public SesionRenovableService(SesionRenovableRepository sesiones, UsuarioRepository usuarios, JwtService jwt,
            @Value("${petly.session.refresh-days:30}") int dias,
            @Value("${spring.jwt.secret}") String clave) {
        if (dias < 1 || dias > 365) throw new IllegalArgumentException("Duración de sesión inválida");
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.jwt = jwt;
        this.duracion = Duration.ofDays(dias);
        this.claveRotacion = new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Transactional
    public Emision iniciar(Usuario usuario) {
        String id = UUID.randomUUID().toString();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = id + "." + BASE64.encodeToString(bytes);
        Instant expira = Instant.now().plus(duracion);
        sesiones.save(new SesionRenovable(id, usuario.getId(), usuario.getTokenVersion(), AuthService.hash(token), expira));
        return emitir(usuario, id, token, expira);
    }

    @Transactional
    public Emision renovar(String token, String operacion) {
        String id = id(token);
        if (operacion == null || !operacion.matches("[0-9a-fA-F-]{36}")) {
            throw new ApiException(400, "Identificador de renovación inválido.");
        }
        Long usuarioId = sesiones.buscarUsuario(id).orElseThrow(SesionRenovableService::invalida);
        Usuario usuario = usuarios.bloquearPorId(usuarioId).orElseThrow(SesionRenovableService::invalida);
        SesionRenovable sesion = sesiones.bloquear(id).orElseThrow(SesionRenovableService::invalida);
        if (sesion.revocadaEn != null || !sesion.expiraEn.isAfter(Instant.now())
                || sesion.tokenVersion != usuario.getTokenVersion()) throw invalida();

        String hash = AuthService.hash(token);
        String operacionHash = AuthService.hash(operacion);
        if (coincide(sesion.tokenHash, hash)) {
            // El navegador pudo guardar Set-Cookie antes de perder el cuerpo de la respuesta.
            if (coincide(sesion.operacionHash, operacionHash)) return emitir(usuario, id, token, sesion.expiraEn);
            String nuevo = siguiente(id, token, operacion);
            sesion.anteriorHash = hash;
            sesion.operacionHash = operacionHash;
            sesion.tokenHash = AuthService.hash(nuevo);
            return emitir(usuario, id, nuevo, sesion.expiraEn);
        }
        if (coincide(sesion.anteriorHash, hash) && coincide(sesion.operacionHash, operacionHash)) {
            String nuevo = siguiente(id, token, operacion);
            if (coincide(sesion.tokenHash, AuthService.hash(nuevo))) return emitir(usuario, id, nuevo, sesion.expiraEn);
        }
        throw invalida();
    }

    @Transactional
    public void revocar(String token) {
        if (token == null || !token.matches(TOKEN_PATTERN)) return;
        sesiones.bloquear(id(token)).ifPresent(sesion -> {
            String hash = AuthService.hash(token);
            if (sesion.revocadaEn == null && (coincide(sesion.tokenHash, hash) || coincide(sesion.anteriorHash, hash))) {
                sesion.revocadaEn = Instant.now();
            }
        });
    }

    public void revocarTodas(Long usuarioId) {
        sesiones.revocarTodas(usuarioId, Instant.now());
    }

    private Emision emitir(Usuario usuario, String id, String renovacion, Instant expira) {
        var identity = new AuthResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol().name());
        return new Emision(new LoginResponse(jwt.generarToken(usuario, id), identity), renovacion, expira);
    }

    private String siguiente(String id, String anterior, String operacion) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveRotacion);
            byte[] bytes = mac.doFinal(("petly-refresh-v1:" + anterior + ":" + operacion).getBytes(StandardCharsets.UTF_8));
            return id + "." + BASE64.encodeToString(bytes);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo rotar la sesión", ex);
        }
    }

    private static boolean coincide(String esperado, String recibido) {
        return esperado != null && MessageDigest.isEqual(esperado.getBytes(StandardCharsets.US_ASCII),
                recibido.getBytes(StandardCharsets.US_ASCII));
    }

    private static String id(String token) {
        if (token == null || !token.matches(TOKEN_PATTERN)) throw invalida();
        return token.substring(0, 36);
    }

    private static ApiException invalida() {
        return new ApiException(401, "Tu sesión venció o fue revocada. Inicia sesión nuevamente.");
    }
}
