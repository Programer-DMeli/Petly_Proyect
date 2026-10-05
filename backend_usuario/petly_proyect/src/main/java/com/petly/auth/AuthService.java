package com.petly.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.petly.auth.dto.AuthResponse;
import com.petly.auth.dto.RegistroRequest;
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

    public AuthResponse registrar(RegistroRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        if (usuarios.existsByCorreo(correo)) {
            throw new RuntimeException("correo ya registrado");
        }
        Usuario usuario = new Usuario(correo, passwordEncoder.encode(request.getPassword()), "ADOPTANTE");
        Usuario guardado = usuarios.save(usuario);
        return new AuthResponse(guardado.getId(), guardado.getCorreo(), guardado.getRol());
    }
}
