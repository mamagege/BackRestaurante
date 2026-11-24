package com.dows.bitacora2.restaurante.auditoria;

import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.PedidoEstadoCambiado;
import com.dows.bitacora2.restaurante.persistence.document.EventoPedidoDocument;
import com.dows.bitacora2.restaurante.repository.EventoPedidoMongoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditoriaPedidoListenerTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 24, 20, 0);

    private final EventoPedidoMongoRepository repositorio = mock(EventoPedidoMongoRepository.class);
    private final AuditoriaPedidoListener listener = new AuditoriaPedidoListener(repositorio);

    @Test
    @DisplayName("guarda el evento con usuario, estados y fecha")
    void registrar_guardaDocumentoCompleto() {
        listener.registrar(new PedidoEstadoCambiado(7L, EstadoPedido.RECIBIDO, EstadoPedido.EN_PREPARACION,
                "cocina@restaurante.com", FECHA, "Cambio de estado"));

        ArgumentCaptor<EventoPedidoDocument> captor = ArgumentCaptor.forClass(EventoPedidoDocument.class);
        verify(repositorio).save(captor.capture());
        EventoPedidoDocument doc = captor.getValue();
        assertEquals(7L, doc.getPedidoId());
        assertEquals("RECIBIDO", doc.getEstadoAnterior());
        assertEquals("EN_PREPARACION", doc.getEstadoNuevo());
        assertEquals("cocina@restaurante.com", doc.getUsuario());
        assertEquals(FECHA, doc.getFechaEvento());
    }

    @Test
    @DisplayName("al crear el pedido no hay estado anterior")
    void registrar_creacionSinEstadoAnterior() {
        listener.registrar(new PedidoEstadoCambiado(7L, null, EstadoPedido.RECIBIDO, "sistema", FECHA, "Pedido creado"));

        ArgumentCaptor<EventoPedidoDocument> captor = ArgumentCaptor.forClass(EventoPedidoDocument.class);
        verify(repositorio).save(captor.capture());
        assertNull(captor.getValue().getEstadoAnterior());
    }

    @Test
    @DisplayName("si Mongo falla, el error se registra en el log y no se propaga")
    void registrar_mongoFalla_noPropaga() {
        when(repositorio.save(any())).thenThrow(new IllegalStateException("Mongo caído"));

        assertDoesNotThrow(() -> listener.registrar(new PedidoEstadoCambiado(7L, null, EstadoPedido.RECIBIDO,
                "sistema", FECHA, "Pedido creado")));
    }
}
