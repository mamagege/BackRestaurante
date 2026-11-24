package com.dows.bitacora2.restaurante.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Identifica al usuario autenticado de la petición en curso (para auditoría). */
@Component
public class UsuarioActual {

    public static final String SISTEMA = "sistema";

    public String obtener() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return SISTEMA;
        }
        return auth.getName();
    }
}
