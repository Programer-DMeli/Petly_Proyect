package com.petly.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para confirmar recuperación de contraseña (US-24).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Confirmación de recuperación de contraseña")
public class PasswordResetConfirmRequest {

    @NotBlank(message = "El token es obligatorio")
    @Schema(example = "eyJhbGciOiJIUzI1NiIs...", description = "Token JWT de recuperación")
    private String token;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    @Schema(example = "NuevaPass123!", description = "Nueva contraseña (mín 8 caracteres)")
    private String nuevaPassword;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    @Schema(example = "NuevaPass123!", description = "Confirmación de nueva contraseña")
    private String nuevaPasswordConfirm;
}