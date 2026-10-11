package com.petly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos para entrar (US-21).
 */
public class LoginRequest {

    @Email(message = "correo inválido")
    @NotBlank(message = "correo requerido")
    private String correo;

    @NotBlank(message = "contraseña requerida")
    private String password;

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
