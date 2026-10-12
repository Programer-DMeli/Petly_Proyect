package com.petly.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para solicitar recuperación de contraseña (US-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de recuperación de contraseña")
public class PasswordResetRequest {

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Formato de email inválido")
    @Schema(example = "usuario@email.com", description = "Correo del usuario")
    private String correo;
}