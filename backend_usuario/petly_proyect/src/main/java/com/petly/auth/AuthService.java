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
import java.util.Map;

import com.petly.common.exception.ApiException;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registro, login y recuperación de contraseña (US-21, US-24).
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

    private static final java.security.SecureRandom RESET_RANDOM = new java.security.SecureRandom();

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Value("${petly.web-reset-link-base:http://localhost:5173/recuperacion}")
    private String webResetLinkBase;

    @Value("${petly.reset-link-base:petly://recuperacion}")
    private String resetLinkBase;

    private static void validarPassword(String password, String campo) {
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "datos inválidos",
                    Map.of(campo, "Usa al menos 8 caracteres y hasta 72 bytes UTF-8."));
        }
    }

    @Transactional
    public AuthResponse registrar(RegistroRequest request) {
        validarPassword(request.getPassword(), "password");
        String correo = request.getCorreo().trim().toLowerCase(java.util.Locale.ROOT);
        String username = request.getUsername() == null
                ? "petly_" + java.util.UUID.randomUUID().toString().replace("-", "")
                : request.getUsername().trim().toLowerCase(java.util.Locale.ROOT);
        
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
        if (Boolean.TRUE.equals(request.getAceptoTerminos())) {
            usuario.setAceptoTerminos(true);
            usuario.setFechaAceptacionTerminos(LocalDateTime.now());
        }
        usuario.setLastName(request.getLastName());
        usuario.setTelefono(request.getTelefono());
        if (request.getFechaNacimiento() != null) {
            usuario.setFechaNacimiento(java.time.LocalDate.parse(request.getFechaNacimiento()));
        }
        
        Usuario guardado = usuarios.save(usuario);
        return new AuthResponse(guardado.getId(), guardado.getCorreo(), guardado.getRol().name());
    }

    @Value("${spring.auth.block-duration-minutes:1}")
    private int blockDurationMinutes;

    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, AuthCuentaBloqueadaException.class})
    public LoginResponse login(LoginRequest request) {
        String correo = request.getCorreo().trim().toLowerCase(java.util.Locale.ROOT);
        Usuario usuario = usuarios.bloquearPorCorreo(correo)
                .orElseThrow(() -> new CredencialesInvalidasException("Credenciales inválidas."));
        LocalDateTime now = LocalDateTime.now();
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(now)) {
            long remaining = Math.max(1, (java.time.Duration.between(now, usuario.getBloqueadoHasta()).toMillis() + 999) / 1000);
            throw new AuthCuentaBloqueadaException("Cuenta bloqueada temporalmente.", remaining);
        }
        if (usuario.getBloqueadoHasta() != null) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
        }
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);
            if (usuario.getIntentosFallidos() >= 2) {
                long seconds = Math.max(1, blockDurationMinutes) * 60L;
                usuario.setBloqueadoHasta(now.plusSeconds(seconds));
                usuarios.save(usuario);
                throw new AuthCuentaBloqueadaException("Cuenta bloqueada tras dos fallos.", seconds);
            }
            usuarios.save(usuario);
            throw new CredencialesInvalidasException("Credenciales inválidas.");
        }
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarios.save(usuario);
        String token = jwtService.generarToken(usuario);
        return new LoginResponse(token, new AuthResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol().name()));
    }

    /**
     * Solicita recuperación de contraseña (US-24).
     * Genera token JWT tipo "password_reset" con expiraci�n 15 min, guarda
     * SOLO su hash en tokens_recuperacion (un solo uso) e invalida los
     * enlaces pendientes anteriores. Envía email con el enlace.
     * Respuesta gen�rica para no revelar si el correo existe.
     */
    @Transactional
    public void solicitarResetPassword(String correo) {
        solicitarResetPassword(correo, true);
    }

    /**
     * Solicita recuperación con canal: web usa WEB_RESET_LINK_BASE,
     * android usa el esquema petly:// de la app m�vil.
     */
    @Transactional
    public void solicitarResetPassword(String correo, boolean web) {
        String email = correo.trim().toLowerCase();
        Usuario usuario = usuarios.findByCorreo(email).orElse(null);

        if (usuario == null) {
            // Siempre respuesta gen�rica (no revelar si existe)
            return;
        }

        // Un solo enlace activo por usuario: los pendientes quedan invalidados
        tokens.invalidarPendientes(usuario.getId(), LocalDateTime.now());

        byte[] bytes = new byte[16]; // 128 bits; 22 caracteres Base64 URL sin relleno.
        RESET_RANDOM.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RecuperacionToken registro = new RecuperacionToken(
                usuario.getId(),
                hashSha256(token),
                LocalDateTime.now().plusMinutes(15));
        tokens.save(registro);

        String base = web ? webResetLinkBase : resetLinkBase;
        String resetUrl = base + "?token=" + token;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(usuario.getCorreo());
            message.setSubject("Recuperación de contraseña - Petly");
            message.setText(
                "Hola " + usuario.getUsername() + ",\n\n" +
                "Código de recuperación: " + token + "\n\n" +
                "Para restablecer tu contraseña, haz clic en el siguiente enlace:\n" +
                resetUrl + "\n\n" +
                "El enlace expira en 15 minutos y solo puede usarse una vez.\n\n" +
                "Si no solicitaste esto, ignora este correo.\n\n" +
                "Equipo Petly"
            );
            mailSender.send(message);
        } catch (Exception e) {
            // Log error pero no revelar si el email falló
            System.err.println("Error enviando email de recuperación: " + e.getMessage());
        }
    }

    /**
     * Confirma recuperación de contraseña (US-24).
     * Valida contraseñas y busca el hash del código opaco; comprueba
     * que no haya expirado ni haya sido usado; luego lo
     * consume (un solo uso) y actualiza la contraseña.
     */
    @Transactional
    public void confirmarResetPassword(String token, String nuevaPassword, String confirmacion) {
        if (!nuevaPassword.equals(confirmacion)) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }
        validarPassword(nuevaPassword, "nuevaPassword");

        String hash = hashSha256(token);
        RecuperacionToken registro = tokens.findByTokenHash(hash)
            .orElseThrow(() -> new TokenRecuperacionInvalidoException("Token de recuperación inválido"));

        Long userId = registro.getUsuarioId(); // Identidad de la fila, nunca del código enviado.
        if (!registro.getExpiraEn().isAfter(LocalDateTime.now())) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperación expiró");
        }

        // Consumo at�mico: si ya fue usado, no se actualiza nada
        int consumidos = tokens.marcarComoUsado(hash, LocalDateTime.now());
        if (consumidos == 0) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperación ya fue usado");
        }

        Usuario usuario = usuarios.findById(userId)
            .orElseThrow(() -> new TokenRecuperacionInvalidoException("Usuario no encontrado"));

        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuario.setTokenVersion(usuario.getTokenVersion() + 1);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
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

    public static String hash(String valor) {
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