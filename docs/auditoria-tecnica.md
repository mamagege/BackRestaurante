# Auditoría técnica — Backend Restaurante "Bella Ciao"

**Fecha:** 2026-11-24 · **Alcance:** repositorio `BackRestaurante` completo (código, configuración, pruebas, Docker, CI/CD y documentación).
**Línea base:** Spring Boot 3.2.3 · Java 21 · PostgreSQL + MongoDB · 38 pruebas en verde.

> Rutas abreviadas: `…/` = `src/main/java/com/dows/bitacora2/restaurante/`.
> Los hallazgos marcados **[verificado]** se reprodujeron con una prueba MockMvc contra la `SecurityConfig` y el `GlobalExceptionHandler` reales. La prueba fue temporal y no quedó en el repositorio.

---

## 1. Resumen ejecutivo

La base arquitectónica es buena para un proyecto de este tamaño:
- capas claras (controller → service → repository);
- dominio separado de las entidades JPA;
- mappers MapStruct en dos niveles;
- DTOs como `record` con Bean Validation;
- excepciones semánticas;
- documentación Swagger aislada en interfaces `*Api`;
- pipeline CI/CD funcional hacia Azure.

Los problemas graves están en cuatro frentes:

1. **Seguridad.** El secreto JWT de producción es el que está en el repositorio. Hay usuarios con contraseñas por defecto en todos los ambientes. Las reglas de autorización por URL no coinciden con las rutas reales: un `CLIENTE` puede pagar cuentas y cerrar mesas.
2. **Reglas de negocio rotas extremo a extremo.**
   - Por la API no se puede pedir ninguna pizza ni pasta.
   - La cuenta de una mesa suma los pedidos de clientes anteriores.
   - RN-06 (una sola cuenta abierta por mesa) y RN-04 (pedido modificable solo en `RECIBIDO`) no resisten concurrencia.
3. **Contrato de errores.** Credenciales incorrectas, JSON malformado, parámetros faltantes y rutas inexistentes responden **500**.
4. **Pruebas que dan falsa confianza.** La prueba de seguridad pasa por una razón distinta a la que cree probar, y la capa DTO → mapper → servicio no tiene ninguna prueba.

| Severidad | Cantidad |
|---|---|
| 🔴 Crítico | 7 |
| 🟠 Alto | 12 |
| 🟡 Medio | 14 |
| 🔵 Bajo / higiene | 10 |

---

## 2. Hallazgos críticos 🔴

### C-01 · Producción firma los JWT con el secreto público del repositorio
- **Dónde:** `…/security/JwtUtil.java:17` lee `spring.jwt.secret`, que está fijo en `application.yml:27`. El perfil `docker` define otra clave, `jwt.secret: ${JWT_SECRET}`, que nadie lee. Los pipelines inyectan la variable `JWT_SECRET`, y Spring la enlaza con `jwt.secret`, no con `spring.jwt.secret`.
- **Impacto:** cualquiera que lea el repositorio puede firmar tokens válidos. El filtro carga los roles desde la base de datos, así que basta conocer el email de un usuario, y los emails sembrados son públicos (ver C-02). El resultado es una suplantación total, incluido el rol `ADMIN`.
- **Corrección:**
  - Leer una sola clave, por ejemplo `app.security.jwt.secret: ${JWT_SECRET}`, sin valor por defecto.
  - Borrar el secreto del YAML.
  - Validar al arrancar que el secreto exista y tenga al menos 256 bits (con `@ConfigurationProperties` y `@Validated`).
  - Rotar el secreto en QA y en producción.

### C-02 · Usuarios con contraseña por defecto en todos los ambientes, y las contraseñas van al log
- **Dónde:** `…/config/DataSeeder.java:24-34`.
- **Impacto:** si la tabla está vacía, se crea `admin@restaurante.com / admin123` también en QA y producción. Además, las cuatro contraseñas se imprimen con `System.out`.
- **Corrección:**
  - Ejecutar el seeder solo con `@Profile("dev")`, o tomar las credenciales iniciales de variables de entorno.
  - No imprimir contraseñas.
  - Cambiar `System.out` por el logger.

