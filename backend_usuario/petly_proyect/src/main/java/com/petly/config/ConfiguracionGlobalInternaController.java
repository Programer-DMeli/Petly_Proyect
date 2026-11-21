package com.petly.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración mínima para que Django aplique el máximo global de imágenes. */
@RestController
@RequestMapping("/api/interno/configuracion-global")
public class ConfiguracionGlobalInternaController {
    private final ConfiguracionGlobalService configuracion;

    public ConfiguracionGlobalInternaController(ConfiguracionGlobalService configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping
    public ConfiguracionGlobalResponse obtener() {
        return ConfiguracionGlobalResponse.desde(configuracion.obtener());
    }
}
