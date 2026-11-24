package com.dows.bitacora2.restaurante.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = new JwtUtil(Base64.getEncoder().encodeToString(new byte[32]), 60_000);
    private final UsuarioDetailsService detailsService = mock(UsuarioDetailsService.class);
    private final JwtAuthFilter filtro = new JwtAuthFilter(jwtUtil, detailsService);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain ejecutar(String authorization) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/menu");
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filtro.doFilter(req, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    @DisplayName("con token válido autentica al usuario con sus roles de la base de datos")
    void tokenValido_autentica() throws Exception {
        when(detailsService.loadUserByUsername("mesero@restaurante.com")).thenReturn(
                User.withUsername("mesero@restaurante.com").password("x").roles("MESERO").build());

        MockFilterChain chain = ejecutar("Bearer " + jwtUtil.generateToken("mesero@restaurante.com", "ROLE_MESERO"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("mesero@restaurante.com", auth.getName());
        assertNotNull(chain.getRequest(), "la petición debe continuar por la cadena");
    }

    @Test
    @DisplayName("sin header Authorization la petición continúa sin autenticar")
    void sinToken_continuaSinAutenticar() throws Exception {
        MockFilterChain chain = ejecutar(null);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("un token inválido no corta la petición: decide SecurityConfig")
    void tokenInvalido_continuaSinAutenticar() throws Exception {
        MockFilterChain chain = ejecutar("Bearer abc.def.ghi");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        verifyNoInteractions(detailsService);
    }

    @Test
    @DisplayName("un token de un usuario eliminado continúa sin autenticar")
    void usuarioEliminado_continuaSinAutenticar() throws Exception {
        when(detailsService.loadUserByUsername("borrado@restaurante.com"))
                .thenThrow(new UsernameNotFoundException("no existe"));

        MockFilterChain chain = ejecutar("Bearer " + jwtUtil.generateToken("borrado@restaurante.com", "ROLE_ADMIN"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }
}