### C-03 · Autorización por URL desalineada: un CLIENTE puede pagar cuentas y cerrar mesas **[verificado]**
- **Dónde:** `…/config/SecurityConfig.java:64-72`. Las reglas protegen `abrir-cuenta`, `cerrar-cuenta`, `actualizar-total` y `pagar`. Los controllers exponen `apertura-cuenta`, `cierre-cuenta`, `total` y `pago`.
- **Impacto:** esas rutas caen en `anyRequest().authenticated()`, así que cualquier usuario autenticado puede llamarlas. Resultado de la prueba: `PATCH /api/v1/cuentas/1/pago` con token de CLIENTE → **200**, y `PATCH /api/v1/mesas/1/cierre-cuenta` con token de CLIENTE → **200**.
- **Corrección:**
  - Usar una sola fuente de autorización: `@PreAuthorize` en todos los endpoints de `MesaController` y `CuentaController`, que hoy no tienen ninguno.
  - Dejar en `SecurityConfig` solo las rutas públicas y una regla por defecto `denyAll` o `authenticated`.
  - Añadir pruebas de autorización por rol sobre la configuración real (ver A-11).

### C-04 · No se puede pedir una pizza ni una pasta por la API
- **Dónde:** `…/model/dto/request/ItemPedidoRequestDTO.java` no tiene `masa`, `salsa` ni `toppings`. El `PedidoMapperImpl` generado solo copia `idPlato` y `cantidad`. `…/service/PedidoServiceImpl.java:146-158` exige masa y salsa (RN-01).
- **Impacto:** todo pedido con un plato de categoría Pizza o Pasta responde 422. Además, el campo `notas` se recibe y se descarta, porque el dominio no lo tiene. Las pruebas unitarias no lo detectan porque construyen el dominio directamente y se saltan el DTO y el mapper.
- **Corrección:**
  - Añadir `masa`, `salsa` y `toppings` al DTO, con `@Size(max = 10)` en la lista como cota sintáctica; la regla de 5 toppings sigue en el dominio.
  - Añadir `notas` al dominio y a la entidad.
  - Activar `unmappedTargetPolicy = ReportingPolicy.ERROR` en los mappers para que un campo olvidado rompa la compilación.

### C-05 · La cuenta de una mesa incluye los pedidos de clientes anteriores
- **Dónde:** `…/service/CuentaServiceImpl.java:68` y `:89` usan `pedidoRepository.findByIdMesa(idMesa)`. `Pedido` no tiene relación con `Cuenta`.
- **Impacto:**
  - Cuando una mesa se libera y se vuelve a abrir, el total de la nueva cuenta suma todos los pedidos históricos de la mesa.
  - La regla "todos los pedidos entregados" también se evalúa sobre el historial completo.
  - El ciclo de facturación queda mal desde la segunda cuenta de cada mesa.
- **Corrección:**
  - Asociar cada `Pedido` a su `Cuenta` con un campo `idCuenta` y una FK.
  - Al crear un pedido, exigir que la mesa tenga una cuenta `ABIERTA` (regla del modelo de dominio que hoy no se valida) y asignarle esa cuenta.
  - Calcular totales y pendientes solo con los pedidos de esa cuenta.

### C-06 · RN-06 y RN-04 no resisten concurrencia (RNF10)
- **Dónde:** `MesaServiceImpl.abrirCuenta`, `CuentaServiceImpl.crear`, `PedidoServiceImpl.actualizar` y `PedidoServiceImpl.cambiarEstado`. Todos siguen el patrón "leer → validar → guardar" sin bloqueo y sin `@Version`.
- **Impacto:**
  - Dos meseros pueden abrir dos cuentas en la misma mesa a la vez.
  - Un cliente puede modificar un pedido en el mismo instante en que cocina lo pasa a `EN_PREPARACION`. Es exactamente la contradicción RF2/RF4 que señala `contexto/requirements.md`, y sigue sin resolverse.
- **Corrección:**
  - Añadir `@Version` (bloqueo optimista) a `PedidoEntity`, `MesaEntity` y `CuentaEntity`, y traducir `ObjectOptimisticLockingFailureException` a **409**.
  - Crear un índice único parcial en PostgreSQL: `CREATE UNIQUE INDEX ux_cuenta_abierta ON cuentas(id_mesa) WHERE estado = 'ABIERTA'`. Es una garantía que no depende del código.

