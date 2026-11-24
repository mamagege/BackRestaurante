# SECCIÓN CONTROLLER Y VERIFICACIÓN DE CALIDAD

## Flujo de una petición — De la red al dominio y de vuelta

Antes de escribir una línea de código, es vital tener claro quién le habla a quién y qué objeto pasa por cada frontera. Este es el mapa de la arquitectura:

1. **Cliente (Postman / React)** envía un JSON Request.
2. El **Controller (`@RestController`)** intercepta el JSON y, con ayuda de Spring, lo valida usando `@Valid`.
3. El JSON validado se convierte en un **`RequestDTO`**.
4. El Controller utiliza un **MapperIn** para traducir ese `RequestDTO` en un **Objeto de Dominio** puro.
5. El Controller delega ese Objeto de Dominio al **Service (`IService` -> `ServiceImpl`)**.
6. El Service aplica la lógica de negocio (y excepciones tipadas) operando sobre los objetos de negocio, e interactúa con el repositorio si es necesario.
7. El Service finaliza su operación y devuelve **otro Objeto de Dominio** al Controller.
8. El Controller usa un **MapperOut** para convertir ese Objeto de Dominio en un **`ResponseDTO`**.
9. Spring convierte el `ResponseDTO` a un JSON Response y se lo entrega al Cliente.

---

## 🔑 Las 4 reglas que no se negocian

1. ✅ **El Mapper vive en el Controller — no en el Service.** El Service recibe y devuelve objetos de dominio puros, nunca DTOs.
2. ✅ **El Service devuelve objetos de dominio — nunca un ResponseDTO.** La conversión de salida (DTO) es estrictamente trabajo de la capa de presentación (Controller) justo antes de responder.
3. ✅ **El Controller no tiene lógica de negocio —** solo recibe, traduce, delega y responde.
4. ✅ **Segregación de Validaciones:** Las validaciones sintácticas o de formato (input) van en el DTO usando *Bean Validation* (`@NotBlank`, `@Positive`). Las validaciones semánticas o de negocio ("¿El plato ya existe?") van en el Service y lanzan excepciones propias.

> ⚠️ **¿Por qué el Mapper NO va en el Service?**
> Si un Service retorna un `ResponseDTO`, queda acoplado irreversiblemente a la capa web. El día que quieras reaprovechar esa misma lógica de negocio para mandar un PDF, procesar un mensaje de Kafka o usar WebSockets, tendrás que reescribir tu Service porque te obliga a recibir/devolver formatos web. El Mapper en el Controller blinda al Service manteniéndolo agnóstico y universal.

---

## PASO 01: Las clases de dominio (`model/domain`)

El dominio es el núcleo absoluto. Todo lo demás (DTOs, Mappers, Services) orbita alrededor de las clases de esta capa. Si el dominio está mal modelado o expone comportamientos indebidos, toda la arquitectura sufrirá de "anemia de dominio" o fugas de lógica.

### Dominio Rico vs Dominio Anémico

Las clases de dominio representan los conceptos nativos del negocio y operan bajo la premisa del Java puro: sin `@Entity`, sin `@Service` y sin acoplamiento a infraestructuras externas.

En nuestro proyecto, las entidades (`Plato`, `Pedido`, `EstadoPedido`) saben gobernar su propio estado:

```java
// model/domain/Plato.java
public class Plato {
    private Boolean disponible;

    // ✅ Comportamiento de negocio propio del objeto
    // El dominio sabe qué puede hacer — sin depender de estado externo
    public boolean estaDisponible() {
        return Boolean.TRUE.equals(disponible);
    }

    // Activa/desactiva sin que el Service tenga que mutar el campo directamente
    public void activar()   { this.disponible = true;  }
    public void desactivar(){ this.disponible = false; }
}
```

### 🌿 ¿Qué SÍ va como método en el dominio?
Comportamiento que el objeto sabe ejecutar evaluando su propio estado interno, sin depender de repositorios, bases de datos o servicios externos.
Ejemplos en nuestro proyecto:
- `estaDisponible()`, `activar()` en `Plato`.
- `puedeTransicionarA()`, `esCancelable()` en el Enum `EstadoPedido`.
- `calcularSubtotal()` en `ItemPedido`.

### 🌿 ¿Qué NO va en el dominio?
- Validaciones de formato sintáctico: `public boolean esValido() { return nombre != null; }` (Eso lo hace `@NotBlank` en el RequestDTO).
- Validaciones que cruzan fronteras: `"¿El nombre del plato está duplicado?"` (El objeto `Plato` individual no puede ver a los demás platos; eso es trabajo del Service consultando al Validator/Repository).

