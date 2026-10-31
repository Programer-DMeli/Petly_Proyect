package com.petly.kyc;

/** Datos devueltos para completar el formulario; no implican verificación de identidad. */
public record ApiPeruDniResult(String dni, String nombres, String apellidos, String proveedor) {}
