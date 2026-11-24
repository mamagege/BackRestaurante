package com.dows.bitacora2.restaurante.security;

import com.dows.bitacora2.restaurante.controller.error.CodigoError;
import com.dows.bitacora2.restaurante.controller.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respuestas 401 y 403 generadas por los filtros de seguridad (antes de llegar a los
 * controllers), con el mismo formato RFC 9457 que usa GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        escribir(response, ProblemDetailFactory.crear(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                "Debe iniciar sesión o enviar un token válido", request.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        escribir(response, ProblemDetailFactory.crear(HttpStatus.FORBIDDEN, CodigoError.ACCESO_DENEGADO,
                "No tiene permisos para realizar esta acción", request.getRequestURI()));
    }

    private void escribir(HttpServletResponse response, ProblemDetail problema) throws IOException {
        response.setStatus(problema.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problema);
    }
}
