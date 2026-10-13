package com.petly.autorizacion.dto;

import java.time.LocalDateTime;

import com.petly.autorizacion.Autorizacion;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de una autorizacion de consulta (US-18).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Estado de una autorizacion de consulta")
public class AutorizacionResponse {

    private Long id;
    private Long albergueId;
    private String estado;
    private LocalDateTime fechaAutorizacion;
    private LocalDateTime fechaRevocacion;

    public static AutorizacionResponse from(Autorizacion autorizacion) {
        return new AutorizacionResponse(
                autorizacion.getId(),
                autorizacion.getAlbergueId(),
                autorizacion.getEstado().name(),
                autorizacion.getFechaAutorizacion(),
                autorizacion.getFechaRevocacion());
    }
}
