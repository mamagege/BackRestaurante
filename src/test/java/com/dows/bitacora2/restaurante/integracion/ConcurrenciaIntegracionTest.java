package com.dows.bitacora2.restaurante.integracion;

import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoCuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.domain.Plato;
import com.dows.bitacora2.restaurante.repository.CuentaRepository;
import com.dows.bitacora2.restaurante.repository.MesaRepository;
import com.dows.bitacora2.restaurante.repository.PedidoRepository;
import com.dows.bitacora2.restaurante.service.ICuentaService;
import com.dows.bitacora2.restaurante.service.IMesaService;
import com.dows.bitacora2.restaurante.service.IPedidoService;
import com.dows.bitacora2.restaurante.service.IPlatoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * Carreras reales contra PostgreSQL (RNF10): sin bloqueo optimista, la segunda escritura
 * pisaba en silencio a la primera y rompía RN-04 y RN-06.
 */
class ConcurrenciaIntegracionTest extends IntegracionBase {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(100);

    @MockitoSpyBean private IMesaService mesaService;
    @Autowired private ICuentaService cuentaService;
    @Autowired private IPlatoService platoService;
    @Autowired private IPedidoService pedidoService;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private CuentaRepository cuentaRepository;

    private Mesa mesaNueva() {
        Mesa mesa = new Mesa();
        mesa.setNumero(SECUENCIA.incrementAndGet());
        mesa.setCapacidad(4);
        return mesaService.crear(mesa);
    }

    private Pedido pedidoRecibido() {
        Mesa mesa = mesaNueva();
        Cuenta cuenta = new Cuenta();
        cuenta.setIdMesa(mesa.getId());
        cuentaService.crear(cuenta);
        Plato limonada = platoService.crear(new Plato(null, "Limonada " + SECUENCIA.incrementAndGet(),
                8000.0, "Bebida", true, null));

        ItemPedido item = new ItemPedido();
        item.setIdPlato(limonada.getId());
        item.setCantidad(1);
        Pedido pedido = new Pedido();
        pedido.setIdMesa(mesa.getId());
        pedido.setItems(new ArrayList<>(List.of(item)));
        return pedidoService.crear(pedido);
    }

    private static Pedido conCantidad(Pedido base, int cantidad) {
        ItemPedido item = new ItemPedido();
        item.setIdPlato(base.getItems().get(0).getIdPlato());
        item.setCantidad(cantidad);
        Pedido cambios = new Pedido();
        cambios.setItems(new ArrayList<>(List.of(item)));
        return cambios;
    }

    @Test
    @DisplayName("RN-04: si cocina toma el pedido mientras el cliente lo edita, la edición falla y gana cocina")
    void edicionConcurrenteConCocina_ganaCocina() throws Exception {
        Pedido pedido = pedidoRecibido();

        Exception error = assertThrows(Exception.class, () -> transactionTemplate.executeWithoutResult(tx -> {
            // El cliente lee el pedido (RECIBIDO, versión 0) dentro de su transacción...
            pedidoRepository.findById(pedido.getId()).orElseThrow();

            // ...en ese instante cocina lo pasa a EN_PREPARACION y confirma
            esperar(CompletableFuture.runAsync(() -> pedidoService.cambiarEstado(pedido.getId(), "EN_PREPARACION")));

            // ...y el cliente intenta guardar su edición con la versión que leyó
            pedidoService.actualizar(pedido.getId(), conCantidad(pedido, 5));
        }));

        assertInstanceOf(ObjectOptimisticLockingFailureException.class, error);
        Pedido finalEnBd = pedidoService.obtenerPorId(pedido.getId());
        assertEquals(EstadoPedido.EN_PREPARACION, finalEnBd.getEstado());
        assertEquals(1, finalEnBd.getItems().get(0).getCantidad(), "la edición del cliente no debe aplicarse");
    }

    @Test
    @DisplayName("RN-06: dos aperturas de cuenta simultáneas en la misma mesa: solo una gana")
    void aperturaConcurrenteDeCuenta_soloUnaGana() throws Exception {
        Mesa mesa = mesaNueva();

        // Ambos meseros pasan la verificación "no hay cuenta abierta" y solo entonces ocupan la mesa
        CyclicBarrier ambosVerificaron = new CyclicBarrier(2);
        doAnswer(inv -> {
            ambosVerificaron.await(10, TimeUnit.SECONDS);
            return inv.callRealMethod();
        }).when(mesaService).abrirCuenta(eq(mesa.getId()));

        CompletableFuture<Cuenta> mesero1 = CompletableFuture.supplyAsync(() -> abrirCuenta(mesa));
        CompletableFuture<Cuenta> mesero2 = CompletableFuture.supplyAsync(() -> abrirCuenta(mesa));

        List<Throwable> errores = new ArrayList<>();
        int exitos = 0;
        for (CompletableFuture<Cuenta> intento : List.of(mesero1, mesero2)) {
            try {
                intento.get(30, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException e) {
                errores.add(e.getCause());
            }
        }

        assertEquals(1, exitos, "exactamente una apertura debe confirmarse");
        assertEquals(1, errores.size());
        // Según quién confirme primero, el perdedor falla por versión obsoleta de la mesa (409)
        // o porque ya la encuentra con cuenta abierta (409). Ambos defienden RN-06.
        assertTrue(errores.get(0) instanceof ObjectOptimisticLockingFailureException
                        || errores.get(0) instanceof ConflictoException,
                "error inesperado: " + errores.get(0));
        long abiertas = cuentaRepository.findAll().stream()
                .filter(c -> c.getIdMesa().equals(mesa.getId()) && c.getEstado() == EstadoCuenta.ABIERTA)
                .count();
        assertEquals(1, abiertas, "la mesa no puede quedar con dos cuentas abiertas");
        assertTrue(mesaRepository.findById(mesa.getId()).orElseThrow().getCuentaAbierta());
    }

    private Cuenta abrirCuenta(Mesa mesa) {
        Cuenta cuenta = new Cuenta();
        cuenta.setIdMesa(mesa.getId());
        return cuentaService.crear(cuenta);
    }

    private static void esperar(CompletableFuture<?> tarea) {
        try {
            tarea.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