### Estructura de paquetes recomendada y cumplida
- `model/domain/` → Clases de negocio puras + Enums. *(Paso 01)*
- `model/dto/request/` → Lo que entra crudo al API. *(Paso 02)*
- `model/dto/response/` → Lo que se escupe formateado al API. *(Paso 02)*
- `mapper/` → Conversión agnóstica entre capas. *(Paso 03)*
- `service/` → Interfaces (contratos). *(Paso 04)*
- `service/impl/` → Lógica dura (implementaciones). *(Paso 04)*
- `controller/` → Endpoints REST y ruteo HTTP. *(Paso 06)*
- `exception/` → Excepciones y traductor Global. *(Paso 07)*

---

## PASO 02: Los DTOs — Lo que entra y lo que sale (`model/dto`)

Los Data Transfer Objects (DTOs) son las verdaderas fronteras de tu sistema. El `RequestDTO` valida todo lo que el cliente te envía. El `ResponseDTO` filtra y maquilla todo lo que le devuelves al cliente.

> 💡 **Pregunta guía antes de crear un DTO:**
> ¿Qué campos son obligatorios? ¿Cuáles opcionales? ¿Qué expongo cuando respondo? La respuesta dictamina cuántos DTOs necesitas. **Nunca utilices un solo DTO global con 15 campos opcionales** para abarcar peticiones diferentes, pues destruirías la utilidad del patrón (rompiendo el principio de responsabilidad única).

### Evolución en nuestro proyecto: De clases a Records
Aunque comúnmente se enseñan como clases con `@Data` de Lombok, nuestro proyecto eleva este estándar y utiliza **Java Records**. Los records proporcionan inmutabilidad nativa sin necesidad de bibliotecas de terceros, lo que los hace perfectos para transferencia de datos:

#### 📥 RequestDTO — Lo que entra (La Barrera de Validación)
Aquí es donde se aplican las reglas sintácticas usando anotaciones *Bean Validation*:

```java
// model/dto/request/PlatoRequestDTO.java
public record PlatoRequestDTO(
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "Maximo 100 caracteres")
    String nombre,

    @NotNull(message = "El precio es obligatorio")
    @Positive(message = "El precio debe ser mayor a 0")
    Double precio,

    @NotBlank(message = "La categoria es obligatoria")
    String categoria,

    // Campo opcional — no requiere @NotNull
    String descripcion
) {}
```

#### 📤 ResponseDTO — Lo que sale (El Filtro de Salida)
```java
// model/dto/response/PlatoResponseDTO.java
public record PlatoResponseDTO(
    Long id,
    String nombre,
    Double precio,
    String categoria,
    Boolean disponible
) {}
```
*(Nota: No exponemos relaciones internas pesadas ni enviamos la clase de Dominio directa, enviamos una proyección curada).*

### ⚠️ ¿Por qué el Service no devuelve el ResponseDTO directamente?
Si tu capa de servicio devolviera un `ResponseDTO`, quedaría contaminada por la infraestructura de transporte web. Si mañana la app exige exportar un reporte en Excel o procesar mensajes de RabbitMQ, te verías forzado a modificar el *Service* (donde vive tu lógica intocable) porque quedó atado a formatos de respuesta JSON. Es el Controller quien, utilizando el Mapper, convierte el Dominio devuelto por el Service en un ResponseDTO.

### Referencia de Anotaciones Clave
| Anotación | Para qué sirve | Ejemplo Práctico |
|-----------|----------------|------------------|
| `@NotNull` | El campo no puede ser `null` (pero sí "") | Objetos complejos numéricos o fechas |
| `@NotBlank`| String con contenido real tras aplicar *trim* | Nombres, contraseñas, categorías |
| `@NotEmpty`| Colecciones que no pueden estar vacías | La lista de `ItemPedido` |
| `@Positive`| Número estrictamente mayor a 0 | Precios, capacidades, cantidades |
| `@Size` | Limita la longitud máxima o mínima | `@Size(max=100)` para un email |
| `@Future` | Fechas que deben ser posteriores a hoy | Horarios de `Reserva` |

---

## PASO 03: El Mapper — El traductor entre capas (`mapper/`)

El Mapper es el motor de conversión bidireccional. Traduce de `RequestDTO` hacia `Dominio`, y de `Dominio` hacia `ResponseDTO`. 

