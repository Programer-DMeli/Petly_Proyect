package com.petly.autorizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;

/**
 * Persistencia de autorizaciones de consulta (US-18).
 * Requiere MariaDB en marcha; cada test revierte al final (@Transactional).
 */
@SpringBootTest
@Transactional
class AutorizacionRepositoryTest {

    @Autowired
    private AutorizacionRepository autorizaciones;

    @Autowired
    private UsuarioRepository usuarios;

    private Usuario adoptante(String sufijo) {
        return usuarios.save(new Usuario(
                "autorizacion" + sufijo,
                "autorizacion" + sufijo + "@petly.test",
                "hash",
                Usuario.Rol.ADOPTANTE));
    }

    @Test
    void guardaYBuscaPorAdoptanteYAlbergue() {
        Usuario u = adoptante("repo1");

        Autorizacion guardada = autorizaciones.save(new Autorizacion(u.getId(), 7L));

        assertNotNull(guardada.getId());
        Optional<Autorizacion> encontrada =
                autorizaciones.findByAdoptanteIdAndAlbergueId(u.getId(), 7L);
        assertTrue(encontrada.isPresent());
        assertEquals(EstadoAutorizacion.ACTIVA, encontrada.get().getEstado());
    }

    @Test
    void detectaAutorizacionActiva() {
        Usuario u = adoptante("repo2");
        autorizaciones.save(new Autorizacion(u.getId(), 9L));

        assertTrue(autorizaciones.existsByAdoptanteIdAndAlbergueIdAndEstado(
                u.getId(), 9L, EstadoAutorizacion.ACTIVA));
    }
}
