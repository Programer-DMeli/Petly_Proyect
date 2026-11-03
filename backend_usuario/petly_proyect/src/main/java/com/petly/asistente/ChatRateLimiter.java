package com.petly.asistente;

import com.petly.common.exception.ApiException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {
    private record Window(long start, int count) {}
    private final Map<Long, Window> users = new HashMap<>();
    private final Clock clock;

    public ChatRateLimiter() { this(Clock.systemUTC()); }
    ChatRateLimiter(Clock clock) { this.clock = clock; }

    public synchronized void acquire(long userId) {
        long now = clock.millis();
        users.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60_000);
        Window window = users.get(userId);
        if ((window != null && window.count() >= 12) || (window == null && users.size() >= 5000)) {
            throw new ApiException(429, "Has enviado varias consultas seguidas. Espera un minuto antes de continuar.");
        }
        users.put(userId, window == null ? new Window(now, 1) : new Window(window.start(), window.count() + 1));
    }
}
