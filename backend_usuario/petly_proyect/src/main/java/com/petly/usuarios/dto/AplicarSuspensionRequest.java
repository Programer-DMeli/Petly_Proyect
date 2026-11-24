package com.petly.usuarios.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AplicarSuspensionRequest(
        @NotBlank @Size(max = 500) String motivo,
        LocalDateTime hasta) {}
