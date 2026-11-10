package com.petly.asistente;

import com.petly.common.exception.ApiException;
import java.time.Clock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRateLimiterTest {
    @Test void permitsTwelveQueriesAndLimitsEachUserIndependently() {
        var limiter = new ChatRateLimiter();
        for (int i = 0; i < 12; i++) limiter.acquire(1);
        assertEquals(429, assertThrows(ApiException.class, () -> limiter.acquire(1)).status);
        assertDoesNotThrow(() -> limiter.acquire(2));
    }

    @Test void resetsAtTheEndOfTheMinuteWithoutSleeping() {
        var clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var limiter = new ChatRateLimiter(clock);
        for (int i = 0; i < 12; i++) limiter.acquire(1);
        when(clock.millis()).thenReturn(59_999L);
        assertEquals(429, assertThrows(ApiException.class, () -> limiter.acquire(1)).status);
        when(clock.millis()).thenReturn(60_000L);
        assertDoesNotThrow(() -> limiter.acquire(1));
    }
}
