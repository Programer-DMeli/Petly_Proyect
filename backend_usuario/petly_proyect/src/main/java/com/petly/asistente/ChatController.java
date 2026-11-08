package com.petly.asistente;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import io.jsonwebtoken.Claims;

@RestController
public class ChatController {
    private final ChatGeminiService gemini;
    private final ChatRateLimiter limiter;
    private final ChatMetrics metrics;
    private final RespuestaAutomaticaChatbotClient respuestasAutomaticas;

    public ChatController(
            ChatGeminiService gemini,
            ChatRateLimiter limiter,
            ChatMetrics metrics,
            RespuestaAutomaticaChatbotClient respuestasAutomaticas) {
        this.gemini = gemini;
        this.limiter = limiter;
        this.metrics = metrics;
        this.respuestasAutomaticas = respuestasAutomaticas;
    }

    @PostMapping("/api/asistente/chat")
    public Map<String, String> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication auth,
            @RequestHeader("Authorization") String authorization) {
        ChatGeminiService.validateConversation(request);
        limiter.acquire(Long.parseLong(((Claims) auth.getPrincipal()).getSubject()));
        String role = ((Claims) auth.getPrincipal()).get("rol", String.class);
        Optional<String> automated = "ADOPTANTE".equals(role)
                ? respuestasAutomaticas.buscar(request.messages().get(request.messages().size() - 1).text(), authorization)
                : Optional.empty();
        String answer = automated.orElseGet(() -> gemini.reply(request, role));
        metrics.record(request.messages().get(request.messages().size() - 1).text());
        return Map.of("reply", answer);
    }

    @GetMapping("/api/admin/asistente/metricas")
    public Map<String, Object> metrics() { return metrics.snapshot(); }
}
