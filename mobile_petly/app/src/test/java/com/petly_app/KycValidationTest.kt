package com.petly_app

import com.petly_app.data.model.dniValido
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KycValidationTest {
    @Test fun aceptaSoloOchoDigitosAscii() {
        assertTrue(dniValido("12345678"))
        assertFalse(dniValido("1234567"))
        assertFalse(dniValido("123456789"))
        assertFalse(dniValido("1234A678"))
        assertFalse(dniValido("١٢٣٤٥٦٧٨"))
    }
}