> 🚨 **Regla de Arquitectura Fundamental:**
> El Mapper **solo** se inyecta y utiliza en la capa del Controller. Nunca en el Service. Reafirmamos: El Service recibe y despacha objetos puros de dominio.

### MapStruct: Generación automática de código
En lugar de escribir manualmente decenas de líneas con `plato.setNombre(dto.getNombre())`, utilizamos **MapStruct**. Esta librería procesa tus interfaces en tiempo de compilación y autogenera (escribe por ti) el código Java puro necesario para las conversiones, haciéndolo extremadamente rápido en ejecución.

#### Configuración Crítica en Maven (`pom.xml`)
El orden en el plugin del compilador es un asunto de vida o muerte:
**Lombok DEBE procesarse siempre antes que MapStruct.**
¿Por qué? Porque MapStruct necesita leer los *getters* y *setters* de las clases para saber cómo mapear. Si MapStruct se ejecuta primero, se topará con clases vacías (porque Lombok aún no ha inyectado el código) y la compilación fallará silenciosamente o con errores crípticos. En nuestro `pom.xml` esto ya está garantizado.

#### La Implementación del Mapper
```java
// mapper/PlatoMapper.java
@Mapper(componentModel = "spring") 
public interface PlatoMapper {

    // ── MapperIn: RequestDTO → Dominio ──────────────────────────────────
    @Mapping(target = "id", ignore = true)             // ID se ignora en la creación
    @Mapping(target = "disponible", constant = "true") // Regla: Todo plato nuevo nace disponible
    Plato toDomain(PlatoRequestDTO dto);

    // ── MapperOut: Dominio → ResponseDTO ────────────────────────────────
    PlatoResponseDTO toResponse(Plato plato);

    // ── Lista: Mapeo masivo en una sola línea ───────────────────────────
    List<PlatoResponseDTO> toResponseList(List<Plato> platos);
}
```
*`componentModel = "spring"` permite que Spring Boot detecte esta interfaz y la inyecte automáticamente con `@Autowired` o vía constructor en el Controller.*

### Las Anotaciones `@Mapping` más comunes
| Caso de Mapeo | Anotación MapStruct | Cuándo se usa |
|---------------|---------------------|---------------|
| **Campos idénticos** | (Ninguna - es automático) | Mismo nombre y tipo entre origen y destino. |
| **Campos distintos** | `@Mapping(source="x", target="y")` | Cuando el DTO dice `fecha` pero el dominio dice `timestamp`. |
| **Ignorar campos** | `@Mapping(target="id", ignore=true)`| Al mapear un RequestDTO hacia el dominio (el ID aún no existe). |
| **Valores fijos** | `@Mapping(target="activo", constant="true")`| Reglas deterministas al crear un objeto. |
| **Campos anidados** | `@Mapping(source="mesa.numero", target="numMesa")`| Para aplanar el ResponseDTO y ocultar jerarquías. |

### 🌿 El Secreto de MapStruct (`target/generated-sources`)
Si alguna vez dudas de cómo MapStruct está realizando la conversión, abre tu carpeta `target/generated-sources/annotations/.../mapper/PlatoMapperImpl.java`. Descubrirás que MapStruct simplemente escribió un bloque gigante de código Java tradicional (`new Plato()`, `setNombre()`, *null-checks*, `for` para listas). Leer esa clase generada es el mejor recurso para depurar mapeos rebeldes.

---

## PASO 04: El Service — Donde vive la verdadera lógica (`service/`)

La capa de Servicio es el cerebro de la aplicación. Todo Controller delega su carga al Service. Si algo debe calcularse, verificarse o decidirse, se hace aquí.

> 🚨 **Regla de Oro del Servicio:**
> **Sin DTOs, sin Spring MVC, sin peticiones HTTP.** La Interfaz del Service (`IPlatoService`) y su implementación (`PlatoServiceImpl`) **únicamente** reciben Objetos de Dominio y devuelven Objetos de Dominio. El servicio es ciego a la red.

### Interfaz e Implementación
La segregación mediante interfaces (`IPlatoService`) no es opcional. Es lo que permite que un Controller interactúe con el negocio sin saber qué base de datos hay por detrás (y facilita inmensamente la creación de *Mocks* para pruebas unitarias).