### C-07 · Credenciales incorrectas responden 500 **[verificado]**
- **Dónde:** `…/service/AuthServiceImpl.java:54` relanza `AuthenticationException` "para que Spring devuelva 401". El `@ExceptionHandler(Exception.class)` de `…/controller/GlobalExceptionHandler.java:63` la captura antes.
- **Impacto:** `POST /api/v1/auth/login` con contraseña mala devuelve **500 "Error inesperado del servidor"**. El front no puede distinguir unas credenciales malas de una caída del servidor, y las alertas de 5xx quedan contaminadas.
- **Corrección:** añadir un handler de `AuthenticationException` que responda 401 con un mensaje genérico ("Credenciales inválidas").

---

## 3. Hallazgos altos 🟠

### A-01 · Varios errores del cliente responden 500 **[verificado]**
El handler genérico atrapa todas las excepciones del framework. Casos probados:

| Caso | Hoy | Debería |
|---|---|---|
| JSON malformado (`HttpMessageNotReadableException`) | 500 | 400 |
| `GET /cuentas/abc` (`MethodArgumentTypeMismatchException`) | 500 | 400 |
| Ruta inexistente (`NoResourceFoundException`) | 500 | 404 |
| Falta un `@RequestParam` (`MissingServletRequestParameterException`) | 500 | 400 |
| Método no soportado | 500 | 405 |
| Violación de constraint en BD (`DataIntegrityViolationException`) | 500 | 409 |

**Corrección:** hacer que `GlobalExceptionHandler` extienda `ResponseEntityExceptionHandler` y adoptar `ProblemDetail` (RFC 9457) como formato único de error. Las respuestas 401 y 403 de `SecurityConfig` y de `JwtAuthFilter` escriben JSON a mano con otra forma (sin `timestamp` ni `path`, y "inv?lido" mal codificado); deben usar el mismo formato.

### A-02 · `/api/v1/menu` exige autenticación **[verificado]**
`GET /api/v1/menu` sin token responde 401. La carta es la vista pública del cliente (RF1), y solo `GET /api/v1/platos/**` es público. Hay que añadir `GET /api/v1/menu/**` a `permitAll`.

### A-03 · Un token inválido bloquea también las rutas públicas **[verificado]**
`JwtAuthFilter` responde 401 ante cualquier token inválido, sin importar la ruta. Por ejemplo, `GET /menu` con un token expirado devuelve 401. Lo correcto es que, si el token no es válido, el filtro no autentique y deje decidir a las reglas de autorización. Así una sesión vencida no rompe la carta pública.

### A-04 · Bloqueo por intentos fallidos permanente y explotable
`AuthServiceImpl` guarda los intentos en un `ConcurrentHashMap` que nunca expira ni se limpia:
- Con 5 intentos fallidos contra `admin@…`, cualquiera bloquea al administrador hasta que se reinicie la aplicación.
- El patrón `getOrDefault` + `put` no es atómico.
- No funciona con varias instancias.

**Corrección:** usar una ventana de tiempo (por ejemplo, 15 minutos) con Caffeine/Bucket4j o con la base de datos, `merge()` atómico y clave normalizada (`email.toLowerCase().strip()`).

### A-05 · Los eventos de auditoría (RN-05) se pierden en silencio
- **Dónde:** `PedidoServiceImpl.java:62`, `:104` y `:128` usan `CompletableFuture.runAsync`.
- **Problemas:**
  - Se ejecuta en el `ForkJoinPool` común, **antes del commit**: se registra el evento aunque la transacción haga rollback.
  - Las excepciones se descartan sin ningún log.
  - No guarda el **usuario** que hizo el cambio, que es justo lo que exigen RN-05 y RNF5.
  - En `docker-compose.yml`, Mongo arranca con usuario root (`MONGO_INITDB_ROOT_USERNAME`) pero la URI del perfil `docker` no lleva credenciales. Las escrituras fallan por autenticación y nadie se entera.
- **Corrección:** publicar un `PedidoEstadoCambiadoEvent` y consumirlo con `@TransactionalEventListener(phase = AFTER_COMMIT)` y `@Async` sobre un `ThreadPoolTaskExecutor` propio, con manejo y log de errores. Tomar el usuario de `SecurityContextHolder`.

### A-06 · Cada cambio de estado borra y vuelve a insertar todos los ítems del pedido
- **Dónde:** `…/mapper/PedidoEntityMapper.java:18` ignora el `id` del ítem. `cambiarEstado`, `actualizar` y `eliminar` convierten dominio → entidad nueva → `save` (merge).
- **Impacto:** con `orphanRemoval = true`, cada transición borra y recrea las filas de `item_pedido` y `item_pedido_toppings`. Los ids de los ítems cambian, se escribe de más en la base y se desactiva el *dirty checking*.
- **Corrección:** en los comandos, cargar la entidad gestionada y modificarla (`entity.setEstado(...)`). Si se mantiene el mapeo, conservar el id del ítem.

