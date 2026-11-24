package com.dows.bitacora2.restaurante.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRETO_VALIDO = Base64.getEncoder().encodeToString(new byte[48]);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("sin JWT_SECRET la aplicación no arranca")
    void constructor_sinSecreto_lanzaExcepcion(String secreto) {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtUtil(secreto, 1000));
        assertTrue(ex.getMessage().contains("JWT_SECRET"));
    }

    @Test
    @DisplayName("un secreto menor a 256 bits se rechaza")
    void constructor_secretoCorto_lanzaExcepcion() {
        String corto = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new JwtUtil(corto, 1000));
        assertTrue(ex.getMessage().contains("256 bits"));
    }

    @Test
    @DisplayName("un secreto que no es Base64 se rechaza")
    void constructor_secretoNoBase64_lanzaExcepcion() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("esto-no-es-base64!!", 1000));
    }

    @Test
    @DisplayName("un token generado es válido y conserva email y rol")
    void generarToken_esValidoYConservaClaims() {
        JwtUtil jwt = new JwtUtil(SECRETO_VALIDO, 60_000);
        String token = jwt.generateToken("mesero@restaurante.com", "ROLE_MESERO");

        assertTrue(jwt.isValid(token));
        assertEquals("mesero@restaurante.com", jwt.extractClaims(token).getSubject());
        assertEquals("ROLE_MESERO", jwt.extractClaims(token).get("rol"));
    }

    @Test
    @DisplayName("un token firmado con otro secreto es inválido")
    void isValid_tokenDeOtroSecreto_esFalso() {
        String otroSecreto = Base64.getEncoder().encodeToString("otro-secreto-de-mas-de-treinta-y-dos-bytes".getBytes());
        String tokenAjeno = new JwtUtil(otroSecreto, 60_000).generateToken("a@b.com", "ROLE_ADMIN");

        assertFalse(new JwtUtil(SECRETO_VALIDO, 60_000).isValid(tokenAjeno));
    }

    @Test
    @DisplayName("un token expirado es inválido")
    void isValid_tokenExpirado_esFalso() {
        JwtUtil jwt = new JwtUtil(SECRETO_VALIDO, -1_000);
        assertFalse(jwt.isValid(jwt.generateToken("a@b.com", "ROLE_ADMIN")));
    }
}
