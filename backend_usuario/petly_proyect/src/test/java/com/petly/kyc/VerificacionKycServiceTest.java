package com.petly.kyc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VerificacionKycServiceTest {
    @Test
    void trataLasDecisionesAutomaticasYManualesComoEstadosFinales() {
        assertTrue(VerificacionKycService.estadoTerminal("VERIFICADO"));
        assertTrue(VerificacionKycService.estadoTerminal("APROBADO_MANUAL"));
        assertTrue(VerificacionKycService.estadoTerminal("RECHAZADO_MANUAL"));
    }

    @Test
    void permiteContinuarSoloLosEstadosPendientes() {
        assertFalse(VerificacionKycService.estadoTerminal("PENDIENTE"));
        assertFalse(VerificacionKycService.estadoTerminal("PENDIENTE_REVISION"));
        org.junit.jupiter.api.Assertions.assertTrue(VerificacionKycService.estadoPendienteRevision("PENDIENTE_REVISION"));
        org.junit.jupiter.api.Assertions.assertFalse(VerificacionKycService.estadoPendienteRevision("PENDIENTE"));
    }
}
