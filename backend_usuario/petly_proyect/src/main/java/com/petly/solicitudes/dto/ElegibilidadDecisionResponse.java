package com.petly.solicitudes.dto;

public record ElegibilidadDecisionResponse(
        boolean perfilFinalizado,
        boolean autorizacionVigente) {
}
