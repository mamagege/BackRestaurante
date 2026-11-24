package com.dows.bitacora2.restaurante.exception;

import com.dows.bitacora2.restaurante.controller.GlobalExceptionHandler;
import com.dows.bitacora2.restaurante.security.JwtAuthFilter;
import com.dows.bitacora2.restaurante.security.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contrato de errores de la API: cada tipo de falla responde con su código HTTP correcto
 * y con el formato RFC 9457 (application/problem+json + propiedades codigo/timestamp).
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ControladorDePrueba.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandlerTest.ControladorDePrueba.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    record Peticion(@NotBlank(message = "El nombre es obligatorio") String nombre,
                    @Positive(message = "El precio debe ser mayor a 0") Double precio) {}

    @RestController
    @RequestMapping("/prueba")
    static class ControladorDePrueba {
        @GetMapping("/no-encontrado") void noEncontrado() { throw new RecursoNoEncontradoException("Mesa", 9L); }
        @GetMapping("/conflicto") void conflicto() { throw new ConflictoException("Ya existe"); }
        @GetMapping("/regla") void regla() { throw new ToppingsExcedidosException("RN-02: máximo 5 toppings"); }
        @GetMapping("/estado") void estado() { throw new EstadoInvalidoException("Estado inválido: X"); }
        @GetMapping("/bloqueo") void bloqueo() { throw new BloqueoTemporalException("Bloqueado"); }
        @GetMapping("/denegado") void denegado() { throw new AccessDeniedException("no"); }
        @GetMapping("/integridad") void integridad() { throw new DataIntegrityViolationException("duplicate key"); }
        @GetMapping("/concurrencia") void concurrencia() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }
        @GetMapping("/inesperado") void inesperado() { throw new IllegalStateException("detalle interno secreto"); }
        @GetMapping("/item/{id}") String item(@PathVariable Long id) { return "ok"; }
        @PatchMapping("/estado-requerido") String estadoRequerido(@RequestParam String estado) { return estado; }
        @PostMapping("/validar") String validar(@Valid @RequestBody Peticion p) { return "ok"; }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private JwtAuthFilter jwtAuthFilter;
    @MockBean private JwtUtil jwtUtil;

    private ResultActions esperarProblema(ResultActions r, int status, String codigo) throws Exception {
        return r.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.instance").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test @DisplayName("recurso inexistente -> 404")
    void noEncontrado_404() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/no-encontrado")), 404, "RECURSO_NO_ENCONTRADO")
                .andExpect(jsonPath("$.detail").value("No existe Mesa con id=9"))
                .andExpect(jsonPath("$.instance").value("/prueba/no-encontrado"));
    }

    @Test @DisplayName("conflicto de dominio -> 409")
    void conflicto_409() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/conflicto")), 409, "CONFLICTO");
    }

    @Test @DisplayName("regla de negocio (incluye subclases) -> 422")
    void reglaDeNegocio_422() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/regla")), 422, "REGLA_DE_NEGOCIO")
                .andExpect(jsonPath("$.detail").value("RN-02: máximo 5 toppings"));
    }

    @Test @DisplayName("estado inválido -> 422")
    void estadoInvalido_422() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/estado")), 422, "REGLA_DE_NEGOCIO");
    }

    @Test @DisplayName("bloqueo temporal -> 429")
    void bloqueo_429() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/bloqueo")), 429, "DEMASIADOS_INTENTOS");
    }

    @Test @DisplayName("acceso denegado en método -> 403")
    void accesoDenegado_403() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/denegado")), 403, "ACCESO_DENEGADO");
    }

    @Test @DisplayName("violación de restricción en BD -> 409 sin exponer el SQL")
    void integridad_409() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/integridad")), 409, "CONFLICTO")
                .andExpect(jsonPath("$.detail", not(containsString("duplicate key"))));
    }

    @Test @DisplayName("bloqueo optimista -> 409")
    void concurrencia_409() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/concurrencia")), 409, "CONFLICTO");
    }

    @Test @DisplayName("error no previsto -> 500 sin filtrar el detalle interno")
    void inesperado_500() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/inesperado")), 500, "ERROR_INTERNO")
                .andExpect(jsonPath("$.detail").value("Error inesperado del servidor"));
    }

    @Test @DisplayName("JSON malformado -> 400 (antes 500)")
    void jsonMalformado_400() throws Exception {
        esperarProblema(mockMvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{malo")), 400, "JSON_INVALIDO");
    }

    @Test @DisplayName("campos inválidos -> 400 con la lista de errores por campo")
    void validacion_400ConErrores() throws Exception {
        esperarProblema(mockMvc.perform(post("/prueba/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"precio\":-1}")), 400, "VALIDACION")
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')].mensaje").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errores[?(@.campo=='precio')].mensaje").value("El precio debe ser mayor a 0"));
    }

    @Test @DisplayName("id no numérico -> 400 (antes 500)")
    void tipoInvalido_400() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/item/abc")), 400, "TIPO_INVALIDO")
                .andExpect(jsonPath("$.detail").value("El valor 'abc' no es válido para 'id'"));
    }

    @Test @DisplayName("falta un @RequestParam -> 400 (antes 500)")
    void parametroFaltante_400() throws Exception {
        esperarProblema(mockMvc.perform(patch("/prueba/estado-requerido")), 400, "PARAMETRO_FALTANTE")
                .andExpect(jsonPath("$.detail").value("Falta el parámetro obligatorio 'estado'"));
    }

    @Test @DisplayName("ruta inexistente -> 404 (antes 500)")
    void rutaInexistente_404() throws Exception {
        esperarProblema(mockMvc.perform(get("/prueba/no-existe")), 404, "RUTA_NO_ENCONTRADA");
    }

    @Test @DisplayName("método no soportado -> 405 con cabecera Allow")
    void metodoNoPermitido_405() throws Exception {
        esperarProblema(mockMvc.perform(delete("/prueba/conflicto")), 405, "METODO_NO_PERMITIDO")
                .andExpect(header().exists("Allow"));
    }

    @Test @DisplayName("tipo de contenido no soportado -> 415")
    void tipoDeContenido_415() throws Exception {
        esperarProblema(mockMvc.perform(post("/prueba/validar").contentType(MediaType.TEXT_PLAIN).content("x")),
                415, "TIPO_DE_CONTENIDO_NO_SOPORTADO");
    }
}
