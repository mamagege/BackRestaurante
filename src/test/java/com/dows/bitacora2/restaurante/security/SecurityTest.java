package com.dows.bitacora2.restaurante.security;

import com.dows.bitacora2.restaurante.config.CorsConfig;
import com.dows.bitacora2.restaurante.config.SecurityConfig;
import com.dows.bitacora2.restaurante.controller.AuthController;
import com.dows.bitacora2.restaurante.controller.CuentaController;
import com.dows.bitacora2.restaurante.controller.MenuController;
import com.dows.bitacora2.restaurante.controller.MesaController;
import com.dows.bitacora2.restaurante.controller.PedidoController;
import com.dows.bitacora2.restaurante.controller.PlatoController;
import com.dows.bitacora2.restaurante.mapper.CuentaMapper;
import com.dows.bitacora2.restaurante.mapper.MesaMapper;
import com.dows.bitacora2.restaurante.mapper.PedidoMapper;
import com.dows.bitacora2.restaurante.mapper.PlatoMapper;
import com.dows.bitacora2.restaurante.repository.UsuarioRepository;
import com.dows.bitacora2.restaurante.service.IAuthService;
import com.dows.bitacora2.restaurante.service.ICuentaService;
import com.dows.bitacora2.restaurante.service.IMesaService;
import com.dows.bitacora2.restaurante.service.IPedidoService;
import com.dows.bitacora2.restaurante.service.IPlatoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de autorización viva: carga la SecurityConfig REAL y verifica, para cada
 * endpoint de la API, qué roles pueden entrar y cuáles reciben 401/403.
 * Si se agrega un endpoint o se cambia un rol, esta tabla debe actualizarse.
 */
@WebMvcTest(controllers = {AuthController.class, PlatoController.class, MenuController.class,
        PedidoController.class, MesaController.class, CuentaController.class})
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthFilter.class, JwtUtil.class,
        ProblemDetailSecurityHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test"})
class SecurityTest {

    private static final String ANONIMO = "ANONIMO";
    private static final List<String> ROLES = List.of("ADMIN", "MESERO", "COCINA", "CLIENTE", ANONIMO);
    private static final String PUBLICO = "PUBLICO";

    private static final String PLATO = "{\"nombre\":\"Pizza\",\"precio\":10,\"categoria\":\"Pizza\"}";
    private static final String PEDIDO = "{\"idMesa\":1,\"items\":[{\"idPlato\":1,\"cantidad\":1}]}";
    private static final String MESA = "{\"numero\":1,\"capacidad\":4}";
    private static final String CUENTA = "{\"idMesa\":1}";
    private static final String PAGO = "{\"metodoPago\":\"EFECTIVO\",\"montoRecibido\":1000}";
    private static final String LOGIN = "{\"email\":\"a@b.com\",\"password\":\"12345678\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;

    @MockitoBean private IAuthService authService;
    @MockitoBean private IPlatoService platoService;
    @MockitoBean private IPedidoService pedidoService;
    @MockitoBean private IMesaService mesaService;
    @MockitoBean private ICuentaService cuentaService;
    @MockitoBean private PlatoMapper platoMapper;
    @MockitoBean private PedidoMapper pedidoMapper;
    @MockitoBean private MesaMapper mesaMapper;
    @MockitoBean private CuentaMapper cuentaMapper;
    @MockitoBean private UsuarioDetailsService usuarioDetailsService;
    @MockitoBean private UsuarioRepository usuarioRepository;

    /** método, ruta, body, roles permitidos (o PUBLICO). */
    private static Stream<Arguments> matriz() {
        return Stream.of(
                fila("POST", "/api/v1/auth/login", LOGIN, PUBLICO),
                fila("POST", "/api/v1/auth/registro", LOGIN, PUBLICO),
                fila("GET", "/api/v1/auth/me", null, "ADMIN", "MESERO", "COCINA", "CLIENTE"),

                fila("GET", "/api/v1/menu", null, PUBLICO),
                fila("GET", "/api/v1/menu/1", null, PUBLICO),
                fila("GET", "/api/v1/menu/categoria/pizza", null, PUBLICO),

                fila("GET", "/api/v1/platos", null, PUBLICO),
                fila("GET", "/api/v1/platos/1", null, PUBLICO),
                fila("POST", "/api/v1/platos", PLATO, "ADMIN"),
                fila("PUT", "/api/v1/platos/1", PLATO, "ADMIN"),
                fila("PATCH", "/api/v1/platos/1/disponibilidad?disponible=false", null, "ADMIN"),
                fila("DELETE", "/api/v1/platos/1", null, "ADMIN"),

                fila("GET", "/api/v1/pedidos", null, "ADMIN", "MESERO", "COCINA", "CLIENTE"),
                fila("GET", "/api/v1/pedidos/1", null, "ADMIN", "MESERO", "COCINA", "CLIENTE"),
                fila("GET", "/api/v1/pedidos/resumen", null, "ADMIN", "MESERO"),
                fila("POST", "/api/v1/pedidos", PEDIDO, "CLIENTE", "MESERO"),
                fila("PUT", "/api/v1/pedidos/1", PEDIDO, "CLIENTE", "MESERO"),
                fila("PATCH", "/api/v1/pedidos/1/estado?estado=LISTO", null, "COCINA", "MESERO"),
                fila("DELETE", "/api/v1/pedidos/1", null, "MESERO", "ADMIN"),

                fila("GET", "/api/v1/mesas", null, "CLIENTE", "MESERO", "ADMIN"),
                fila("GET", "/api/v1/mesas/1", null, "CLIENTE", "MESERO", "ADMIN"),
                fila("POST", "/api/v1/mesas", MESA, "ADMIN"),
                fila("PUT", "/api/v1/mesas/1", MESA, "ADMIN"),
                fila("PATCH", "/api/v1/mesas/1/estado?estado=OCUPADA", null, "MESERO", "ADMIN"),
                // eliminados: la mesa solo se ocupa/libera a través de la cuenta (RN-07)
                fila("PATCH", "/api/v1/mesas/1/apertura-cuenta", null),
                fila("PATCH", "/api/v1/mesas/1/cierre-cuenta", null),

                fila("GET", "/api/v1/cuentas", null, "CLIENTE", "MESERO", "ADMIN"),
                fila("GET", "/api/v1/cuentas/1", null, "CLIENTE", "MESERO", "ADMIN"),
                fila("POST", "/api/v1/cuentas", CUENTA, "CLIENTE", "MESERO"),
                fila("PATCH", "/api/v1/cuentas/1/total", null, "CLIENTE", "MESERO"),
                fila("PATCH", "/api/v1/cuentas/1/pago", PAGO, "MESERO", "ADMIN"),

                // una ruta de la API que no está declarada se niega a todos los autenticados
                fila("GET", "/api/v1/no-declarada", null)
        ).flatMap(SecurityTest::expandirPorRol);
    }

