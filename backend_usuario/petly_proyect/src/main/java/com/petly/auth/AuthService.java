package com.petly.auth;

import com.petly.auth.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registro, login y recuperaci�n de contrase�a (US-21, US-24).
 */
@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private RecuperacionTokenRepository tokens;

    @Value("${spring.mail.from:noreply@petly.local}")
    private String mailFrom;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    public AuthResponse registrar(RegistroRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        String username = request.getUsername().trim().toLowerCase();
        
        if (usuarios.existsByCorreo(correo)) {
            throw new CorreoYaRegistradoException("correo ya registrado");
        }
        if (usuarios.existsByUsername(username)) {
            throw new UsuarioYaRegistradoException("nombre de usuario ya existe");
        }
        
        Usuario usuario = new Usuario(
            username,
            correo,
            passwordEncoder.encode(request.getPassword()),
            Usuario.Rol.ADOPTANTE
        );
        usuario.setFirstName(request.getFirstName());
        usuario.setLastName(request.getLastName());
        usuario.setTelefono(request.getTelefono());
        if (request.getFechaNacimiento() != null) {
            usuario.setFechaNacimiento(java.time.LocalDate.parse(request.getFechaNacimiento()));
        }
        
        Usuario guardado = usuarios.save(usuario);
        return new AuthResponse(guardado.getId(), guardado.getCorreo(), guardado.getRol().name());
    }

    public LoginResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        Usuario usuario = usuarios.findByCorreo(correo)
            .orElseThrow(() -> new CredencialesInvalidasException("credenciales inv�lidas"));
        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(java.time.LocalDateTime.now())) {
                throw new AuthCuentaBloqueadaException("cuenta bloqueada temporalmente");
            }
            // bloqueo vencido: los 2 intentos ya se usaron, empieza de 0
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarios.save(usuario);
        }
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);
            if (usuario.getIntentosFallidos() >= 2) {
                usuario.setBloqueadoHasta(java.time.LocalDateTime.now().plusMinutes(5));
            }
            usuarios.save(usuario);
            throw new CredencialesInvalidasException("credenciales inv�lidas");
        }
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarios.save(usuario);
        String token = jwtService.generarToken(usuario);
        AuthResponse user = new AuthResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol().name());
        return new LoginResponse(token, user);
    }

    /**
     * Solicita recuperaci�n de contrase�a (US-24).
     * Genera token JWT tipo "password_reset" con expiraci�n 15 min, guarda
     * SOLO su hash en tokens_recuperacion (un solo uso) e invalida los
     * enlaces pendientes anteriores. Env�a email con el enlace.
     * Respuesta gen�rica para no revelar si el correo existe.
     */
    @Transactional
    public void solicitarResetPassword(String correo) {
        String email = correo.trim().toLowerCase();
        Usuario usuario = usuarios.findByCorreo(email).orElse(null);

        if (usuario == null) {
            // Siempre respuesta gen�rica (no revelar si existe)
            return;
        }

        // Un solo enlace activo por usuario: los pendientes quedan invalidados
        tokens.invalidarPendientes(usuario.getId(), LocalDateTime.now());

        String token = jwtService.generarTokenPasswordReset(usuario);
        RecuperacionToken registro = new RecuperacionToken(
                usuario.getId(),
                hashSha256(token),
                LocalDateTime.now().plusMinutes(15));
        tokens.save(registro);

        String resetUrl = frontendUrl + "/recuperacion?token=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(usuario.getCorreo());
            message.setSubject("Recuperaci�n de contrase�a - Petly");
            message.setText(
                "Hola " + usuario.getUsername() + ",\n\n" +
                "Para restablecer tu contrase�a, haz clic en el siguiente enlace:\n" +
                resetUrl + "\n\n" +
                "El enlace expira en 15 minutos y solo puede usarse una vez.\n\n" +
                "Si no solicitaste esto, ignora este correo.\n\n" +
                "Equipo Petly"
            );
            mailSender.send(message);
        } catch (Exception e) {
            // Log error pero no revelar si el email fall�
            System.err.println("Error enviando email de recuperaci�n: " + e.getMessage());
        }
    }

    /**
     * Confirma recuperaci�n de contrase�a (US-24).
     * Valida que las contrase�as coincidan, que el token sea de tipo
     * password_reset, no est� expirado y no haya sido usado; luego lo
     * consume (un solo uso) y actualiza la contrase�a.
     */
    @Transactional
    public void confirmarResetPassword(String token, String nuevaPassword, String confirmacion) {
        if (!nuevaPassword.equals(confirmacion)) {
            throw new IllegalArgumentException("Las contrase�as no coinciden");
        }

        Long userId;
        try {
            userId = jwtService.validarTokenPasswordReset(token);
        } catch (IllegalArgumentException e) {
            throw new TokenRecuperacionInvalidoException("Token de recuperaci�n inv�lido");
        } catch (io.jsonwebtoken.JwtException e) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperaci�n es inv�lido o expi�");
        }

        String hash = hashSha256(token);
        RecuperacionToken registro = tokens.findByTokenHash(hash)
            .orElseThrow(() -> new TokenRecuperacionInvalidoException("Token de recuperaci�n inv�lido"));

        if (!registro.getUsuarioId().equals(userId)) {
            throw new TokenRecuperacionInvalidoException("Token de recuperaci�n inv�lido");
        }
        if (registro.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperaci�n expi�");
        }

        // Consumo at�mico: si ya fue usado, no se actualiza nada
        int consumidos = tokens.marcarComoUsado(hash, LocalDateTime.now());
        if (consumidos == 0) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperaci�n ya fue usado");
        }

        Usuario usuario = usuarios.findById(userId)
            .orElseThrow(() -> new TokenRecuperacionInvalidoException("Usuario no encontrado"));

        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarios.save(usuario);
    }

    private String hashSha256(String valor) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}