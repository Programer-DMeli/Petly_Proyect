package com.petly.auth;

import com.petly.auth.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registro de adoptantes (US-21).
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
        if (usuarios.existsByCorreo(correo)) {
            throw new CorreoYaRegistradoException("correo ya registrado");
        }
        Usuario usuario = new Usuario(correo, passwordEncoder.encode(request.getPassword()), "ADOPTANTE");
        Usuario guardado = usuarios.save(usuario);
        return new AuthResponse(guardado.getId(), guardado.getCorreo(), guardado.getRol());
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
        AuthResponse user = new AuthResponse(usuario.getId(), usuario.getCorreo(), usuario.getRol());
        return new LoginResponse(token, user);
    }
}
