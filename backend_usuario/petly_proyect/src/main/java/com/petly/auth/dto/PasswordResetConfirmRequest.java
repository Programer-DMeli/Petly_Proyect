package com.petly.auth.dto;
import jakarta.validation.constraints.*;
public class PasswordResetConfirmRequest {
 @NotBlank private String token;
 public String getToken() { return token; }
 public void setToken(String value) { token = value; }
 @NotBlank @Size(min=8,max=72) private String nuevaPassword;
 public String getNuevaPassword() { return nuevaPassword; }
 public void setNuevaPassword(String value) { nuevaPassword = value; }
 @NotBlank private String nuevaPasswordConfirm;
 public String getNuevaPasswordConfirm() { return nuevaPasswordConfirm; }
 public void setNuevaPasswordConfirm(String value) { nuevaPasswordConfirm = value; }
}