### A-07 · La mesa tiene dos fuentes de verdad que pueden contradecirse, y RN-07 se puede saltar
- Los endpoints `PATCH /mesas/{id}/apertura-cuenta` y `/cierre-cuenta` cambian `Mesa.cuentaAbierta` sin crear ni cerrar una `Cuenta`. Una mesa puede liberarse sin ningún pago registrado.
- `PATCH /mesas/{id}/estado` acepta cualquier estado, por ejemplo `DISPONIBLE` con la cuenta abierta.
- Si se abre la mesa por su endpoint, después `POST /cuentas` falla con 422.

**Corrección:**
- Que la apertura y el cierre ocurran solo a través de `Cuenta`: `POST /cuentas` abre la cuenta y `pago` la cierra.
- Que el estado de la mesa sea una consecuencia de eso.
- Quitar los dos endpoints o hacerlos de solo lectura.
- Validar las transiciones de `EstadoMesa` igual que se hace con `EstadoPedido.puedeTransicionarA`.

### A-08 · El pago no registra método ni monto (BCR-05)
`PATCH /cuentas/{id}/pago` no recibe body. BCR-05 exige `metodoPago` (tarjeta, efectivo o transferencia) y `montoRecibido >= total`, y no queda ningún comprobante. Hace falta un `PagoRequestDTO` y una entidad `Pago`. La integración con la pasarela externa debería ir detrás de un puerto, una interfaz `PasarelaPago` con un adaptador.

### A-09 · El dinero se maneja como `Double`
Los precios y totales usan `Double` en `Plato`, `ItemPedido`, `Cuenta` y sus entidades, y en la base son `float8`. Con eso hay errores de redondeo al sumar. **Corrección:** usar `BigDecimal` con escala 2 y `numeric(12,2)` en la base, y que `CalculoUtils` opere con `BigDecimal`.

### A-10 · Se borran platos en lugar de desactivarlos (RF9)
`PlatoServiceImpl.java:95` hace `deleteById`. RF9 pide desactivar un plato sin borrar su historial. **Corrección:** borrado lógico (`activo = false`) y que `DELETE` desactive; o eliminar el endpoint y dejar solo `PATCH /disponibilidad`.

### A-11 · Las pruebas de seguridad no prueban la configuración real
`SecurityTest` usa `@WebMvcTest` sin `@Import(SecurityConfig.class)`, así que Spring carga la cadena de seguridad por defecto, que tiene **CSRF activado**. Los asserts `403` pasan por falta de token CSRF, no por el rol. Además, "sin token" debería ser **401**, no 403. Las fallas C-03, C-07, A-01 y A-02 sobrevivieron por esta razón.

**Corrección:** importar `SecurityConfig`, `CorsConfig`, `JwtAuthFilter` y `JwtUtil`, y escribir una prueba parametrizada (rol × endpoint × resultado esperado) que actúe como matriz de autorización viva.

### A-12 · Spring Boot 3.2.3 está fuera de soporte
La línea 3.2.x dejó de recibir parches OSS a finales de 2024 y arrastra CVEs conocidos en Spring Framework, Spring Security y Tomcat. **Corrección:** migrar a una línea soportada (3.5.x, o 4.0.x si el equipo puede absorber Jakarta EE 11), actualizar springdoc, jjwt y MapStruct, y activar Dependabot o OWASP Dependency-Check en CI.

---

## 4. Hallazgos medios 🟡