#### Evolución Híbrida en nuestro proyecto
Aunque clásicamente al iniciar un proyecto la persistencia se simula en un mapa en memoria (`Map<Long, Plato> platos = new ConcurrentHashMap<>()`), nuestro proyecto ya implementó una persistencia profesional usando **Spring Data JPA** (`PlatoRepository`).

Aun así, **la lógica con Streams sigue siendo el núcleo del servicio** para operaciones complejas donde la base de datos entregó listas gigantes que requieren cruzarse, filtrarse o tabularse (como ya hicimos en el `ResumenDelDia` usando `flatMap` y `groupingBy`).

### Referencia Rápida — El Stream correcto para cada operación
Cuando recibes colecciones o necesitas hacer cálculos sofisticados en memoria, el API de *Streams* es insuperable:

| Qué necesitas hacer en el Service | Operación Stream en Java | Resultado |
|-----------------------------------|--------------------------|-----------|
| **Obtener todos como lista** | `.stream().toList()` | `List<T>` |
| **Filtrar por condición** | `.stream().filter(p -> p.activo()).toList()` | `List<T>` |
| **Buscar uno específico** | `.stream().filter(...).findFirst().orElseThrow(...)` | `T` o Excepción |
| **Verificar si existe (Short-circuit)** | `.stream().anyMatch(...)` | `boolean` |
| **Ordenar** | `.stream().sorted(Comparator.comparing(...)).toList()`| `List<T>` |
| **Aplanar listas anidadas** | `.stream().flatMap(x -> x.getItems().stream()).toList()` | `List<Item>` |
| **Sumar un campo numérico** | `.stream().mapToDouble(Item::getPrecio).sum()` | `double` |
| **Agrupar por llave (Diccionario)** | `.stream().collect(Collectors.groupingBy(...))` | `Map<K, List<T>>` |
| **Contar elementos** | `.stream().filter(...).count()` | `long` |
| **Transformar a otro tipo** | `.stream().map(Item::toDomain).toList()` | `List<R>` |

### ⚠️ Un Service puede depender de otro Service
Uno de los hitos de nuestra arquitectura es la **reutilización**.
Si `PedidoServiceImpl` necesita saber si un plato existe y está disponible antes de agregarlo al carrito, **jamás** inyecta a `PlatoRepository` para buscarlo manualmente, ni duplica la lógica de negocio. Lo que hace es inyectar la interfaz `IPlatoService` e invocar el método `buscarPorId()`.
> **Cuidado:** Siempre inyecta la interfaz (`IPlatoService`), nunca la implementación concreta (`PlatoServiceImpl`), y ten cuidado con las **dependencias circulares** (Si el Plato llama al Pedido, y el Pedido llama al Plato, Spring Boot crasheará en el arranque).

---

## PASO 05: Validaciones (Input vs Negocio) y Utilidades (`validator/` y `util/`)

Mezclar los dos tipos de validación es el error arquitectónico más común. Existen dos fronteras claramente marcadas.

### 1. Validación de INPUT (Sintáctica)
Responde a preguntas como: *¿El formato es correcto? ¿El campo está nulo? ¿El número es negativo?*
- **Dónde vive:** Exclusivamente en los DTOs (`@NotBlank`, `@Positive`).
- **Cómo se activa:** Añadiendo la anotación `@Valid` justo al lado de `@RequestBody` en los parámetros del **Controller**.
- **Qué lanza:** Un `400 Bad Request` automático (manejado por nuestro Gerente `GlobalExceptionHandler`).

### 2. Validación de NEGOCIO (Semántica)
Responde a preguntas como: *¿El nombre del plato ya existe en la carta? ¿La mesa tiene pedidos sin pagar?*
- **Dónde vive:** En la capa Service, o delegada a un componente dedicado llamado **Validator**.
- **Cómo se activa:** Por código explícito manual dentro del Service.
- **Qué lanza:** Excepciones de negocio personalizadas como `ConflictoException` (`409`) o `ReglaDeNegocioException` (`422`).

#### ¿Un Validator por Dominio?
Para no ensuciar el código del Service con decenas de `if-else` kilométricos, extraemos esa lógica a componentes que aplican el SRP (Principio de Responsabilidad Única).
```java
// validator/IPlatoValidator.java
public interface IPlatoValidator {
    void validarNombreUnico(String nombre, Collection<Plato> existentes);
}
```
*Si la validación solo afecta a Plato, va en PlatoValidator. Si afecta a dos dominios, extráelo.*

---

## PASO 05-B: Utilidades Compartidas (`util/`)

