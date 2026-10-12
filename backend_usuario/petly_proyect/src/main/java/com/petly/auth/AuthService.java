package com.petly.auth;

import com.petly.auth.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
     * Genera token JWT tipo "password_reset" con expiración 15 min.
     * Envía email con enlace de recuperación.
     * Respuesta genérica para no revelar si el correo existe.
     */
    public void solicitarResetPassword(String correo) {
        String email = correo.trim().toLowerCase();
        Usuario usuario = usuarios.findByCorreo(email).orElse(null);
        
        if (usuario != null) {
            String token = jwtService.generarTokenPasswordReset(usuario);
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
                    "El enlace expira en 15 minutos.\n\n" +
                    "Si no solicitaste esto, ignora este correo.\n\n" +
                    "Equipo Petly"
                );
                mailSender.send(message);
            } catch (Exception e) {
                // Log error pero no revelar si el email falló
                System.err.println("Error enviando email de recuperación: " + e.getMessage());
            }
        }
        // Siempre respuesta genérica (no revelar si existe)
    }

    /**
     * Confirma recuperación de contraseña (US-24).
     * Valida token (tipo, expiración, firma), actualiza password.
     */
    public void confirmarResetPassword(String token, String nuevaPassword) {
        Long userId = jwtService.validarTokenPasswordReset(token);
        Usuario usuario = usuarios.findById(userId)
            .orElseThrow(() -> new RuntimeException("usuario no encontrado"));
        
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarios.save(usuario);
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
