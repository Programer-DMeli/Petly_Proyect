package com.petly.notificaciones;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta y actualización de canales de notificación del propio usuario (US-45).
 * El usuario sale del token, nunca del cuerpo.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class PreferenciasNotificacionController {
    private final PreferenciasNotificacionService service;

    public PreferenciasNotificacionController(PreferenciasNotificacionService service) {
        this.service = service;
    }

    @GetMapping("/preferencias")
    public PreferenciaNotificacion obtener(Authentication authentication) {
        return service.obtener(id(authentication));
    }

    @PutMapping("/preferencias")
    public PreferenciaNotificacion guardar(Authentication authentication,
                                           @RequestBody PreferenciaNotificacion preferencia) {
        return service.guardar(id(authentication), preferencia);
    }

    private static long id(Authentication authentication) {
        return Long.parseLong(((Claims) authentication.getPrincipal()).getSubject());
    }
}
