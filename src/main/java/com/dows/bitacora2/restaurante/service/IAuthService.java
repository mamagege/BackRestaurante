package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.model.dto.auth.LoginRequestDTO;

public interface IAuthService {
    String autenticarYGenerarToken(LoginRequestDTO dto);

    /** Crea un usuario con rol CLIENTE y devuelve su JWT. Email duplicado: ConflictoException (409). */
    String registrarCliente(String email, String password);
}