A lo largo de tu proyecto notarás operaciones algorítmicas que se repiten: limpiar mayúsculas/minúsculas, formatear fechas de clientes o redondear los cálculos de las propinas y el cobro. 
**No dupliques esa lógica.**

Hemos implementado el paquete `util` que guarda clases de lógica pura que no le pertenecen a ningún dominio en concreto.
- **`CalculoUtils.java`:** Para redondear decimales (`redondear()`) o aplicar porcentajes de descuentos.
- **`TextoUtils.java`:** Para purificar nombres (`normalizarNombre()`) y comparar cadenas de texto.

> ⚠️ **¿Cuándo es una Utility y cuándo es un Validator?**
> Si la lógica analiza un requerimiento del negocio y **hace explotar** el flujo lanzando una Excepción (ej. "*Toppings excedidos*"), es un **Validator**. 
> Si la lógica simplemente mastica datos para transformarlos, limpiarlos o calcularlos sin lanzar excepciones (ej. "*pasar a mayúsculas*"), es una **Utility**.

### Mapa de Dependencias — ¿Quién usa a quién?
La cadena de responsabilidades fluye de la siguiente manera:
1. **Controller:** Delega al Service mediante el **Mapper**.
2. **Mapper:** Traduce sin lógica de negocio.
3. **Service:** Orquesta todo. Opera sobre los objetos del Dominio, se auxilia con **Utils** estáticos para cálculos matemáticos, y delega chequeos pesados al **Validator**.
4. **Dominio:** Los objetos de negocio protegen y gobiernan su propio estado interno.

---

## PASO 06: El Controller — Recibe, traduce y delega (`controller/`)

El Controller es la aduana de nuestro sistema. Su trabajo no es pensar, es despachar. Si tienes condicionales de negocio (`if/else`) en un Controller, estás rompiendo la arquitectura.

> 🎮 **La Regla del Controller Tonto:**
> 1. Recibe la petición HTTP y valida el formato (`@Valid`).
> 2. Llama al `MapperIn` para traducir el DTO a un Objeto de Dominio.
> 3. Delega el Objeto de Dominio al `Service`.
> 4. Recibe la respuesta (Dominio) del Service.
> 5. Llama al `MapperOut` para traducirlo a un DTO de respuesta.
> 6. Responde con el código de estado HTTP adecuado.

### Implementación Real (`PlatoController.java`)
Observemos cómo se implementa este flujo a la perfección en nuestro proyecto:

```java
@RestController
@RequestMapping("/api/v1/platos")
@RequiredArgsConstructor
@Slf4j
public class PlatoController implements PlatoApi {

    // ✅ El Mapper se inyecta AQUÍ — jamás en el Service
    private final IPlatoService platoService;
    private final PlatoMapper   platoMapper;

    // ── POST /api/v1/platos ─────────────────────────────────────
    @Override @PostMapping
    public ResponseEntity<PlatoResponseDTO> crear(@RequestBody @Valid PlatoRequestDTO dto) {
        log.info("POST /api/v1/platos - nombre={}", dto.nombre());

        // 1. MapperIn: RequestDTO → dominio
        Plato plato = platoMapper.toDomain(dto);

        // 2. Service recibe dominio, devuelve dominio
        Plato creado = platoService.crear(plato);

        // 3. MapperOut: dominio → ResponseDTO
        return ResponseEntity
            .status(HttpStatus.CREATED)  // ← 201 Created para creaciones exitosas
            .body(platoMapper.toResponse(creado));
    }

    // ── DELETE /api/v1/platos/{id} ──────────────────────────────
    @Override @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        platoService.eliminar(id);
        return ResponseEntity.noContent().build();  // ← 204 No Content, sin cuerpo
    }
}
```

### Códigos de Estado HTTP Correctos
Una API profesional se comunica mediante los códigos semánticos de HTTP, no devolviendo un JSON con `{ "status": "error" }` sobre un estatus `200 OK`. 

- `200 OK`: Para respuestas exitosas de lectura (`GET`) o modificaciones generales (`PUT`, `PATCH`).
- `201 CREATED`: Estrictamente para recursos creados con éxito tras un `POST`.
- `204 NO CONTENT`: Para borrados (`DELETE`) donde la operación fue exitosa pero no hay datos que devolver.
- `400 BAD REQUEST`: Activado automáticamente por `@Valid` si el JSON de entrada está mal formado.
- `404 NOT FOUND`: Manejado por el `GlobalExceptionHandler` si el ID no existe.
- `409 CONFLICT`: Lanzado por validadores si se intenta duplicar un recurso existente.
- `422 UNPROCESSABLE ENTITY`: Cuando el JSON está perfecto sintácticamente, pero rompe una regla de negocio.

