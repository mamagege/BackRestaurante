package com.dows.bitacora2.restaurante.controller;

import com.dows.bitacora2.restaurante.exception.ToppingsExcedidosException;
import com.dows.bitacora2.restaurante.mapper.PedidoMapper;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.security.JwtAuthFilter;
import com.dows.bitacora2.restaurante.security.JwtUtil;
import com.dows.bitacora2.restaurante.service.IPedidoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba web de punta a punta (JSON -> DTO -> mapper real -> servicio): es la capa que
 * dejaba pasar que masa, salsa y toppings se perdieran antes de llegar al dominio.
 */
@WebMvcTest(controllers = PedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PedidoControllerTest.MapperReal.class)
class PedidoControllerTest {

    static class MapperReal {
        @org.springframework.context.annotation.Bean
        PedidoMapper pedidoMapper() {
            return Mappers.getMapper(PedidoMapper.class);
        }
    }

    private static final String PIZZA = """
            {"idMesa": 3, "items": [{
                "idPlato": 7, "cantidad": 1, "masa": "Delgada", "salsa": "Tomate",
                "toppings": ["Albahaca", "Mozzarella"], "notas": "Bien horneada"
            }]}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IPedidoService pedidoService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;
    @MockitoBean private JwtUtil jwtUtil;

    @Test
    @DisplayName("la personalización enviada en el JSON llega intacta al servicio")
    void crear_personalizacionLlegaAlServicio() throws Exception {
        when(pedidoService.crear(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content(PIZZA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].masa").value("Delgada"))
                .andExpect(jsonPath("$.items[0].toppings.length()").value(2));

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoService).crear(captor.capture());
        ItemPedido item = captor.getValue().getItems().get(0);
        assertEquals("Delgada", item.getMasa());
        assertEquals("Tomate", item.getSalsa());
        assertEquals(List.of("Albahaca", "Mozzarella"), item.getToppings());
        assertEquals("Bien horneada", item.getNotas());
    }

    @Test
    @DisplayName("una regla de personalización incumplida responde 422")
    void crear_reglaIncumplida_422() throws Exception {
        when(pedidoService.crear(any())).thenThrow(new ToppingsExcedidosException("RN-02: máximo 5 toppings"));

        mockMvc.perform(post("/api/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content(PIZZA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_DE_NEGOCIO"));
    }

    @Test
    @DisplayName("un topping vacío es un error de validación (400), no llega al servicio")
    void crear_toppingVacio_400() throws Exception {
        String json = PIZZA.replace("\"Mozzarella\"", "\"\"");

        mockMvc.perform(post("/api/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        verifyNoInteractions(pedidoService);
    }
}
