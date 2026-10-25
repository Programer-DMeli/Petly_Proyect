package com.petly.kyc;

/** Payload mínimo enviado al proveedor autorizado; nunca incluye las imágenes del DNI. */
public record ReniecRequest(String dni, String nombres, String apellidos) {}
