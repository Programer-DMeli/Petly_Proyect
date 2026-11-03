import com.fasterxml.jackson.databind.ObjectMapper;
import com.petly.asistente.ChatGeminiService;
import com.petly.asistente.ChatRequest;
import com.petly.common.exception.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Opt-in evaluation against the real Java adapter; all cases use synthetic questions. */
public class PetlyChatEvaluation {
    private static long nextStart;
    private static synchronized void waitForSlot() throws InterruptedException {
        long remaining = nextStart - System.nanoTime();
        if (remaining > 0) Thread.sleep(java.time.Duration.ofNanos(remaining));
        nextStart = System.nanoTime() + 6_000_000_000L; // At most ten cases per minute.
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Indica archivo de casos y archivo de informe.");
        var json = new ObjectMapper();
        String key = System.getenv("GEMINI_API_KEY");
        String model = System.getenv("GEMINI_CHAT_MODEL");
        if (key == null || key.isBlank() || model == null || model.isBlank())
            throw new IllegalStateException("Configura las credenciales en el entorno privado del servidor.");
        var service = new ChatGeminiService(key, model, 40);
        var cases = json.readTree(Files.readString(Path.of(args[0])));
        List<Callable<Map<String, Object>>> tasks = new ArrayList<>();
        for (var test : cases) {
            tasks.add(() -> {
                waitForSlot();
                List<ChatRequest.Message> messages = new ArrayList<>();
                for (var message : test.path("messages"))
                    messages.add(new ChatRequest.Message(message.path("role").asText(), message.path("text").asText()));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", test.path("id").asText());
                result.put("area", test.path("area").asText());
                result.put("messages", messages);
                result.put("criterios", json.convertValue(test.path("criterios"), List.class));
                long started = System.nanoTime();
                try {
                    result.put("respuesta", service.reply(new ChatRequest(messages)));
                    result.put("estado", "respuesta_recibida");
                } catch (ApiException ex) {
                    result.put("estado", "sin_respuesta");
                    result.put("http", ex.status);
                    result.put("error", ex.getMessage());
                }
                result.put("duracionMs", (System.nanoTime() - started) / 1_000_000);
                result.put("evaluacion", "pendiente_revision_humana");
                System.out.println(test.path("id").asText() + " " + result.get("estado"));
                return result;
            });
        }
        List<Map<String, Object>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var future : pool.invokeAll(tasks)) results.add(future.get());
        }
        var context = ChatGeminiService.class.getDeclaredField("CONTEXT");
        context.setAccessible(true);
        String instructionsHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(((String) context.get(null)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var report = Map.of("instruccionesSha256", instructionsHash, "fecha", ZonedDateTime.now(ZoneId.of("America/Lima")).toString(),
                "modelo", model, "proveedor", "Gemini real mediante ChatGeminiService Java",
                "datos", "preguntas sintéticas; sin cuentas ni datos personales",
                "alcance", "Evaluación de contenido del adaptador real; autenticación e interfaz se verifican por separado.",
                "casos", results);
        Path output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
        System.out.println("Informe guardado: " + output);
        if (results.stream().anyMatch(result -> !"respuesta_recibida".equals(result.get("estado")))) System.exit(2);
    }
}
