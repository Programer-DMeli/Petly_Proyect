package com.petly.auth;

import com.petly.auth.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.repository.UsuarioRepository;
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
     * Respuesta genérica para no revelar si el correo existe.
     */
    public void solicitarResetPassword(String correo) {
        String email = correo.trim().toLowerCase();
        Usuario usuario = usuarios.findByCorreo(email).orElse(null);
        
        if (usuario != null) {
            String token = jwtService.generarTokenPasswordReset(usuario);
            // TODO: Enviar email con token (configurar JavaMailSender + template)
            // Ejemplo: emailService.enviarResetPassword(usuario.getCorreo(), token);
            System.out.println("=== RESET PASSWORD TOKEN para " + email + " ===");
            System.out.println(token);
            System.out.println("========================================");
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
}
