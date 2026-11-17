package com.petly.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sesiones_renovables")
class SesionRenovable {
    @Id @Column(length = 36)
    String id;
    @Column(nullable = false)
    Long usuarioId;
    @Column(nullable = false)
    int tokenVersion;
    @Column(nullable = false, length = 64)
    String tokenHash;
    @Column(length = 64)
    String anteriorHash;
    @Column(length = 64)
    String operacionHash;
    @Column(nullable = false)
    Instant expiraEn;
    Instant revocadaEn;

    protected SesionRenovable() {}

    SesionRenovable(String id, Long usuarioId, int version, String hash, Instant expiraEn) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.tokenVersion = version;
        this.tokenHash = hash;
        this.expiraEn = expiraEn;
    }
}
