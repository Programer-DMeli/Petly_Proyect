package com.petly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una cuenta (US-21).
 * Requeridos: correo y contraseña. El usuario es opcional para los clientes de registro con correo; el resto también es opcional.
 * El registro p�blico siempre crea cuentas ADOPTANTE; las cuentas
 * ALBERGUE/ADMIN se crean por procedimiento, nunca desde aqu�.
 */
public class RegistroRequest {

    @Size(min = 3, max = 50, message = "el usuario debe tener entre 3 y 50 caracteres")
    private String username;

    @Email(message = "correo inválido")
    @NotBlank(message = "correo requerido")
    @jakarta.validation.constraints.Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "correo inválido")
    private String correo;

    @NotBlank(message = "contraseña requerida")
    @Size(min = 8, max = 72, message = "la contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    @Size(max = 50, message = "el nombre debe tener hasta 50 caracteres")
    private String firstName;

    private Boolean aceptoTerminos = false;

    public Boolean getAceptoTerminos() { return aceptoTerminos; }

    public void setAceptoTerminos(Boolean aceptoTerminos) { this.aceptoTerminos = aceptoTerminos; }

    private String lastName;

    private String telefono;

    private String fechaNacimiento;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(String fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }
}