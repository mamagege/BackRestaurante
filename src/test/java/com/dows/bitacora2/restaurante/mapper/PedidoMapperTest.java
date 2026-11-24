package com.dows.bitacora2.restaurante.mapper;

import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.dto.request.ItemPedidoRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.request.PedidoRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.response.ItemPedidoResponseDTO;
import com.dows.bitacora2.restaurante.model.dto.response.PedidoResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PedidoMapperTest {

    private final PedidoMapper mapper = Mappers.getMapper(PedidoMapper.class);

    @Test
    @DisplayName("el request conserva toda la personalización del ítem (regresión: masa/salsa/toppings se perdían)")
    void toDomain_conservaPersonalizacion() {
        PedidoRequestDTO dto = new PedidoRequestDTO(3L, List.of(new ItemPedidoRequestDTO(
                7L, 2, "Delgada", "Tomate", List.of("Pesto"), List.of("Pollo"),
                List.of("Albahaca", "Aceitunas"), "Sin cebolla")));

        Pedido pedido = mapper.toDomain(dto);
        ItemPedido item = pedido.getItems().get(0);

        assertEquals(3L, pedido.getIdMesa());
        assertEquals(EstadoPedido.RECIBIDO, pedido.getEstado());
        assertEquals(7L, item.getIdPlato());
        assertEquals(2, item.getCantidad());
        assertEquals("Delgada", item.getMasa());
        assertEquals("Tomate", item.getSalsa());
        assertEquals(List.of("Pesto"), item.getSalsasAdicionales());
        assertEquals(List.of("Pollo"), item.getProteinas());
        assertEquals(List.of("Albahaca", "Aceitunas"), item.getToppings());
        assertEquals("Sin cebolla", item.getNotas());
        assertNull(item.getPrecioCongelado(), "el precio lo congela el dominio, nunca el cliente");
    }

    @Test
    @DisplayName("un ítem sin listas produce listas vacías, no nulas")
    void toDomain_listasAusentes_sonVacias() {
        ItemPedido item = mapper.toDomain(new ItemPedidoRequestDTO(1L, 1, null, null, null, null, null, null));
        assertTrue(item.getToppings().isEmpty());
        assertTrue(item.getProteinas().isEmpty());
        assertTrue(item.getSalsasAdicionales().isEmpty());
    }

    @Test
    @DisplayName("la respuesta expone precio unitario, subtotal, total y la personalización")
    void toResponse_exponePreciosYPersonalizacion() {
        ItemPedido item = new ItemPedido(7L, "Margarita", 30000.0, 2, "Delgada", "Tomate", List.of("Albahaca"));
        item.setNotas("Bien horneada");
        Pedido pedido = new Pedido(10L, 3L, List.of(item), EstadoPedido.RECIBIDO, null);

        PedidoResponseDTO r = mapper.toResponse(pedido);
        ItemPedidoResponseDTO ri = r.items().get(0);

        assertEquals(60000.0, r.total());
        assertEquals("RECIBIDO", r.estado());
        assertEquals(30000.0, ri.precioUnitario());
        assertEquals(60000.0, ri.subtotal());
        assertEquals("Delgada", ri.masa());
        assertEquals(List.of("Albahaca"), ri.toppings());
        assertEquals("Bien horneada", ri.notas());
    }
}
