package com.dows.bitacora2.restaurante.controller;

import com.dows.bitacora2.restaurante.exception.BloqueoTemporalException;
import com.dows.bitacora2.restaurante.security.JwtAuthFilter;
import com.dows.bitacora2.restaurante.security.JwtUtil;
import com.dows.bitacora2.restaurante.service.IAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String LOGIN = "{\"email\":\"admin@restaurante.com\",\"password\":\"incorrecta\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IAuthService authService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    @DisplayName("login exitoso devuelve 200 con el token")
    void login_credencialesValidas_devuelve200() throws Exception {
        when(authService.autenticarYGenerarToken(any())).thenReturn("token-jwt");

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-jwt"));
    }

    @Test
    @DisplayName("credenciales incorrectas devuelven 401 y no 500")
    void login_credencialesInvalidas_devuelve401() throws Exception {
        when(authService.autenticarYGenerarToken(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"));
    }

    @Test
    @DisplayName("cualquier otra falla de autenticación también es 401 con mensaje genérico")
    void login_cuentaDeshabilitada_devuelve401() throws Exception {
        when(authService.autenticarYGenerarToken(any())).thenThrow(new DisabledException("disabled"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("usuario bloqueado por intentos fallidos devuelve 429")
    void login_usuarioBloqueado_devuelve429() throws Exception {
        when(authService.autenticarYGenerarToken(any())).thenThrow(new BloqueoTemporalException("bloqueado"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
    }

    @Test
    @DisplayName("registro exitoso devuelve 201 con el token")
    void registro_valido_devuelve201() throws Exception {
        when(authService.registrarCliente("nuevo@correo.com", "secreto123")).thenReturn("jwt-cliente");

        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo@correo.com\",\"password\":\"secreto123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-cliente"));
    }

    @Test
    @DisplayName("registro con password corto devuelve 400 con el campo inválido")
    void registro_passwordCorto_devuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo@correo.com\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("password"));
    }
}
