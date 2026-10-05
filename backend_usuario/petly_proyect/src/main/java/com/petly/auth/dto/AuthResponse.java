package com.petly.auth.dto;

/**
 * Datos de la cuenta creada (US-21). Sin token todavía.
 */
public class AuthResponse {

    private Long id;
    private String correo;
    private String rol;

    public AuthResponse(Long id, String correo, String rol) {
        this.id = id;
        this.correo = correo;
        this.rol = rol;
    }

    public Long getId() {
        return id;
    }

    public String getCorreo() {
        return correo;
    }

    public String getRol() {
        return rol;
    }
}
