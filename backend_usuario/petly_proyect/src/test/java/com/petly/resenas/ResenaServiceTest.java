package com.petly.resenas;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.petly.common.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ResenaServiceTest {
    private JdbcTemplate db;
    private RestTemplate http;
    private ResenaService service;

    @BeforeEach
    void preparar() {
        var origen = new DriverManagerDataSource(
                "jdbc:h2:mem:resenas-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(origen);
        db.execute("CREATE TABLE usuarios (id BIGINT PRIMARY KEY)");
        db.execute("CREATE TABLE albergues (id BIGINT PRIMARY KEY)");
        db.update("INSERT INTO usuarios (id) VALUES (1), (2)");
        db.update("INSERT INTO albergues (id) VALUES (9), (10)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V14__crear_resenas_albergue.sql"))
                .execute(origen);
        db.execute("ALTER TABLE resenas_albergue ADD estado_moderacion VARCHAR(8) DEFAULT 'VISIBLE' NOT NULL");
        db.execute("ALTER TABLE resenas_albergue ADD respuesta_albergue VARCHAR(1000)");
        db.execute("ALTER TABLE resenas_albergue ADD moderada_en TIMESTAMP");
        http = new RestTemplate();
        service = new ResenaService(new ResenaRepository(db), http, "http://django.test", "solo-pruebas");
    }

    @Test
    void registraUnaResenaYDetectaReintentosInclusoSiFueOcultada() {
        var django = MockRestServiceServer.bindTo(http).build();
        django.expect(requestTo(urlElegibilidad())).andRespond(withSuccess("{\"elegible\":true}", MediaType.APPLICATION_JSON));
        assertFalse(service.estadoPropio(1, 9).registrada());

        var resena = new CrearResenaRequest(4, "  Atención amable.  ");
        assertEquals(1, service.crear(1, 9, resena).total());
        db.update("UPDATE resenas_albergue SET estado_moderacion = 'OCULTA'");

        assertEquals(new ResenaService.EstadoResena(true, 4), service.estadoPropio(1, 9));
        assertFalse(service.estadoPropio(2, 9).registrada());
        assertFalse(service.estadoPropio(1, 10).registrada());
        assertEquals(409, assertThrows(ApiException.class, () -> service.crear(1, 9, resena)).status);
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM resenas_albergue", Integer.class));
        assertEquals("Atención amable.", db.queryForObject("SELECT comentario FROM resenas_albergue", String.class));
        assertEquals(0, service.obtener(9).total());
        django.verify();
    }

    @Test
    void dosEnviosConcurrentesGuardanUnaSolaResena() throws Exception {
        var barrera = new CyclicBarrier(2);
        var django = MockRestServiceServer.bindTo(http).build();
        django.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(urlElegibilidad()))
                .andRespond(request -> {
                    try {
                        barrera.await(10, TimeUnit.SECONDS);
                    } catch (Exception error) {
                        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                        throw new java.io.IOException(error);
                    }
                    return withSuccess("{\"elegible\":true}", MediaType.APPLICATION_JSON).createResponse(request);
                });

        try (var trabajadores = Executors.newFixedThreadPool(2)) {
            var primero = trabajadores.submit(this::enviar);
            var segundo = trabajadores.submit(this::enviar);
            assertEquals(java.util.Set.of(201, 409), java.util.Set.of(
                    primero.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)));
        }
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM resenas_albergue", Integer.class));
        django.verify();
    }

    private int enviar() {
        try {
            service.crear(1, 9, new CrearResenaRequest(5, "Excelente atención."));
            return 201;
        } catch (ApiException error) {
            return error.status;
        }
    }

    private String urlElegibilidad() {
        return "http://django.test/api/contratos/resenas/eligibilidad/?adoptante_id=1&albergue_id=9";
    }
}
