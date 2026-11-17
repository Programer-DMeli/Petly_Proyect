package com.petly.usuarios.dto;

import com.petly.usuarios.Usuario;
import jakarta.validation.constraints.NotNull;

public record ActualizarRolRequest(@NotNull Usuario.Rol rol) {}