| ID | Hallazgo | Dónde | Recomendación |
|---|---|---|---|
| M-01 | `resumenDelDia` no filtra por día, cuenta pedidos cancelados, cuenta líneas y no cantidades, no reporta por mesero (RF6) y devuelve un DTO desde el servicio (rompe la regla 2 del propio equipo) | `PedidoServiceImpl.java:165` | Consulta agregada en el repositorio (`@Query` con `GROUP BY`) filtrada por rango de fechas y estado; modelo de dominio `ResumenVentas`; registrar el mesero en el pedido |
| M-02 | `/pedidos/resumen` está disponible para el rol CLIENTE y no está documentado en `PedidoApi` | `PedidoController.java:75` | `@PreAuthorize("hasRole('ADMIN')")` y documentarlo en Swagger |
| M-03 | Un CLIENTE puede listar y ver todos los pedidos de todas las mesas (IDOR) | `PedidoController` `GET` | Filtrar por la mesa o cuenta del usuario autenticado; hay que modelar la relación usuario ↔ cuenta |
| M-04 | Lógica de negocio en el controller (`if (!plato.estaDisponible())`, `.filter(...)`), que contradice la regla 3 del equipo | `MenuController.java:38,50` | Métodos de servicio `obtenerDisponiblePorId` y `obtenerDisponiblesPorCategoria`, con la consulta `findByCategoriaIgnoreCaseAndDisponibleTrue` |
| M-05 | Las reglas de negocio viven en el servicio y no en el dominio (RN-01/RN-02 en `validarYCompletarItems`, transiciones de mesa en `MesaServiceImpl`), y el dominio expone setters para todo | `PedidoServiceImpl.java:137-161` | Dominio rico: `ItemPedido.personalizar(masa, salsa, toppings)`, `Pedido.cambiarEstado(...)`, `Mesa.abrir()`. Las invariantes se protegen en el objeto y el servicio orquesta |
| M-06 | Reglas propias del concepto italiano sin implementar: máximo 2 proteínas y máximo 3 salsas en pasta | — | Implementarlas en el dominio y probarlas (RNF7) |
| M-07 | Categoría como texto libre y comparada con "Pizza"/"Pasta" escritos en el código | `PedidoServiceImpl.java:146` | `enum CategoriaPlato` con un atributo `personalizable` y un `maxToppings` |
| M-08 | Duplicado de número de mesa responde 422 (los docs dicen 409); la validación recorre `findAll()` en memoria | `MesaServiceImpl.java:55,73` | `existsByNumero` / `existsByNumeroAndIdNot` y `ConflictoException`; manejar también la violación de la restricción única |
| M-09 | La documentación Swagger contradice los códigos reales (dice 400 o 409 donde se responde 422) | `controller/docs/*Api.java` | Alinear con el handler; reutilizar respuestas comunes con `@ApiResponse(ref=…)` |
| M-10 | Los listados no tienen paginación (`findAll()` en mesas, pedidos, cuentas y platos) | Servicios | `Pageable` y `Page<T>` en los listados que crecen (pedidos, cuentas) |
| M-11 | Consultas N+1 y Open-Session-In-View activo por defecto. Los ítems (`@OneToMany`) y los toppings (`@ElementCollection`) se cargan perezosamente, ítem por ítem, durante el mapeo | Entidades `Pedido` e `ItemPedido` | `spring.jpa.open-in-view=false`, `@EntityGraph` o `JOIN FETCH` en las consultas de pedidos, y `@Transactional(readOnly = true)` en las lecturas |
| M-12 | `ddl-auto: update` en todos los perfiles, también producción, sin migraciones versionadas | `application*.yml` | Flyway (`db/migration/V1__init.sql`, …) y `ddl-auto: validate` |
| M-13 | Configuración inconsistente: el perfil por defecto exige SSL con un `.p12` que no está en el repo (en local no arranca), pide `GOOGLE_CLIENT_ID/SECRET` que no figuran en `.env.example`, y tiene `show-sql: true` y la contraseña de BD escrita en el YAML | `application.yml` | Perfiles `dev`, `test` y `prod`; todo por variable de entorno; `.env.example` completo; `@ConfigurationProperties` validadas |
| M-14 | OAuth2 con sesión (`JSESSIONID`), CSRF desactivado y CORS con credenciales: una sesión iniciada con Google queda expuesta a CSRF. Además, el `successHandler` devuelve el token como JSON en lugar de redirigir al front, y guarda `"OAUTH2_USER"` como contraseña | `SecurityConfig.java:91` y siguientes | API `STATELESS` para `/api/**` y sesión solo durante el flujo OAuth (dos `SecurityFilterChain`); redirigir al front con un código de un solo uso en lugar de exponer el token en el body |

---

## 5. Hallazgos bajos / higiene 🔵

