package com.petly.asistente;

import java.util.List;

public final class AsistenteContenidoDtos {
    private AsistenteContenidoDtos() {}

    public record PreguntaFrecuente(String id, String pregunta, String respuesta) {}

    public record CategoriaPreguntasFrecuentes(
            String id,
            String nombre,
            List<PreguntaFrecuente> preguntas) {}

    public record ConsejoCuidado(String categoria, String titulo, String contenido) {}

    public record RespuestaCuidados(
            String especie,
            String raza,
            boolean recomendacionEspecifica,
            String mensaje,
            List<ConsejoCuidado> consejos) {}

    public record GuiaCuestionario(String campo, String pregunta, String orientacion, int orden) {}
}
