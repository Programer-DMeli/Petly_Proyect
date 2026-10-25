package com.petly.kyc;

/** Respuesta normalizada del adaptador configurado para el proveedor RENIEC autorizado. */
public record ReniecResponse(Boolean coincide, String referencia) {}
