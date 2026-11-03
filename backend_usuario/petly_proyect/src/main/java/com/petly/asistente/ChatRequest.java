package com.petly.asistente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(
        @NotEmpty @Size(max = 21) List<@NotNull @Valid Message> messages,
        @Pattern(regexp = "android|web") String platform) {
    public ChatRequest(List<Message> messages) { this(messages, null); }
    public record Message(
            @NotBlank @Pattern(regexp = "user|model") String role,
            @NotBlank @Size(max = 2000) String text) {}
}
