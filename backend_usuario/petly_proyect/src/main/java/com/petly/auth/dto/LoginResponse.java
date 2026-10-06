package com.petly.auth.dto;

/**
 * Respuesta del login con el JWT (US-21, Fase 3).
 */
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private long expiresIn = 3600;
    private AuthResponse user;

    public LoginResponse(String token, AuthResponse user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public AuthResponse getUser() {
        return user;
    }
}
