package com.dows.bitacora2.restaurante.model.dto.auth;

/** Usuario autenticado: email y rol sin el prefijo ROLE_ (ADMIN, MESERO, COCINA, CLIENTE). */
public record PerfilResponseDTO(String email, String rol) {
}