---

## PASO 07: Excepciones y el `GlobalExceptionHandler` (`exception/`)

El `GlobalExceptionHandler` es el Gerente del Restaurante. Intercepta todos los problemas (errores y excepciones) lanzados por los Meseros (Controllers/Services) y le explica al cliente qué ocurrió de una manera uniforme y digna, en lugar de desmayarse y devolver un "500 Internal Server Error" sin contexto.

### 7.1. Las Excepciones del Dominio
Nuestro dominio arroja excepciones personalizadas ricas en significado, blindando la arquitectura:
- `RecursoNoEncontradoException` (Manejado como **404**)
- `ConflictoException` (Manejado como **409** — Ej. Platos con nombres duplicados)
- `EstadoInvalidoException` / `ReglaDeNegocioException` (Manejados como **422** — Ej. Transición ilegal en un pedido).

### 7.2. El `ErrorResponseDTO` Uniforme
Garantizamos que si el front-end, Angular o Postman consumen nuestra API, *siempre* recibirán la misma estructura de error gracias a nuestro Record Factory:

```json
{
  "timestamp": "2026-10-07T16:00:00.123",
  "status": 404,
  "error": "Not Found",
  "message": "No existe Plato con id=99",
  "path": "/api/v1/platos/99"
}
```

### 7.3. La Magia del `@RestControllerAdvice`
Un único componente en todo el proyecto que intercepta cada excepción lanzada.
```java
@RestControllerAdvice 
@Slf4j
public class GlobalExceptionHandler {

    // 404 — Recurso no encontrado (Lógica de Negocio)
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(RecursoNoEncontradoException ex, HttpServletRequest request) {
        log.warn("RecursoNoEncontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDTO.of(404, "Not Found", ex.getMessage(), request.getRequestURI()));
    }

    // 400 — Validación de input automática (@Valid falló en el DTO)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.of(400, "Bad Request", mensaje, request.getRequestURI()));
    }

    // 500 — El Muro de Contención Final
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenerico(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDTO.of(500, "Internal Server Error", "Error inesperado del servidor", request.getRequestURI()));
    }
}
```

> Porque Spring evalúa y selecciona los manejadores en cascada, del más específico al más genérico. Si el manejador genérico de `Exception` estuviera arriba, secuestraría y atraparía todos los demás errores, matando la utilidad de tus excepciones específicas personalizadas. ¡El 500 es siempre tu última línea de defensa!

---

## PASO 08: Swagger/OpenAPI — La doc que se escribe sola (`docs/`)

Hemos integrado **springdoc-openapi** en el `pom.xml`.
Escribir la documentación directamente en los métodos del Controller los vuelve ilegibles (un método termina con 12 líneas de anotaciones por cada 3 líneas de código Java).
Por lo tanto, la arquitectura de este proyecto aísla la documentación utilizando **Interfaces API**.

### 📖 El Patrón de Documentación Aislada
1. Se crea un paquete `docs/` dentro de `controller/`.
2. Se escribe una interfaz pura (ej. `PlatoApi.java`, `MenuApi.java`) que aglomera todas las anotaciones pesadas de `@Tag`, `@Operation` y `@ApiResponses`.
3. El Controller real usa `implements PlatoApi` y **no** necesita tener ninguna de esas anotaciones. ¡Swagger es lo suficientemente inteligente para ir a leerlas desde la interfaz!

```java
// controller/docs/MenuApi.java — La documentación vive aquí
@Tag(name = "Menu", description = "Consulta de la carta - vista del cliente")
public interface MenuApi {

    @Operation(summary = "Ver carta filtrada por categoria")
    @ApiResponse(responseCode = "200", description = "Platos de la categoria")
    ResponseEntity<List<PlatoResponseDTO>> porCategoria(String categoria);
}
```

### 🛠️ Diferenciación Semántica: Platos vs. Menú
Como parte del cierre de la guía del Controlador, analizamos un caso de uso clásico:
*¿Si `Plato` y `Menu` trabajan sobre los mismos datos (platos), deben estar en el mismo Controller?*
**No.** Responden a actores totalmente distintos.