| ID | Hallazgo |
|---|---|
| B-01 | **Codificación rota (mojibake)** en mensajes que llegan al cliente: `"no estÃƒÂ¡ disponible"` (`PedidoServiceImpl`), `"MÃ¡ximo 100 caracteres"` (`PlatoRequestDTO`), `"â†’"` en logs y todo `controlador/controlador.md`. Conviene recodificar los archivos a UTF-8 y añadir `.editorconfig`. |
| B-02 | **Archivos que no deberían estar versionados:** `target/` (4 MB) y `.idea/` están en git aunque figuran en `.gitignore`. Cada build ensucia el árbol. Solución: `git rm -r --cached target .idea .vscode`. |
| B-03 | **Otro proyecto completo dentro de `security/img/test/`**: una API de barbería (`edu.dosw.parcial`, unos 100 archivos) guardada como si fuera imágenes. Hay que eliminarla. |
| B-04 | **Código y pruebas vacíos o muertos:**<br>• Archivos vacíos: `PlatoService.java`, `PlatoNotFoundException.java`, `PlatoAlreadyExistsException.java`.<br>• Excepción sin uso: `MesaNoDisponibleException`.<br>• Auditoría paralela sin uso: `EventoRestaurante`, `EventoMapper` y `EventoRestauranteRepository`.<br>• Método engañoso: `asociarPedido(idCuenta, idPedido)` ignora `idPedido`.<br>• Configuración de pruebas vacía: `application-test.properties`.<br>• Clases de prueba sin ningún `@Test`: `PlatoControllerTest`, `GlobalExceptionHandlerTest`, `PlatoMapperTest`, `PlatoRepositoryTest`. |
| B-05 | **Estilo inconsistente:**<br>• Anotaciones y tipos con el paquete completo dentro del código (`@org.springframework...PreAuthorize`, `java.util.concurrent.CompletableFuture`, `@lombok.RequiredArgsConstructor`) en lugar de imports.<br>• Lombok a medias: el dominio tiene getters manuales y `EventoRestaurante` usa `@Data`.<br>• `DataSeeder` inyecta por constructor manual, el resto con Lombok. |
| B-06 | **Logging:**<br>• Un 404 se registra como `log.error` ("no encontrad@"), cuando es `warn` o `debug`.<br>• `toUpperCase()` sin `Locale.ROOT`.<br>• Los estados llegan como `String` y se convierten a mano con `valueOf`; es mejor recibir directamente el `enum` en el `@RequestParam` o en el body. |
| B-07 | **`pom.xml`:**<br>• Las versiones de Lombok y MapStruct están repetidas; conviene declararlas como propiedades.<br>• JaCoCo no tiene umbral mínimo (falta el goal `check`).<br>• No hay Maven Wrapper (`mvnw`).<br>• `artifactId` en mayúsculas (`BITACORA-TERCIO2`).<br>• `@MockBean` está deprecado desde Boot 3.4; el reemplazo es `@MockitoBean`. |
| B-08 | **Docker:**<br>• La imagen corre como `root` (falta `USER`).<br>• No hay `HEALTHCHECK` (Actuator `/actuator/health`) ni flags de memoria para contenedor (`-XX:MaxRAMPercentage=75`).<br>• En compose, la clave `version` está obsoleta, las credenciales están escritas en el archivo y las bases de datos quedan expuestas al host. |
| B-09 | **CI/CD:**<br>• El pipeline de producción reconstruye la imagen desde el tag **sin correr pruebas**; lo correcto es promover la imagen ya probada en QA (*build once, deploy many*).<br>• `ci-qa` no corre en ramas `feature/**` ni en PRs hacia `develop`.<br>• `azure/login@v1` está deprecado.<br>• Falta `mvn verify` con umbral de cobertura y análisis estático (Checkstyle/SpotBugs o Sonar). |
| B-10 | **Documentación:**<br>• El README tiene una sola línea.<br>• Las imágenes que referencia `contexto/*.md` (`../uml/…`, `../images/mockup/…`) no existen.<br>• Hay documentos duplicados (`persistencia.md` y `persistency/persistency.md`; cada `.md` tiene además una copia `.html`).<br>• No hay guía para levantar el proyecto en local. |

---

## 6. Lo que está bien ✅

