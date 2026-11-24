package com.dows.bitacora2.restaurante.integracion;

import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.domain.Plato;
import com.dows.bitacora2.restaurante.persistence.document.EventoPedidoDocument;
import com.dows.bitacora2.restaurante.repository.EventoPedidoMongoRepository;
import com.dows.bitacora2.restaurante.service.ICuentaService;
import com.dows.bitacora2.restaurante.service.IMesaService;
import com.dows.bitacora2.restaurante.service.IPedidoService;
import com.dows.bitacora2.restaurante.service.IPlatoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** RN-05 contra MongoDB real: cada cambio confirmado queda con usuario y hora; un rollback no deja rastro. */
class AuditoriaIntegracionTest extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(500);

    @Autowired private IMesaService mesaService;
    @Autowired private ICuentaService cuentaService;
    @Autowired private IPlatoService platoService;
    @Autowired private IPedidoService pedidoService;
    @Autowired private EventoPedidoMongoRepository eventos;
    @Autowired private TransactionTemplate transactionTemplate;

    /** Cada contexto recrea las tablas (los ids se repiten) pero Mongo persiste entre clases de prueba. */
    @BeforeEach
    void limpiarAuditoria() {
        eventos.deleteAll();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private Pedido pedidoNuevo() {
        Mesa mesa = new Mesa();
        mesa.setNumero(SECUENCIA.incrementAndGet());
        mesa.setCapacidad(2);
        mesa = mesaService.crear(mesa);
        Cuenta cuenta = new Cuenta();
        cuenta.setIdMesa(mesa.getId());
        cuentaService.crear(cuenta);
        Plato agua = platoService.crear(new Plato(null, "Agua " + SECUENCIA.incrementAndGet(), 4000.0, "Bebida", true, null));

        ItemPedido item = new ItemPedido();
        item.setIdPlato(agua.getId());
        item.setCantidad(1);
        Pedido pedido = new Pedido();
        pedido.setIdMesa(mesa.getId());
        pedido.setItems(new ArrayList<>(List.of(item)));
        return pedidoService.crear(pedido);
    }

    /** Espera a que el listener asíncrono escriba (máx. 5 s). */
    private List<EventoPedidoDocument> eventosDe(Long pedidoId, int esperados) throws InterruptedException {
        List<EventoPedidoDocument> lista = List.of();
        for (int i = 0; i < 50 && lista.size() < esperados; i++) {
            Thread.sleep(100);
            lista = eventos.findByPedidoIdOrderByFechaEventoDesc(pedidoId);
        }
        return lista;
    }

    @Test
    @DisplayName("cada cambio confirmado queda auditado con el usuario que lo hizo")
    void cambioConfirmado_quedaAuditadoConUsuario() throws Exception {
        autenticarComo("mesero@restaurante.com");
        Pedido pedido = pedidoNuevo();
        autenticarComo("cocina@restaurante.com");
        pedidoService.cambiarEstado(pedido.getId(), "EN_PREPARACION");

        List<EventoPedidoDocument> auditoria = eventosDe(pedido.getId(), 2);

        assertEquals(2, auditoria.size());
        EventoPedidoDocument ultimo = auditoria.get(0);
        assertEquals("EN_PREPARACION", ultimo.getEstadoNuevo());
        assertEquals("RECIBIDO", ultimo.getEstadoAnterior());
        assertEquals("cocina@restaurante.com", ultimo.getUsuario());
        assertNotNull(ultimo.getFechaEvento());
        assertEquals("mesero@restaurante.com", auditoria.get(1).getUsuario());
    }

    @Test
    @DisplayName("un cambio revertido por rollback no deja evento de auditoría")
    void cambioRevertido_noSeAudita() throws Exception {
        Pedido pedido = pedidoNuevo();
        eventosDe(pedido.getId(), 1); // evento de creación

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(tx -> {
            pedidoService.cambiarEstado(pedido.getId(), "EN_PREPARACION");
            throw new IllegalStateException("falla posterior dentro de la misma transacción");
        }));

        Thread.sleep(1000);
        List<EventoPedidoDocument> auditoria = eventos.findByPedidoIdOrderByFechaEventoDesc(pedido.getId());
        assertEquals(1, auditoria.size(), "solo debe existir el evento de creación");
        assertEquals("RECIBIDO", auditoria.get(0).getEstadoNuevo());
    }
}
