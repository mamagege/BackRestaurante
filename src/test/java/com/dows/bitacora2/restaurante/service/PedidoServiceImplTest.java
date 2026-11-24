package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.exception.ToppingsExcedidosException;
import com.dows.bitacora2.restaurante.exception.TransicionEstadoInvalidaException;
import com.dows.bitacora2.restaurante.mapper.PedidoEntityMapper;
import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.ItemPedido;
import com.dows.bitacora2.restaurante.model.domain.Mesa;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.domain.Plato;
import com.dows.bitacora2.restaurante.persistence.document.EventoPedidoDocument;
import com.dows.bitacora2.restaurante.persistence.entity.PedidoEntity;
import com.dows.bitacora2.restaurante.repository.EventoPedidoMongoRepository;
import com.dows.bitacora2.restaurante.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceImplTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private PedidoEntityMapper entityMapper;

    @Mock
    private IPlatoService platoService;

    @Mock
    private IMesaService mesaService;

    @Mock
    private EventoPedidoMongoRepository eventoMongoRepo;

    @Mock
    private ICuentaService cuentaService;

    @InjectMocks
    private PedidoServiceImpl pedidoService;

    private Pedido pedidoBase;
    private Plato pizzaPlato;

    @BeforeEach
    void setUp() {
        // Arrange general
        ItemPedido item = new ItemPedido();
        item.setIdPlato(1L);
        item.setCantidad(2);
        item.setMasa("Fina");
        item.setSalsa("Tomate");

        pedidoBase = new Pedido();
        pedidoBase.setId(100L);
        pedidoBase.setIdMesa(1L);
        pedidoBase.setItems(List.of(item));
        pedidoBase.setEstado(EstadoPedido.RECIBIDO);

        pizzaPlato = new Plato();
        pizzaPlato.setId(1L);
        pizzaPlato.setNombre("Pizza Margarita");
        pizzaPlato.setCategoria("Pizza");
        pizzaPlato.setPrecio(15.0);
        pizzaPlato.setDisponible(true);

        Cuenta cuentaAbierta = new Cuenta();
        cuentaAbierta.setId(50L);
        cuentaAbierta.setIdMesa(1L);
        lenient().when(cuentaService.obtenerAbiertaPorMesa(1L)).thenReturn(cuentaAbierta);
    }

    @Test
    void crear_ConMasaYSalsaEnPizza_DebeCrearPedidoCongelandoPrecio() {
        // Arrange
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);
        
        PedidoEntity entityMock = new PedidoEntity();
        entityMock.setId(100L);
        when(entityMapper.toEntity(any(Pedido.class))).thenReturn(entityMock);
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        // Act
        Pedido resultado = pedidoService.crear(pedidoBase);

        // Assert
        assertNotNull(resultado);
        assertEquals(EstadoPedido.RECIBIDO, pedidoBase.getEstado());
        assertEquals(15.0, pedidoBase.getItems().get(0).getPrecioCongelado()); // RN-03 Precio Congelado
        assertEquals("Pizza Margarita", pedidoBase.getItems().get(0).getNombrePlato());
        verify(pedidoRepository, times(1)).save(any());
        // No verificamos el async acÃ¡ directamente por complejidad de hilos en JUnit bÃ¡sico, pero validamos interacciones base.
    }

    @Test
    void crear_PizzaSinMasaNiSalsa_DebeLanzarReglaDeNegocioException() {
        // Arrange
        pedidoBase.getItems().get(0).setMasa(null);
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);

        // Act & Assert (RN-01)
        ReglaDeNegocioException exception = assertThrows(ReglaDeNegocioException.class, () -> {
            pedidoService.crear(pedidoBase);
        });
        assertTrue(exception.getMessage().contains("RN-01"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void crear_PizzaConMasDe5Toppings_DebeLanzarToppingsExcedidosException() {
        // Arrange
        pedidoBase.getItems().get(0).setToppings(Arrays.asList("T1", "T2", "T3", "T4", "T5", "T6"));
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);

        // Act & Assert (RN-02)
        ToppingsExcedidosException exception = assertThrows(ToppingsExcedidosException.class, () -> {
            pedidoService.crear(pedidoBase);
        });
        assertTrue(exception.getMessage().contains("RN-02"));
    }

    // --- LOOP COVERAGE ---

    @Test
    void crear_PedidoSinItems_LoopCeroVeces_DebeCrearPedido() {
        // Arrange
        pedidoBase.setItems(java.util.Collections.emptyList());
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        PedidoEntity entityMock = new PedidoEntity();
        entityMock.setId(101L);
        when(entityMapper.toEntity(any(Pedido.class))).thenReturn(entityMock);
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        // Act
        Pedido resultado = pedidoService.crear(pedidoBase);

        // Assert
        assertNotNull(resultado);
        verify(pedidoRepository).save(any());
    }

    @Test
    void crear_PedidoConMultiplesItems_LoopNVeces_DebeCrearPedido() {
        // Arrange
        ItemPedido item1 = new ItemPedido();
        item1.setIdPlato(1L);
        item1.setMasa("Gruesa");
        item1.setSalsa("Tomate");

        ItemPedido item2 = new ItemPedido();
        item2.setIdPlato(1L);
        item2.setMasa("Fina");
        item2.setSalsa("Pesto");

        pedidoBase.setItems(Arrays.asList(item1, item2));
        
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato); // Ambas son pizza
        
        PedidoEntity entityMock = new PedidoEntity();
        when(entityMapper.toEntity(any(Pedido.class))).thenReturn(entityMock);
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        // Act
        Pedido resultado = pedidoService.crear(pedidoBase);

        // Assert
        assertNotNull(resultado);
        verify(platoService, times(2)).obtenerPorId(1L);
    }

    // --- BRANCH COVERAGE ---

    @Test
    void crear_PlatoNoDisponible_RamaNoDisponible_DebeLanzarExcepcion() {
        // Arrange
        pizzaPlato.setDisponible(false);
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);

        // Act & Assert
        ReglaDeNegocioException exception = assertThrows(ReglaDeNegocioException.class, () -> {
            pedidoService.crear(pedidoBase);
        });
        assertTrue(exception.getMessage().contains("disponible"));
    }

    @Test
    void crear_PlatoNoEsPizzaNiPasta_RamaElse_NoValidaMasaNiSalsa() {
        // Arrange
        Plato bebida = new Plato();
        bebida.setId(2L);
        bebida.setNombre("Gaseosa");
        bebida.setCategoria("Bebida");
        bebida.setPrecio(5.0);
        bebida.setDisponible(true);

        ItemPedido itemBebida = new ItemPedido();
        itemBebida.setIdPlato(2L);
        itemBebida.setCantidad(1);
        pedidoBase.setItems(List.of(itemBebida));

        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(2L)).thenReturn(bebida);
        
        PedidoEntity entityMock = new PedidoEntity();
        when(entityMapper.toEntity(any(Pedido.class))).thenReturn(entityMock);
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        // Act
        Pedido resultado = pedidoService.crear(pedidoBase);

        // Assert
        assertNotNull(resultado);
        verify(pedidoRepository).save(any());
    }

    @Test
    void crear_PastaConSalsaVacia_RamaEmptySalsa_DebeLanzarExcepcion() {
        // Arrange
        pizzaPlato.setCategoria("Pasta");
        pedidoBase.getItems().get(0).setSalsa("   "); // Blank
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);

        // Act & Assert
        ReglaDeNegocioException exception = assertThrows(ReglaDeNegocioException.class, () -> {
            pedidoService.crear(pedidoBase);
        });
        assertTrue(exception.getMessage().contains("RN-01"));
    }

    @Test
    void crear_PizzaExactamente5Toppings_RamaBoundary_DebeCrearPedido() {
        // Arrange
        pedidoBase.getItems().get(0).setToppings(Arrays.asList("T1", "T2", "T3", "T4", "T5"));
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);
        
        PedidoEntity entityMock = new PedidoEntity();
        when(entityMapper.toEntity(any(Pedido.class))).thenReturn(entityMock);
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        // Act
        Pedido resultado = pedidoService.crear(pedidoBase);

        // Assert
        assertNotNull(resultado);
        verify(pedidoRepository).save(any());
    }

    @Test
    void actualizar_PedidoEnPreparacion_DebeLanzarReglaDeNegocioException() {
        // Arrange
        PedidoEntity entityMock = new PedidoEntity();
        entityMock.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(entityMock));

        // Act & Assert (RN-04)
        ReglaDeNegocioException exception = assertThrows(ReglaDeNegocioException.class, () -> {
            pedidoService.actualizar(100L, pedidoBase);
        });
        assertTrue(exception.getMessage().contains("RN-04"));
    }

    @Test
    void cambiarEstado_DeRecibidoAEnPreparacion_DebeActualizarEstadoExitosamente() {
        // Arrange
        PedidoEntity entityMock = new PedidoEntity();
        entityMock.setEstado(EstadoPedido.RECIBIDO);
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(entityMock));
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        // Act
        Pedido resultado = pedidoService.cambiarEstado(100L, "EN_PREPARACION");

        // Assert
        assertNotNull(resultado);
        assertEquals(EstadoPedido.EN_PREPARACION, entityMock.getEstado());
        verify(pedidoRepository, times(1)).save(entityMock); // la misma entidad gestionada, sin re-mapear
        verify(entityMapper, never()).toEntity(any());
    }

    @Test
    void cambiarEstado_DeRecibidoAListo_DebeLanzarTransicionEstadoInvalidaException() {
        // Arrange
        PedidoEntity entityMock = new PedidoEntity();
        entityMock.setEstado(EstadoPedido.RECIBIDO);
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(entityMock));

        // Act & Assert
        assertThrows(TransicionEstadoInvalidaException.class, () -> {
            pedidoService.cambiarEstado(100L, "LISTO");
        });
    }

    @Test
    void crear_AsociaElPedidoALaCuentaAbiertaDeLaMesa() {
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);
        PedidoEntity entityMock = new PedidoEntity();
        when(entityMapper.toEntity(any(Pedido.class))).thenReturn(entityMock);
        when(pedidoRepository.save(entityMock)).thenReturn(entityMock);
        when(entityMapper.toDomain(entityMock)).thenReturn(pedidoBase);

        pedidoService.crear(pedidoBase);

        assertEquals(50L, pedidoBase.getIdCuenta());
    }

    @Test
    void crear_MesaSinCuentaAbierta_DebeLanzarReglaDeNegocioYNoGuardar() {
        when(mesaService.obtenerPorId(1L)).thenReturn(new Mesa());
        when(cuentaService.obtenerAbiertaPorMesa(1L))
                .thenThrow(new ReglaDeNegocioException("La mesa con id=1 no tiene una cuenta abierta."));

        assertThrows(ReglaDeNegocioException.class, () -> pedidoService.crear(pedidoBase));
        verify(pedidoRepository, never()).save(any());
        verifyNoInteractions(platoService);
    }

    @Test
    void actualizar_ReemplazaLosItemsSobreLaEntidadGestionada() {
        PedidoEntity entity = new PedidoEntity();
        entity.setEstado(EstadoPedido.RECIBIDO);
        entity.getItems().add(new com.dows.bitacora2.restaurante.persistence.entity.ItemPedidoEntity());
        com.dows.bitacora2.restaurante.persistence.entity.ItemPedidoEntity nuevoItem =
                new com.dows.bitacora2.restaurante.persistence.entity.ItemPedidoEntity();
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(entity));
        when(platoService.obtenerPorId(1L)).thenReturn(pizzaPlato);
        when(entityMapper.toItemEntity(any())).thenReturn(nuevoItem);
        when(pedidoRepository.save(entity)).thenReturn(entity);
        when(entityMapper.toDomain(entity)).thenReturn(pedidoBase);

        pedidoService.actualizar(100L, pedidoBase);

        assertEquals(List.of(nuevoItem), entity.getItems());
        verify(entityMapper, never()).toEntity(any());
    }

    @Test
    void eliminar_PedidoRecibido_MarcaCanceladoSobreLaEntidad() {
        PedidoEntity entity = new PedidoEntity();
        entity.setEstado(EstadoPedido.RECIBIDO);
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(entity));

        pedidoService.eliminar(100L);

        assertEquals(EstadoPedido.CANCELADO, entity.getEstado());
        verify(pedidoRepository).save(entity);
    }

    @Test
    void eliminar_PedidoEnPreparacion_DebeLanzarReglaDeNegocio() {
        PedidoEntity entity = new PedidoEntity();
        entity.setEstado(EstadoPedido.EN_PREPARACION);
        when(pedidoRepository.findById(100L)).thenReturn(Optional.of(entity));

        assertThrows(ReglaDeNegocioException.class, () -> pedidoService.eliminar(100L));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void cambiarEstado_EstadoInexistente_DebeLanzarEstadoInvalidoSinConsultar() {
        assertThrows(com.dows.bitacora2.restaurante.exception.EstadoInvalidoException.class,
                () -> pedidoService.cambiarEstado(100L, "VOLANDO"));
        verifyNoInteractions(pedidoRepository);
    }
}
