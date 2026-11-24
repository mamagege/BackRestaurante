package com.dows.bitacora2.restaurante.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequestDTO(
        @NotBlank(message = "El email no puede estar vacío")
        @Email(message = "Debe ser un email válido")
        @Size(max = 120, message = "El email admite máximo 120 caracteres")
        String email,

        @NotBlank(message = "El password no puede estar vacío")
        @Size(min = 8, max = 72, message = "El password debe tener entre 8 y 72 caracteres")
        String password
) {
}
