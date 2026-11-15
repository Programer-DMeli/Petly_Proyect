package com.petly.comunidad;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InteraccionesCasoExitoResponse(
        @JsonProperty("caso_id") Long casoId,
        @JsonProperty("total_me_gusta") long totalMeGusta,
        @JsonProperty("me_gusta_usuario") boolean meGustaUsuario,
        @JsonProperty("total_comentarios") long totalComentarios,
        List<ComentarioCasoExitoResponse> comentarios) {
}