- **`PlatoController` (`/api/v1/platos`):** Diseñado para el **Gerente/Admin**. Contiene el CRUD destructivo, expone todos los platos (incluso los inactivos) y retorna estados técnicos (201, 204).
- **`MenuController` (`/api/v1/menu`):** Diseñado para el **Cliente/Comensal**. Es de solo lectura. Internamente reutiliza exactamente el mismo `IPlatoService`, pero los resultados pasan por el filtro `.filter(Plato::estaDisponible)`. Si intentas buscar un plato inactivo por ID desde este Controller, obtendrás un `404 Not Found` (protegiendo el negocio).

Ambos Controllers mapean de manera distinta y exponen rutas distintas, pero **no duplicamos código en el Service**. Esa es la verdadera maestría de delegar la lógica de negocio lejos del enrutamiento web.

> ✅ **Verificación Final Swagger:**
> Levanta tu servidor Spring Boot y accede a `http://localhost:8080/swagger-ui/index.html`.
> Verás claramente separados los bloques de endpoints para "Menú" y "Platos", con todos los códigos de error correctamente catalogados, mientras que tu código base de Java sigue luciendo prístino y conciso.

---

## PASO 09: Pruebas Unitarias — Confia, pero verifica (src/test/)

La arquitectura que hemos construido es altamente "Testeable" precisamente porque hemos separado las responsabilidades. Al aislar la logica en el Service, Validator y Utils, podemos probar la logica de negocio sin levantar la red (Controller) ni conectarnos a una base de datos real.

> 💡 **La Regla de Oro de las Pruebas Unitarias:**
> Se prueba la **logica** (Service, Validator, Utils). **NO** se prueba la red ni el enrutamiento (Controller) con pruebas unitarias puras; eso se reserva para pruebas de integracion o Swagger/Postman.

### 9.1. Pruebas del Service (@InjectMocks y @Mock)
En el Service probamos el flujo y las decisiones utilizando **Mockito** para controlar las dependencias.

- **@Mock**: Crea un doble controlado de la dependencia (ej. IPlatoValidator o PlatoRepository). No ejecuta su codigo real, sino que nosotros le dictamos que hacer (ej. doThrow(...)).
- **@InjectMocks**: Es la clase real bajo prueba (nuestro PlatoServiceImpl), a la cual se le inyectan los Mocks creados arriba.
- **erify(...)**: Se usa para comprobar que el Service llamo al Validator.
- **Los 5 Escenarios Obligatorios del Service**:
  1. *Happy Path* (Flujo exitoso)
  2. *Recurso No Encontrado* (404 virtual)
  3. *Duplicado/Conflicto* (409 virtual)
  4. *Estado Invalido* (Regla violada)
  5. *Lista Vacia* (No debe explotar con un 
ull pointer)

*(Nota: En nuestro proyecto ya tenemos todo esto rigurosamente cubierto en PlatoServiceImplTest.java)*

### 9.2. Pruebas del Validator (Logica Pura sin Mocks)
El Validator **NO** requiere mocks porque su trabajo es ejecutar reglas algoritmicas directas. Se prueba de forma pura: le pasas datos correctos y verificas que viva; le pasas datos incorrectos y verificas que explote.

`java
class PlatoValidatorTest {
    private final PlatoValidator validator = new PlatoValidator();

    @Test
    void validarNombreUnico_nombreDuplicado_lanzaConflicto() {
        Plato existente = new Plato(); existente.setNombre("Ajiaco");
        List<Plato> existentes = List.of(existente);

        assertThrows(ConflictoException.class, () ->
            validator.validarNombreUnico("AJIACO", existentes));
    }
}
`

### 9.3. Pruebas de Utilities (Casos Limite y Extremos)
De forma analoga al Validator, las Utilities son funciones puras. En nuestro proyecto hemos asegurado nuestro CalculoUtilsTest y TextoUtilsTest para verificar el redondeo, los descuentos, y la normalizacion de mayusculas y espacios extremos.

---

## 🎯 CHECKLIST DEFINITIVO DE CALIDAD

Antes de dar por concluida la implementación de cualquier nuevo dominio en tu Restaurante (ej. Pedidos, Mesas, Cuentas), revisa rigurosamente estos puntos:

