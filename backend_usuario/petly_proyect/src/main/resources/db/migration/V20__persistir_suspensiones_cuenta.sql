-- US-74: conserva la decisión administrativa y su vigencia en la identidad Spring.
-- Las cuentas existentes quedan activas; el bloqueo por intentos fallidos
-- continúa separado en bloqueado_hasta.
ALTER TABLE usuarios
    ADD COLUMN cuenta_suspendida BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN motivo_suspension VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN suspendida_en DATETIME(6) NULL,
    ADD COLUMN suspension_hasta DATETIME(6) NULL,
    ADD COLUMN suspendida_por_id BIGINT NULL;
