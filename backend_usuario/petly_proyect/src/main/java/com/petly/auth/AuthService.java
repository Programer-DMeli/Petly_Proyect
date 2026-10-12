package com.petly.auth;

import com.petly.auth.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registro, login y recuperación de contraseña (US-21, US-24).
 * Verificación de autorización con Django (US-18).
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

    @Autowired
    private RestTemplate restTemplate;

    @Value("${spring.mail.from:noreply@petly.local}")
    private String mailFrom;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @Value("${DJANGO_API_URL:http://localhost:8000}")
    private String djangoApiUrl;

    public AuthResponse registrar(RegistroRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        String username = request.getUsername().trim().toLowerCase();
        
        if (usuarios.existsByCorreo(correo)) {
            throw new CorreoYaRegistradoException("correo ya registrado");
        }
        if (usuarios.existsByUsername(username)) {
            throw new RuntimeException("nombre de usuario ya existe");
        }
        
        Usuario usuario = new Usuario(
            username,
            correo,
            passwordEncoder.encode(request.getPassword()),
            Usuario.Rol.valueOf(request.getRol().toUpperCase())
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
            .orElseThrow(() -> new RuntimeException("credenciales inválidas"));
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
            throw new RuntimeException("credenciales inválidas");
        }
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarios.save(usuario);
        String token = jwtService.generarToken(usuario);
        AuthResponse user = new AuthResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol().name());
        return new LoginResponse(token, user);
    }

    /**
     * Solicita recuperación de contraseña (US-24).
     * Genera token JWT tipo "password_reset" con expiración 15 min, guarda
     * SOLO su hash en tokens_recuperacion (un solo uso) e invalida los
     * enlaces pendientes anteriores. Envía email con el enlace.
     * Respuesta genérica para no revelar si el correo existe.
     */
    @Transactional
    public void solicitarResetPassword(String correo) {
        String email = correo.trim().toLowerCase();
        Usuario usuario = usuarios.findByCorreo(email).orElse(null);

        if (usuario == null) {
            // Siempre respuesta genérica (no revelar si existe)
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
            message.setSubject("Recuperación de contraseña - Petly");
            message.setText(
                "Hola " + usuario.getUsername() + ",\n\n" +
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
     * Valida que las contraseñas coincidan, que el token sea de tipo
     * password_reset, no esté expirado y no haya sido usado; luego lo
     * consume (un solo uso) y actualiza la contraseña.
     */
    @Transactional
    public void confirmarResetPassword(String token, String nuevaPassword, String confirmacion) {
        if (!nuevaPassword.equals(confirmacion)) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }

        Long userId;
        try {
            userId = jwtService.validarTokenPasswordReset(token);
        } catch (IllegalArgumentException e) {
            throw new TokenRecuperacionInvalidoException("Token de recuperación inválido");
        } catch (io.jsonwebtoken.JwtException e) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperación es inválido o expiró");
        }

        String hash = hashSha256(token);
        RecuperacionToken registro = tokens.findByTokenHash(hash)
            .orElseThrow(() -> new TokenRecuperacionInvalidoException("Token de recuperación inválido"));

        if (!registro.getUsuarioId().equals(userId)) {
            throw new TokenRecuperacionInvalidoException("Token de recuperación inválido");
        }
        if (registro.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperación expiró");
        }

        // Consumo atómico: si ya fue usado, no se actualiza nada
        int consumidos = tokens.marcarComoUsado(hash, LocalDateTime.now());
        if (consumidos == 0) {
            throw new TokenRecuperacionInvalidoException("El enlace de recuperación ya fue usado");
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

    /**
     * Verifica autorización de perfil con Django (US-18).
     * Llama a endpoint interno de Django /api/interno/autorizacion/
     * para verificar si un adoptante autorizó a un albergue específico.
     */
    public boolean verificarAutorizacion(Long adoptanteId, Long albergueId, String djangoToken) {
        try {
            String url = djangoApiUrl + "/api/acceso/interno/autorizacion/";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBearerAuth(djangoToken);
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("adoptante_id", adoptanteId.toString());
            params.add("albergue_id", albergueId.toString());
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            var response = restTemplate.postForEntity(url, request, AutorizacionResponse.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().isAutorizado();
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error verificando autorización con Django: " + e.getMessage());
            return false;
        }
    }

    // Response class for autorización check
    public static class AutorizacionResponse {
        private boolean autorizado;
        private java.util.List<String> campos_permitidos;
        
        public boolean isAutorizado() { return autorizado; }
        public void setAutorizado(boolean autorizado) { this.autorizado = autorizado; }
        public java.util.List<String> getCampos_permitidos() { return campos_permitidos; }
        public void setCampos_permitidos(java.util.List<String> campos_permitidos) { this.campos_permitidos = campos_permitidos; }
    }
}
