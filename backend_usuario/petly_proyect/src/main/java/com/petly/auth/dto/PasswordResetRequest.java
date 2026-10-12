package com.petly.auth.dto;
import jakarta.validation.constraints.*;
public class PasswordResetRequest {
 @NotBlank @Email private String correo;
 public String getCorreo() { return correo; }
 public void setCorreo(String value) { correo = value; }
}
