package com.dows.bitacora2.restaurante.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsuarioActualTest {

    private final UsuarioActual usuarioActual = new UsuarioActual();

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usuarioAutenticado_devuelveSuEmail() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "mesero@restaurante.com", null, AuthorityUtils.createAuthorityList("ROLE_MESERO")));
        assertEquals("mesero@restaurante.com", usuarioActual.obtener());
    }

    @Test
    void sinAutenticacion_devuelveSistema() {
        assertEquals(UsuarioActual.SISTEMA, usuarioActual.obtener());
    }

    @Test
    void anonimo_devuelveSistema() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(UsuarioActual.SISTEMA, usuarioActual.obtener());
    }
}
