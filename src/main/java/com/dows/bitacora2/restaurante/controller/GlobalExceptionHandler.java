package com.dows.bitacora2.restaurante.controller;

import com.dows.bitacora2.restaurante.controller.error.CodigoError;
import com.dows.bitacora2.restaurante.controller.error.ProblemDetailFactory;
import com.dows.bitacora2.restaurante.controller.error.ProblemDetailFactory.ErrorDeCampo;
import com.dows.bitacora2.restaurante.exception.BloqueoTemporalException;
import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.exception.EstadoInvalidoException;
import com.dows.bitacora2.restaurante.exception.RecursoNoEncontradoException;
import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Traduce cualquier excepción a una respuesta RFC 9457 ({@code application/problem+json}).
 * <ul>
 *   <li>Excepciones del framework (JSON malformado, parámetros faltantes, rutas inexistentes…):
 *       las resuelve {@link ResponseEntityExceptionHandler} con su código 4xx correcto y aquí
 *       solo se reescribe el mensaje en español.</li>
 *   <li>Excepciones de dominio: 404, 409, 422 y 429 según su semántica.</li>
 *   <li>Lo no previsto: 500 con mensaje genérico; el detalle solo va al log.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ── Dominio ──────────────────────────────────────────────────────────────

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> handleNoEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        log.debug("Recurso no encontrado: {}", ex.getMessage());
        return responder(HttpStatus.NOT_FOUND, CodigoError.RECURSO_NO_ENCONTRADO, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ProblemDetail> handleConflicto(ConflictoException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, CodigoError.CONFLICTO, ex.getMessage(), req);
    }

    @ExceptionHandler({EstadoInvalidoException.class, ReglaDeNegocioException.class})
    public ResponseEntity<ProblemDetail> handleReglaDeNegocio(RuntimeException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.REGLA_DE_NEGOCIO, ex.getMessage(), req);
    }

    @ExceptionHandler(BloqueoTemporalException.class)
    public ResponseEntity<ProblemDetail> handleBloqueoTemporal(BloqueoTemporalException ex, HttpServletRequest req) {
        log.warn("Bloqueo temporal: {}", ex.getMessage());
        return responder(HttpStatus.TOO_MANY_REQUESTS, CodigoError.DEMASIADOS_INTENTOS, ex.getMessage(), req);
    }

    // ── Seguridad ────────────────────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAutenticacion(AuthenticationException ex, HttpServletRequest req) {
        // Mensaje genérico a propósito: no revelar si el email existe o si falló la contraseña
        log.warn("Autenticación fallida en {}: {}", req.getRequestURI(), ex.getClass().getSimpleName());
        return responder(HttpStatus.UNAUTHORIZED, CodigoError.CREDENCIALES_INVALIDAS, "Credenciales inválidas", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Acceso denegado (403) a {}", req.getRequestURI());
        return responder(HttpStatus.FORBIDDEN, CodigoError.ACCESO_DENEGADO,
                "No tiene permisos para realizar esta acción", req);
    }

    // ── Persistencia ─────────────────────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, CodigoError.CONFLICTO,
                "La operación entra en conflicto con datos existentes (registro duplicado o referenciado)", req);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrencia(ObjectOptimisticLockingFailureException ex,
                                                            HttpServletRequest req) {
        log.warn("Conflicto de concurrencia en {}: {}", req.getRequestURI(), ex.getMessage());
        return responder(HttpStatus.CONFLICT, CodigoError.CONFLICTO,
                "El recurso fue modificado por otra operación. Consúltelo de nuevo e intente otra vez", req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest req) {
        List<ErrorDeCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorDeCampo(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ProblemDetailFactory.validacion(HttpStatus.BAD_REQUEST, errores, req.getRequestURI()));
    }

    // ── Último recurso ───────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenerico(Exception ex, HttpServletRequest req) {
        log.error("Error inesperado en {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                "Error inesperado del servidor", req);
    }

    // ── Excepciones del framework (4xx con mensaje en español) ───────────────

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorDeCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorDeCampo(e.getField(), e.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex, ProblemDetailFactory.validacion(status, errores, ruta(request)),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problema(ex, headers, status, request, CodigoError.JSON_INVALIDO,
                "El cuerpo de la petición no es un JSON válido o tiene valores con tipo incorrecto");
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problema(ex, headers, status, request, CodigoError.PARAMETRO_FALTANTE,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'");
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String nombre = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return problema(ex, headers, status, request, CodigoError.TIPO_INVALIDO,
                "El valor '" + ex.getValue() + "' no es válido para '" + nombre + "'");
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problema(ex, headers, status, request, CodigoError.RUTA_NO_ENCONTRADA,
                "La ruta " + ruta(request) + " no existe");
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problema(ex, headers, status, request, CodigoError.METODO_NO_PERMITIDO,
                "El método " + ex.getMethod() + " no está permitido en esta ruta");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problema(ex, headers, status, request, CodigoError.TIPO_DE_CONTENIDO_NO_SOPORTADO,
                "El tipo de contenido '" + ex.getContentType() + "' no está soportado; use application/json");
    }

    /** Cualquier otra excepción del framework conserva su código 4xx y recibe código/timestamp/instancia. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problema) {
            ProblemDetailFactory.completar(problema, CodigoError.ERROR_HTTP, ruta(request));
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private ResponseEntity<Object> problema(Exception ex, HttpHeaders headers, HttpStatusCode status,
                                            WebRequest request, CodigoError codigo, String detalle) {
        return handleExceptionInternal(ex, ProblemDetailFactory.crear(status, codigo, detalle, ruta(request)),
                headers, status, request);
    }

    private static ResponseEntity<ProblemDetail> responder(HttpStatus status, CodigoError codigo, String detalle,
                                                           HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ProblemDetailFactory.crear(status, codigo, detalle, req.getRequestURI()));
    }

    private static String ruta(WebRequest request) {
        return request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : null;
    }
}