- Separación de capas y regla "el mapper vive en el controller" aplicadas con bastante consistencia.
- Dominio separado de las entidades JPA, con algo de comportamiento (`EstadoPedido.puedeTransicionarA`, `Pedido.puedeModificarse`).
- DTOs inmutables (`record`) con Bean Validation y `@Valid` en las listas anidadas.
- Excepciones semánticas con jerarquía (`ToppingsExcedidosException extends ReglaDeNegocioException`).
- Documentación Swagger separada en interfaces `*Api`, con el esquema `bearerAuth` configurado.
- Contraseñas con BCrypt y JWT con jjwt 0.12 (API moderna, `verifyWith`).
- Dockerfile multi-stage con imagen JRE Alpine y caché de dependencias.
- El pipeline separa QA (automático) de producción (aprobación manual) e inyecta los secretos con `toJson`.
- Pruebas de servicio bien organizadas (AAA, cobertura de bucles, ramas y límites, como el caso de exactamente 5 toppings).

---

## 7. Arquitectura objetivo (recomendación)

El equipo ya contempla `refactor` hacia hexagonal en su convención de commits. Una ruta incremental que no rompe lo existente:

```
restaurante/
├── pedido/                     ← paquete por feature (bounded context)
│   ├── domain/                 Pedido, ItemPedido, EstadoPedido, reglas RN-01..RN-04 (sin Spring)
│   ├── application/            casos de uso: CrearPedido, CambiarEstadoPedido (+ puertos de salida)
│   ├── infrastructure/
│   │   ├── persistence/        PedidoEntity, PedidoJpaRepository, adaptador del puerto
│   │   └── audit/              listener AFTER_COMMIT → Mongo
│   └── web/                    PedidoController, DTOs, PedidoWebMapper, PedidoApi
├── cuenta/  mesa/  carta/  auth/
└── shared/                     ProblemDetail handler, config, seguridad
```

**Principios:**
- El dominio no depende de Spring.
- Los servicios dependen de puertos (interfaces), no de `JpaRepository`. Hoy `CuentaServiceImpl` accede directamente al `PedidoRepository` de otro agregado.
- Las invariantes viven en los agregados.
- La infraestructura es intercambiable: pasarela de pago, facturación electrónica y proveedor de identidad van detrás de puertos, tal como pide el diagrama de contexto C4.

---

## 8. Plan de remediación priorizado

Cada bloque va en una rama `feature/...` con commits según la convención del equipo.

| # | Rama | Commits sugeridos | Cubre |
|---|---|---|---|
| 1 | `feature/hardening-seguridad` | `fix(security): leer secreto jwt desde variable de entorno`<br>`fix(security): alinear rutas protegidas con los endpoints reales`<br>`fix(auth): responder 401 ante credenciales invalidas`<br>`fix(security): permitir acceso publico a la carta`<br>`chore(config): restringir datos semilla al perfil dev` | C-01, C-02, C-03, C-07, A-02, A-03, A-04 |
| 2 | `feature/manejo-errores-problem-detail` | `refactor(error): adoptar problem detail y response entity exception handler` | A-01, M-09 |
| 3 | `feature/pedido-personalizacion` | `fix(pedido): exponer masa salsa y toppings en el dto de items`<br>`feat(pedido): validar limites de proteinas y salsas en pasta` | C-04, M-06, M-07 |
| 4 | `feature/cuenta-pedido-relacion` | `fix(cuenta): calcular total solo con pedidos de la cuenta`<br>`feat(cuenta): registrar metodo y monto de pago` | C-05, A-07, A-08 |
| 5 | `feature/concurrencia-y-auditoria` | `fix(pedido): agregar bloqueo optimista con version`<br>`refactor(auditoria): publicar eventos despues del commit con usuario` | C-06, A-05, A-06 |
| 6 | `feature/dinero-bigdecimal-flyway` | `refactor(dominio): usar bigdecimal para montos`<br>`build(db): agregar migraciones flyway` | A-09, M-12 |
| 7 | `feature/pruebas-seguridad-integracion` | `test(security): matriz de autorizacion por rol`<br>`test(pedido): pruebas web de punta a punta del dto al servicio` | A-11, B-04 |
| 8 | `feature/upgrade-spring-boot` | `build(deps): actualizar spring boot a linea soportada` | A-12, B-07 |
| 9 | `feature/limpieza-repositorio` | `chore(repo): dejar de versionar target e idea`<br>`chore(repo): eliminar proyecto ajeno en security/img/test`<br>`fix(i18n): corregir codificacion utf-8 de mensajes` | B-01..B-03 |
| 10 | `feature/ci-cd-mejoras` | `ci(prod): promover imagen probada en qa`<br>`ci(qa): ejecutar pruebas en ramas feature y prs` | B-08, B-09 |
