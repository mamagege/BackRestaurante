# Contrato de errores de la API

Toda respuesta de error usa el estándar **RFC 9457** (`Content-Type: application/problem+json`), tanto si el error ocurre en un controller como en la capa de seguridad.

```json
{
  "type": "about:blank",
  "title": "Regla de negocio incumplida",
  "status": 422,
  "detail": "RN-02: Una pizza no puede llevar más de 5 toppings.",
  "instance": "/api/v1/pedidos",
  "codigo": "REGLA_DE_NEGOCIO",
  "timestamp": "2026-11-24T18:05:12.345Z"
}
```

| Campo | Uso |
|---|---|
| `status` | Código HTTP, repetido en el cuerpo. |
| `codigo` | **Identificador estable del error.** El front debe decidir por este campo. |
| `title` | Descripción corta del tipo de error. |
| `detail` | Mensaje en español apto para mostrar al usuario. Puede cambiar de redacción. |
| `instance` | Ruta que produjo el error. |
| `timestamp` | Momento del error en UTC (ISO-8601). |
| `errores` | Solo en `VALIDACION`: lista `[{ "campo", "mensaje" }]` con cada campo inválido. |

## Códigos

| HTTP | `codigo` | Cuándo ocurre |
|---|---|---|
| 400 | `VALIDACION` | Un campo del body no cumple Bean Validation (`@NotBlank`, `@Positive`, …). Incluye `errores`. |
| 400 | `JSON_INVALIDO` | El body no es JSON válido o un valor tiene un tipo incorrecto. |
| 400 | `PARAMETRO_FALTANTE` | Falta un parámetro obligatorio de la URL (por ejemplo `?estado=`). |
| 400 | `TIPO_INVALIDO` | Un parámetro tiene formato incorrecto (por ejemplo `/mesas/abc`). |
| 401 | `NO_AUTENTICADO` | Ruta protegida sin token, o con token inválido o expirado. |
| 401 | `CREDENCIALES_INVALIDAS` | Login con email o contraseña incorrectos. |
| 403 | `ACCESO_DENEGADO` | El rol del usuario no tiene permiso para la operación. |
| 404 | `RECURSO_NO_ENCONTRADO` | El recurso pedido no existe (mesa, plato, pedido, cuenta). |
| 404 | `RUTA_NO_ENCONTRADA` | La URL no corresponde a ningún endpoint. |
| 405 | `METODO_NO_PERMITIDO` | Método HTTP no soportado en esa ruta (incluye la cabecera `Allow`). |
| 409 | `CONFLICTO` | Choque con el estado actual: nombre o número duplicado, mesa con cuenta ya abierta, modificación concurrente. |
| 415 | `TIPO_DE_CONTENIDO_NO_SOPORTADO` | El body no se envió como `application/json`. |
| 422 | `REGLA_DE_NEGOCIO` | La petición es válida, pero viola una regla del negocio (RN-01 a RN-07, transición de estado no permitida). |
| 429 | `DEMASIADOS_INTENTOS` | Login bloqueado temporalmente por intentos fallidos. |
| 500 | `ERROR_INTERNO` | Error no previsto. El detalle técnico solo queda en el log del servidor. |

## Ejemplo de validación

```json
{
  "title": "Datos de entrada inválidos",
  "status": 400,
  "detail": "La petición tiene campos inválidos",
  "instance": "/api/v1/platos",
  "codigo": "VALIDACION",
  "timestamp": "2026-11-24T18:05:12.345Z",
  "errores": [
    { "campo": "nombre", "mensaje": "El nombre es obligatorio" },
    { "campo": "precio", "mensaje": "El precio debe ser mayor a 0" }
  ]
}
```

## Implementación

- `controller/GlobalExceptionHandler`: extiende `ResponseEntityExceptionHandler` y traduce las excepciones de dominio y del framework.
- `controller/error/ProblemDetailFactory` y `CodigoError`: construyen el cuerpo de la respuesta y definen el catálogo de códigos.
- `security/ProblemDetailSecurityHandler`: genera el 401 y el 403 que se producen en los filtros, antes de llegar a los controllers.
- Para agregar un nuevo tipo de error: crear la excepción en `exception/`, añadir su `@ExceptionHandler` y, si hace falta, un valor nuevo en `CodigoError`. Después cubrirlo en `GlobalExceptionHandlerTest`.
