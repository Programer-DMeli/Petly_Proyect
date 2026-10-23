package com.petly.autorizacion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para autorizar la consulta del perfil a un albergue (US-18).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Autorizacion de consulta hacia un albergue")
public class AutorizacionRequest {

    @NotNull(message = "El albergue es obligatorio")
    @Positive(message = "albergueId debe ser positivo")
    @Schema(example = "1", description = "Id del albergue (gestionado por Django)")
    private Long albergueId;
}
