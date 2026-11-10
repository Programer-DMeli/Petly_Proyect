package com.petly.asistente;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

/** Real MariaDB in a disposable data directory; never reads Petly's .env or database. */
class ChatMetricsMariaDbTest {
    @TempDir static Path directory;
    private static Process server;
    private static String url;

    @BeforeAll static void startIsolatedMariaDbAndApplyPublishedMigrations() throws Exception {
        Path data = Files.createDirectory(directory.resolve("data"));
        Path log = directory.resolve("mariadb.log");
        try {
            var install = new ProcessBuilder("mariadb-install-db", "--no-defaults",
                    "--datadir=" + data, "--auth-root-authentication-method=normal", "--skip-test-db")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!install.waitFor(30, TimeUnit.SECONDS)) {
                install.destroyForcibly();
                assertTrue(install.waitFor(10, TimeUnit.SECONDS), "MariaDB initializer did not stop");
                fail("MariaDB initialization timed out");
            }
            assertEquals(0, install.exitValue(), () -> readLog(log));
            int port;
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            server = new ProcessBuilder("mariadbd", "--no-defaults", "--datadir=" + data,
                    "--socket=" + directory.resolve("db.sock"), "--pid-file=" + directory.resolve("db.pid"),
                    "--bind-address=127.0.0.1", "--port=" + port, "--skip-log-bin")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
            var admin = new JdbcTemplate(new DriverManagerDataSource(
                    "jdbc:mariadb://127.0.0.1:" + port + "/?connectTimeout=500&socketTimeout=1000", "root", ""));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            boolean ready = false;
            while (server.isAlive() && System.nanoTime() < deadline) {
                try {
                    admin.execute("CREATE DATABASE petly_test_chat_metrics CHARACTER SET utf8mb4");
                    ready = true;
                    break;
                } catch (org.springframework.dao.DataAccessException notReady) { Thread.sleep(100); }
            }
            assertTrue(ready, () -> "Isolated MariaDB did not start: " + readLog(log));
            url = "jdbc:mariadb://127.0.0.1:" + port + "/petly_test_chat_metrics";
            var flyway = Flyway.configure().dataSource(url, "root", "")
                    .locations("classpath:db/migration").target("8").baselineOnMigrate(false).load();
            flyway.migrate();
            assertEquals("8", flyway.info().current().getVersion().getVersion());
            assertTrue(flyway.info().current().getState().isApplied());
        } catch (IOException missingBinary) {
            fail("This integration test requires mariadb-install-db and mariadbd on PATH; no real database is used.", missingBinary);
        } finally {
            // @AfterAll also handles assertion failures after the server was created.
            if (url == null && server != null) stopMariaDb();
        }
    }

    @AfterAll static void stopMariaDb() throws InterruptedException {
        if (server != null && server.isAlive()) {
            server.destroy();
            if (!server.waitFor(10, TimeUnit.SECONDS)) {
                server.destroyForcibly();
                assertTrue(server.waitFor(10, TimeUnit.SECONDS), "Temporary MariaDB did not stop");
            }
        }
    }

    private static String readLog(Path log) {
        try { return Files.readString(log); }
        catch (IOException error) { return "Could not read temporary MariaDB log"; }
    }

    @Test void v8AccumulatesDailyCountsAndANewServiceReadsThemWithoutPrivateData() {
        var db = new JdbcTemplate(new DriverManagerDataSource(url, "root", ""));
        assertEquals("MariaDB", assertDoesNotThrow(() -> {
            try (var connection = db.getDataSource().getConnection()) {
                assertTrue(connection.getMetaData().getDatabaseProductVersion().contains("MariaDB"));
                return connection.getMetaData().getDatabaseProductName();
            }
        }));
        assertEquals(Map.of("perfil_cuestionario", 0L, "proceso_adopcion", 0L, "cuidados", 0L, "otras_consultas", 0L),
                new ChatMetrics(db).snapshot().get("temas"));

        var first = new ChatMetrics(db);
        first.record("Mi perfil para adoptar: persona-privada@example.test, DNI 12345678; vacunas");
        first.record("¿Qué vacunas y comida necesita?");
        first.record("¡Buenos días!");

        // A separate connection/service must read persisted data, including multiple days.
        var independentDb = new JdbcTemplate(new DriverManagerDataSource(url, "root", ""));
        assertEquals(Map.of("perfil_cuestionario", 1L, "proceso_adopcion", 1L, "cuidados", 2L, "otras_consultas", 1L),
                new ChatMetrics(independentDb).snapshot().get("temas"));
        assertEquals(4, independentDb.queryForObject("SELECT COUNT(*) FROM asistente_metricas_diarias WHERE fecha=CURRENT_DATE", Integer.class));
        independentDb.update("INSERT INTO asistente_metricas_diarias(fecha, tema, consultas) VALUES (CURRENT_DATE - INTERVAL 1 DAY, ?, ?)",
                "cuidados", 3L);
        assertEquals(Map.of("perfil_cuestionario", 1L, "proceso_adopcion", 1L, "cuidados", 5L, "otras_consultas", 1L),
                new ChatMetrics(independentDb).snapshot().get("temas"));
        assertEquals(List.of("fecha", "tema", "consultas"), independentDb.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() "
                        + "AND TABLE_NAME='asistente_metricas_diarias' ORDER BY ORDINAL_POSITION", String.class));
        var stored = independentDb.queryForList("SELECT * FROM asistente_metricas_diarias").toString();
        assertFalse(stored.contains("persona-privada"));
        assertFalse(stored.contains("12345678"));
        assertFalse(stored.contains("vacunas"));
    }
}