- [x] **Dominio (model/domain/)**: Clases de negocio puras. Sin DTOs. Con métodos de comportamiento propio (estaDisponible(), ctivar()). Los Enums gobiernan sus propias transiciones.
- [x] **RequestDTO (model/dto/request/)**: La aduana de entrada. Inmutable (Records). Solo validaciones sintácticas de *Bean Validation* (@NotBlank, @Positive). Cero lógica.
- [x] **ResponseDTO (model/dto/response/)**: El filtro de salida. Cero lógica. Solo contiene los campos que son seguros y relevantes para el cliente.
- [x] **Mapper (mapper/)**: Interfaz con @Mapper(componentModel="spring"). Transforma 	oDomain(), 	oResponse() y listas. **Solo se inyecta en el Controller.**
- [x] **Interfaz Service (service/)**: El contrato agnóstico. Solo recibe y devuelve objetos de Dominio.
- [x] **ServiceImpl (service/impl/)**: El cerebro. @Service, @Slf4j. Usa todo el poder de Java *Streams* para filtrar y operar. No sabe qué es un DTO ni sabe qué es HTTP.
- [x] **Interfaz Validator (alidator/)**: Contrato de validaciones de negocio semánticas. Inyectada en el ServiceImpl.
- [x] **ValidatorImpl (@Component)**: Cada método valida una regla estricta (SRP). Lanza excepciones de negocio si la regla se rompe.
- [x] **Utils (util/)**: Funciones algorítmicas sin estado. Clases inal con constructores private y métodos static. Solo si la lógica es transversal.
- [x] **Controller (controller/)**: Sin lógica de negocio. Usa el patrón: mapperIn -> service -> mapperOut. Usa @Valid. Retorna códigos correctos (200, 201, 204).
- [x] **Excepciones (exception/)**: Semánticas (ConflictoException, RecursoNoEncontradoException). Capturadas uniformemente por el **GlobalExceptionHandler** (con el 500 siempre de último).
- [x] **Swagger (docs/)**: Documentación separada en Interfaces (PlatoApi, MenuApi). @Tag, @Operation, @ApiResponses.
- [x] **Pruebas (src/test/)**: ServiceImpl testeado orquestando con @Mock y @InjectMocks. Validator testeado a pura lógica sin mocks. Utilities testeadas con casos extremos. Los 5 escenarios están cubiertos.

> 🚨 **Los 5 Errores Arquitectónicos Capitales:**
> ❌ El Mapper inyectado en el ServiceImpl (Debe ir en el Controller).
> ❌ El Service recibiendo o devolviendo DTOs (Solo debe ver Dominio).
> ❌ El Controller con condicionales if/else o lógica de negocio.
> ❌ Validaciones de formato y nulidad metidas en el Validator (Pertenecen al DTO con @Valid).
> ❌ Responder un POST con 200 (Debe ser 201) o un DELETE devolviendo JSON (Debe ser 204).

---

## ⏸ Repaso de Cierre (Q&A del Sistema)

**1. ¿Por qué MenuController y PlatoController inyectan el mismo IPlatoService?**
Porque la lógica central es exactamente la misma (ambos interactúan con los Platos del restaurante). Su diferencia es meramente de actor y semántica: el Menú filtra y oculta (Vista Cliente), mientras el PlatoController expone todo con poderes destructivos (Vista Administrador). Crear un MenuService paralelo generaría deuda técnica y duplicación de reglas de negocio.

**2. Un PUT recibe un nombre que ya tiene el propio plato. ¿Debe lanzar 409 Conflicto?**
**No.** En el método ctualizar(), el Validador de nombre único debe excluir del Stream al plato que se está editando mediante un filtro como .filter(p -> !p.getId().equals(id)). Si no filtras el propio plato, el usuario nunca podrá editar el precio de la "Bandeja Paisa" manteniendo su nombre intacto, porque el sistema creerá erróneamente que es un duplicado.

**3. ¿Qué código devuelve un DELETE exitoso y qué body tiene?**
Devuelve **204 No Content** y su body viene completamente vacío: 
eturn ResponseEntity.noContent().build();. Si devuelves 200 OK o adjuntas un body JSON confirmando el borrado, estás rompiendo el estándar HTTP.

**4. ¿Cuándo se lanza un 409 y cuándo un 422?**
- **409 Conflict:** Indica un choque en el estado del recurso (El nombre que intentas registrar *ya está ocupado*, o la mesa que intentas borrar *tiene una cuenta activa*).
- **422 Unprocessable Entity:** El JSON enviado está perfectamente construido, pero intentar procesarlo viola una regla intrínseca de negocio (Intentar devolver el estado de un pedido de "LISTO" a "RECIBIDO", o agregar toppings excediendo el máximo).
