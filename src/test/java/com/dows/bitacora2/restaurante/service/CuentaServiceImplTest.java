package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.mapper.CuentaEntityMapper;
import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoCuenta;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.model.domain.MetodoPago;
import com.dows.bitacora2.restaurante.persistence.entity.CuentaEntity;
import com.dows.bitacora2.restaurante.persistence.entity.PedidoEntity;
import com.dows.bitacora2.restaurante.repository.CuentaRepository;
import com.dows.bitacora2.restaurante.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CuentaEntityMapper entityMapper;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private IMesaService mesaService;

    @Mock
    private IPasarelaPago pasarelaPago;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    private Cuenta cuenta;
    private CuentaEntity cuentaEntity;

    @BeforeEach
    void setUp() {
        cuenta = new Cuenta();
        cuenta.setId(1L);
        cuenta.setIdMesa(2L);
        cuenta.setEstado(EstadoCuenta.ABIERTA);

        cuentaEntity = new CuentaEntity();
        cuentaEntity.setId(1L);
        cuentaEntity.setIdMesa(2L);
    }

    @Test
    void crear_MesaValida_DebeAbrirCuenta() {
        Mesa mesa = new Mesa();
        mesa.setId(2L);
        mesa.setCuentaAbierta(false);

        when(mesaService.obtenerPorId(2L)).thenReturn(mesa);
        when(entityMapper.toEntity(cuenta)).thenReturn(cuentaEntity);
        when(cuentaRepository.save(cuentaEntity)).thenReturn(cuentaEntity);
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);

        Cuenta result = cuentaService.crear(cuenta);

        assertNotNull(result);
        assertEquals(EstadoCuenta.ABIERTA, result.getEstado());
        verify(mesaService).abrirCuenta(2L);
        verify(cuentaRepository).save(any());
    }

    @Test
    void pagar_CuentaAbiertaYPedidosEntregados_DebePagarYLiberarMesa() {
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);
        when(pedidoRepository.findByIdCuenta(1L)).thenReturn(java.util.Collections.emptyList());
        when(cuentaRepository.save(cuentaEntity)).thenReturn(cuentaEntity);

        Cuenta result = cuentaService.pagar(1L, MetodoPago.EFECTIVO, 0.0);

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        verify(mesaService).cerrarCuenta(2L);
    }

    @Test
    void pagar_CuentaYaCerrada_DebeLanzarExcepcion() {
        cuenta.setEstado(EstadoCuenta.CERRADA);
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);

        ReglaDeNegocioException exception = assertThrows(ReglaDeNegocioException.class, () -> {
            cuentaService.pagar(1L, MetodoPago.EFECTIVO, 1_000_000.0);
        });

        assertTrue(exception.getMessage().contains("pagada"));
    }

    @Test
    void pagar_CuentaConPedidosPendientes_DebeLanzarExcepcion() {
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);
        
        PedidoEntity pedidoPendiente = new PedidoEntity();
        pedidoPendiente.setEstado(com.dows.bitacora2.restaurante.model.domain.EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findByIdCuenta(1L)).thenReturn(java.util.Collections.singletonList(pedidoPendiente));

        ReglaDeNegocioException exception = assertThrows(ReglaDeNegocioException.class, () -> {
            cuentaService.pagar(1L, MetodoPago.EFECTIVO, 1_000_000.0);
        });

        assertTrue(exception.getMessage().contains("preparación"));
        verify(mesaService, never()).cerrarCuenta(any());
    }

    private static PedidoEntity pedido(com.dows.bitacora2.restaurante.model.domain.EstadoPedido estado,
                                       double precio, int cantidad) {
        com.dows.bitacora2.restaurante.persistence.entity.ItemPedidoEntity item =
                new com.dows.bitacora2.restaurante.persistence.entity.ItemPedidoEntity();
        item.setPrecioCongelado(precio);
        item.setCantidad(cantidad);
        PedidoEntity p = new PedidoEntity();
        p.setEstado(estado);
        p.setItems(java.util.List.of(item));
        return p;
    }

    @Test
    void actualizarTotal_SumaSoloPedidosDeLaCuentaYExcluyeCancelados() {
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);
        when(pedidoRepository.findByIdCuenta(1L)).thenReturn(java.util.List.of(
                pedido(com.dows.bitacora2.restaurante.model.domain.EstadoPedido.ENTREGADO, 30000, 2),
                pedido(com.dows.bitacora2.restaurante.model.domain.EstadoPedido.CANCELADO, 99999, 1)));
        when(cuentaRepository.save(cuentaEntity)).thenReturn(cuentaEntity);

        cuentaService.actualizarTotal(1L);

        assertEquals(60000.0, cuenta.getTotal());
        verify(entityMapper).actualizarEntity(cuenta, cuentaEntity);
        // regresión: el total ya no se calcula con el historial de la mesa
        verify(pedidoRepository).findByIdCuenta(1L);
        verifyNoMoreInteractions(pedidoRepository);
    }

    @Test
    void pagar_CongelaElTotalConLosPedidosDeLaCuenta() {
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);
        when(pedidoRepository.findByIdCuenta(1L)).thenReturn(java.util.List.of(
                pedido(com.dows.bitacora2.restaurante.model.domain.EstadoPedido.ENTREGADO, 12500.5, 2)));
        when(cuentaRepository.save(cuentaEntity)).thenReturn(cuentaEntity);

        cuentaService.pagar(1L, MetodoPago.EFECTIVO, 30000.0);

        assertEquals(25001.0, cuenta.getTotal());
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
    }

    @Test
    void crear_MesaConCuentaAbierta_DebeLanzarConflicto() {
        when(mesaService.obtenerPorId(2L)).thenReturn(new Mesa());
        when(cuentaRepository.existsByIdMesaAndEstado(2L, EstadoCuenta.ABIERTA)).thenReturn(true);

        assertThrows(com.dows.bitacora2.restaurante.exception.ConflictoException.class, () -> cuentaService.crear(cuenta));
        verify(mesaService, never()).abrirCuenta(any());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void obtenerAbiertaPorMesa_SinCuentaAbierta_DebeLanzarReglaDeNegocio() {
        when(cuentaRepository.findFirstByIdMesaAndEstado(2L, EstadoCuenta.ABIERTA)).thenReturn(Optional.empty());

        ReglaDeNegocioException ex = assertThrows(ReglaDeNegocioException.class,
                () -> cuentaService.obtenerAbiertaPorMesa(2L));
        assertTrue(ex.getMessage().contains("no tiene una cuenta abierta"));
    }

    @Test
    void obtenerAbiertaPorMesa_ConCuentaAbierta_LaRetorna() {
        when(cuentaRepository.findFirstByIdMesaAndEstado(2L, EstadoCuenta.ABIERTA)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);

        assertEquals(1L, cuentaService.obtenerAbiertaPorMesa(2L).getId());
    }

    private void cuentaConPedidoEntregado(double precio) {
        when(cuentaRepository.findById(1L)).thenReturn(Optional.of(cuentaEntity));
        when(entityMapper.toDomain(cuentaEntity)).thenReturn(cuenta);
        when(pedidoRepository.findByIdCuenta(1L)).thenReturn(java.util.List.of(
                pedido(com.dows.bitacora2.restaurante.model.domain.EstadoPedido.ENTREGADO, precio, 1)));
    }

    @Test
    void pagar_Efectivo_NoUsaPasarelaYCalculaCambio() {
        cuentaConPedidoEntregado(58000);
        when(cuentaRepository.save(cuentaEntity)).thenReturn(cuentaEntity);

        cuentaService.pagar(1L, MetodoPago.EFECTIVO, 60000.0);

        assertEquals(2000.0, cuenta.getCambio());
        assertNotNull(cuenta.getFechaCierre());
        verifyNoInteractions(pasarelaPago);
        verify(mesaService).cerrarCuenta(2L);
    }

    @Test
    void pagar_Tarjeta_ProcesaPorPasarelaYGuardaReferencia() {
        cuentaConPedidoEntregado(58000);
        when(pasarelaPago.procesar(1L, MetodoPago.TARJETA, 58000.0))
                .thenReturn(IPasarelaPago.ResultadoPago.aprobado("TX-123"));
        when(cuentaRepository.save(cuentaEntity)).thenReturn(cuentaEntity);

        cuentaService.pagar(1L, MetodoPago.TARJETA, 58000.0);

        assertEquals("TX-123", cuenta.getReferenciaPago());
        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
    }

    @Test
    void pagar_PasarelaRechaza_CuentaSigueAbiertaYMesaOcupada() {
        cuentaConPedidoEntregado(58000);
        when(pasarelaPago.procesar(1L, MetodoPago.TARJETA, 58000.0))
                .thenReturn(IPasarelaPago.ResultadoPago.rechazado("Fondos insuficientes"));

        ReglaDeNegocioException ex = assertThrows(ReglaDeNegocioException.class,
                () -> cuentaService.pagar(1L, MetodoPago.TARJETA, 58000.0));

        assertTrue(ex.getMessage().contains("Fondos insuficientes"));
        assertEquals(EstadoCuenta.ABIERTA, cuenta.getEstado());
        verify(mesaService, never()).cerrarCuenta(any());
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void pagar_MontoInsuficiente_NoCobraNiCierra() {
        cuentaConPedidoEntregado(58000);

        assertThrows(ReglaDeNegocioException.class, () -> cuentaService.pagar(1L, MetodoPago.TARJETA, 50000.0));

        verifyNoInteractions(pasarelaPago);
        verify(mesaService, never()).cerrarCuenta(any());
    }
}
