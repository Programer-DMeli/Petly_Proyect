package com.petly.asistente;

import static com.petly.asistente.AsistenteContenidoDtos.*;

import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.petly.common.exception.ApiException;

@RestController
@RequestMapping("/api/asistente")
public class AsistenteContenidoController {
    private static final int MAX_RAZA_LENGTH = 80;
    private final AsistenteContenidoService contenido;

    public AsistenteContenidoController(AsistenteContenidoService contenido) {
        this.contenido = contenido;
    }

    @GetMapping("/preguntas-frecuentes")
    public List<CategoriaPreguntasFrecuentes> preguntasFrecuentes() {
        return contenido.preguntasFrecuentes();
    }

    @GetMapping("/cuidados")
    public RespuestaCuidados cuidados(
            @RequestParam String especie,
            @RequestParam(required = false, defaultValue = "") String raza) {
        String especieNormalizada = especie.trim().toLowerCase(Locale.ROOT);
        if (!List.of("perro", "gato").contains(especieNormalizada)) {
            throw new ApiException(400, "La especie debe ser perro o gato.");
        }
        String razaNormalizada = raza.trim();
        if (razaNormalizada.length() > MAX_RAZA_LENGTH) {
            throw new ApiException(400, "La raza no puede superar 80 caracteres.");
        }
        return contenido.cuidados(especieNormalizada, razaNormalizada);
    }

    @GetMapping("/cuestionario/guia")
    public List<GuiaCuestionario> guiaCuestionario() {
        return contenido.guiaCuestionario();
    }
}
