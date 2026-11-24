package com.petly.config;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Configuración global persistida en MariaDB y administrada por Spring/Flyway. */
@Entity
@Table(name = "configuracion_global")
public class ConfiguracionGlobal {
    public static final String CLAVE_GLOBAL = "GLOBAL";
    public static final int MAX_IMAGEN_MB_PREDETERMINADO = 5;
    public static final int EXPIRACION_SESION_DIAS_PREDETERMINADA = 30;

    @Id
    @Column(name = "clave", nullable = false, length = 16)
    private String clave = CLAVE_GLOBAL;

    @Column(name = "max_imagen_mb", nullable = false)
    private int maxImagenMb = MAX_IMAGEN_MB_PREDETERMINADO;

    @Column(name = "expiracion_sesion_dias", nullable = false)
    private int expiracionSesionDias = EXPIRACION_SESION_DIAS_PREDETERMINADA;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    protected ConfiguracionGlobal() {}

    public ConfiguracionGlobal(int maxImagenMb, int expiracionSesionDias) {
        this.clave = CLAVE_GLOBAL;
        this.maxImagenMb = maxImagenMb;
        this.expiracionSesionDias = expiracionSesionDias;
    }

    public String getClave() { return clave; }
    public int getMaxImagenMb() { return maxImagenMb; }
    public int getExpiracionSesionDias() { return expiracionSesionDias; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }

    public void setMaxImagenMb(int value) { maxImagenMb = value; }
    public void setExpiracionSesionDias(int value) { expiracionSesionDias = value; }
}
