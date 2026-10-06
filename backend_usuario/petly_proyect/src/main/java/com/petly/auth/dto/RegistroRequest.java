package com.petly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cuenta de adoptante (US-21).
 * Solo correo y contraseña en el Sprint 1.
 */
public class RegistroRequest {

    @Email(message = "correo inválido")
    @NotBlank(message = "correo requerido")
    private String correo;

    @NotBlank(message = "contraseña requerida")
    @Size(min = 8, max = 72, message = "la contraseña debe tener entre 8 y 72 caracteres")
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
