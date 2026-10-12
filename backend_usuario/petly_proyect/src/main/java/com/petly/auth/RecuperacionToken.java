package com.petly.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Token de recuperacion de contrasena (US-24, Persona B).
 * Tabla creada por Flyway V2; Hibernate no la crea (ddl-auto: none).
 * Se guarda solo el hash del token, nunca el token en claro.
 */
@Entity
@Table(name = "tokens_recuperacion", uniqueConstraints = @UniqueConstraint(columnNames = "token_hash"))
public class RecuperacionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RecuperacionToken() {
    }

    public RecuperacionToken(Long usuarioId, String tokenHash, LocalDateTime expiraEn) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public LocalDateTime getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(LocalDateTime usadoEn) {
        this.usadoEn = usadoEn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
