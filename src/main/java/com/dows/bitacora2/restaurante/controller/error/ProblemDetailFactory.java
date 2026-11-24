package com.dows.bitacora2.restaurante.controller.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Construye las respuestas de error de la API con el formato estándar RFC 9457
 * ({@code application/problem+json}). Todas las respuestas de error, incluidas las de
 * seguridad (401/403), pasan por aquí para que el contrato sea siempre el mismo:
 * <pre>
 * { "type": "about:blank", "title": "...", "status": 404, "detail": "...",
 *   "instance": "/api/v1/...", "codigo": "RECURSO_NO_ENCONTRADO", "timestamp": "...",
 *   "errores": [ { "campo": "...", "mensaje": "..." } ]   // solo en VALIDACION
 * }
 * </pre>
 */
public final class ProblemDetailFactory {

    public static final String CODIGO = "codigo";
    public static final String TIMESTAMP = "timestamp";
    public static final String ERRORES = "errores";

    public record ErrorDeCampo(String campo, String mensaje) {}

    private ProblemDetailFactory() {}

    public static ProblemDetail crear(HttpStatusCode status, CodigoError codigo, String detalle, String ruta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        completar(problema, codigo, ruta);
        return problema;
    }

    public static ProblemDetail validacion(HttpStatusCode status, List<ErrorDeCampo> errores, String ruta) {
        ProblemDetail problema = crear(status, CodigoError.VALIDACION, "La petición tiene campos inválidos", ruta);
        problema.setProperty(ERRORES, errores);
        return problema;
    }

    /** Agrega título, código, instancia y timestamp a un ProblemDetail existente si no los tiene. */
    public static void completar(ProblemDetail problema, CodigoError codigo, String ruta) {
        if (problema.getProperties() == null || !problema.getProperties().containsKey(CODIGO)) {
            problema.setTitle(codigo.getTitulo());
            problema.setProperty(CODIGO, codigo.name());
        }
        if (problema.getInstance() == null && ruta != null) {
            try {
                problema.setInstance(URI.create(ruta));
            } catch (IllegalArgumentException ignorada) {
                // ruta con caracteres no válidos para una URI: se omite la instancia
            }
        }
        problema.setProperty(TIMESTAMP, Instant.now());
    }
}
