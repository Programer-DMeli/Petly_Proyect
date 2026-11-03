package com.petly.asistente;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.Authentication;
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

    public ChatController(ChatGeminiService gemini, ChatRateLimiter limiter, ChatMetrics metrics) {
        this.gemini = gemini;
        this.limiter = limiter;
        this.metrics = metrics;
    }

    @PostMapping("/api/asistente/chat")
    public Map<String, String> chat(@Valid @RequestBody ChatRequest request, Authentication auth) {
        ChatGeminiService.validateConversation(request);
        limiter.acquire(Long.parseLong(((Claims) auth.getPrincipal()).getSubject()));
        String answer = gemini.reply(request, ((Claims) auth.getPrincipal()).get("rol", String.class));
        metrics.record(request.messages().get(request.messages().size() - 1).text());
        return Map.of("reply", answer);
    }

    @GetMapping("/api/admin/asistente/metricas")
    public Map<String, Object> metrics() { return metrics.snapshot(); }
}
