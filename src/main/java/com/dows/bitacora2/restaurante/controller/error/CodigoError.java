package com.dows.bitacora2.restaurante.controller.error;

/**
 * Códigos de error estables que viajan en la propiedad {@code codigo} de cada respuesta
 * de error. El front debe decidir por este código, no por el texto de {@code detail}.
 */
public enum CodigoError {
    VALIDACION("Datos de entrada inválidos"),
    JSON_INVALIDO("Cuerpo de la petición inválido"),
    PARAMETRO_FALTANTE("Falta un parámetro obligatorio"),
    TIPO_INVALIDO("Parámetro con formato inválido"),
    NO_AUTENTICADO("Autenticación requerida"),
    CREDENCIALES_INVALIDAS("Credenciales inválidas"),
    ACCESO_DENEGADO("Acceso denegado"),
    RECURSO_NO_ENCONTRADO("Recurso no encontrado"),
    RUTA_NO_ENCONTRADA("Ruta no encontrada"),
    METODO_NO_PERMITIDO("Método HTTP no permitido"),
    TIPO_DE_CONTENIDO_NO_SOPORTADO("Tipo de contenido no soportado"),
    CONFLICTO("Conflicto con el estado actual del recurso"),
    REGLA_DE_NEGOCIO("Regla de negocio incumplida"),
    DEMASIADOS_INTENTOS("Demasiados intentos"),
    ERROR_HTTP("Error en la petición"),
    ERROR_INTERNO("Error interno del servidor");

    private final String titulo;

    CodigoError(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }
}
