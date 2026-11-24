package com.dows.bitacora2.restaurante.integracion;

import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.domain.Plato;
import com.dows.bitacora2.restaurante.model.domain.ResumenVentas;
import com.dows.bitacora2.restaurante.service.ICuentaService;
import com.dows.bitacora2.restaurante.service.IMesaService;
import com.dows.bitacora2.restaurante.service.IPedidoService;
import com.dows.bitacora2.restaurante.service.IPlatoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** RF6 contra PostgreSQL real: consulta por rango de fecha, sin cancelados, agrupado por plato y usuario. */
class ResumenVentasIntegracionTest extends IntegracionBase {

    @Autowired private IMesaService mesaService;
    @Autowired private ICuentaService cuentaService;
    @Autowired private IPlatoService platoService;
    @Autowired private IPedidoService pedidoService;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("el resumen de hoy suma cantidades e ingresos por plato y por quien registró el pedido")
    void resumenDelDia() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String mesero = "mesero-" + sufijo + "@restaurante.com";
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                mesero, null, AuthorityUtils.createAuthorityList("ROLE_MESERO")));

        Mesa mesa = new Mesa();
        mesa.setNumero(Math.abs(sufijo.hashCode() % 100000) + 1000);
        mesa.setCapacidad(4);
        mesa = mesaService.crear(mesa);
        Cuenta cuenta = new Cuenta();
        cuenta.setIdMesa(mesa.getId());
        cuentaService.crear(cuenta);
        Plato tiramisu = platoService.crear(new Plato(null, "Tiramisú " + sufijo, 15000.0, "Postre", true, null));

        Pedido p1 = pedidoService.crear(pedido(mesa.getId(), tiramisu.getId(), 2));
        pedidoService.crear(pedido(mesa.getId(), tiramisu.getId(), 1));
        Pedido cancelado = pedidoService.crear(pedido(mesa.getId(), tiramisu.getId(), 10));
        pedidoService.eliminar(cancelado.getId());
        assertNotNull(p1.getId());

        ResumenVentas r = pedidoService.resumenDelDia(LocalDate.now());

        ResumenVentas.VentaPlato venta = r.porPlato().stream()
                .filter(v -> v.plato().equals(tiramisu.getNombre())).findFirst().orElseThrow();
        assertEquals(3, venta.cantidad(), "2 + 1, sin los 10 del pedido cancelado");
        assertEquals(45000.0, venta.ingresos());
        ResumenVentas.VentaUsuario porMesero = r.porUsuario().stream()
                .filter(v -> v.usuario().equals(mesero)).findFirst().orElseThrow();
        assertEquals(2, porMesero.pedidos());
        assertEquals(45000.0, porMesero.ingresos());

        assertTrue(pedidoService.resumenDelDia(LocalDate.now().minusDays(1)).porPlato().stream()
                .noneMatch(v -> v.plato().equals(tiramisu.getNombre())), "ayer no hubo ventas de este plato");
    }

    private static Pedido pedido(Long idMesa, Long idPlato, int cantidad) {
        ItemPedido item = new ItemPedido();
        item.setIdPlato(idPlato);
        item.setCantidad(cantidad);
        Pedido pedido = new Pedido();
        pedido.setIdMesa(idMesa);
        pedido.setItems(new ArrayList<>(List.of(item)));
        return pedido;
    }
}