    @ParameterizedTest(name = "{0} {1} como {3} -> {4}")
    @MethodSource("matriz")
    void autorizacionPorRol(String metodo, String ruta, String body, String rol, String esperado) throws Exception {
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(metodo), ruta);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (!ANONIMO.equals(rol)) {
            req.with(user(rol.toLowerCase() + "@restaurante.com").roles(rol));
        }

        int status = mockMvc.perform(req).andReturn().getResponse().getStatus();

        switch (esperado) {
            case "PERMITIDO" -> assertThat(status)
                    .as("%s %s como %s debía pasar la autorización", metodo, ruta, rol)
                    .isNotIn(401, 403);
            case "401" -> assertThat(status).as("%s %s sin sesión", metodo, ruta).isEqualTo(401);
            default -> assertThat(status).as("%s %s como %s", metodo, ruta, rol).isEqualTo(403);
        }
    }

    // ── JWT real de punta a punta (filtro + reglas) ─────────────────────────

    private String tokenDe(String email, String rol) {
        when(usuarioDetailsService.loadUserByUsername(email))
                .thenReturn(User.withUsername(email).password("x").roles(rol).build());
        return "Bearer " + jwtUtil.generateToken(email, "ROLE_" + rol);
    }

    @Test
    @DisplayName("un JWT válido de MESERO permite pagar una cuenta")
    void jwtValido_meseroPuedePagar() throws Exception {
        mockMvc.perform(patch("/api/v1/cuentas/1/pago").header("Authorization", tokenDe("mesero@restaurante.com", "MESERO"))
                        .contentType(MediaType.APPLICATION_JSON).content(PAGO))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un JWT válido de CLIENTE no permite pagar una cuenta")
    void jwtValido_clienteNoPuedePagar() throws Exception {
        mockMvc.perform(patch("/api/v1/cuentas/1/pago").header("Authorization", tokenDe("cliente@restaurante.com", "CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(PAGO))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un token inválido no bloquea la carta pública")
    void tokenInvalido_rutaPublica_responde() throws Exception {
        mockMvc.perform(get("/api/v1/menu").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un token inválido en una ruta protegida responde 401 con formato problem+json")
    void tokenInvalido_rutaProtegida_401() throws Exception {
        mockMvc.perform(get("/api/v1/pedidos").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.instance").value("/api/v1/pedidos"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("un rol sin permiso recibe 403 con formato problem+json")
    void rolSinPermiso_403ProblemJson() throws Exception {
        mockMvc.perform(patch("/api/v1/cuentas/1/pago").header("Authorization", tokenDe("cocina@restaurante.com", "COCINA")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.detail").value("No tiene permisos para realizar esta acción"));
    }

    @Test
    @DisplayName("GET /auth/me devuelve el email y el rol del token")
    void perfil_devuelveEmailYRol() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", tokenDe("cocina@restaurante.com", "COCINA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cocina@restaurante.com"))
                .andExpect(jsonPath("$.rol").value("COCINA"));
    }

    private static Arguments fila(String metodo, String ruta, String body, String... permitidos) {
        return Arguments.of(metodo, ruta, body, Set.copyOf(Arrays.asList(permitidos)));
    }

    @SuppressWarnings("unchecked")
    private static Stream<Arguments> expandirPorRol(Arguments fila) {
        Object[] a = fila.get();
        Set<String> permitidos = (Set<String>) a[3];
        return ROLES.stream().map(rol -> {
            String esperado;
            if (permitidos.contains(PUBLICO) || permitidos.contains(rol)) {
                esperado = "PERMITIDO";
            } else if (ANONIMO.equals(rol)) {
                esperado = "401";
            } else {
                esperado = "403";
            }
            return Arguments.of(a[0], a[1], a[2], rol, esperado);
        });
    }
}
