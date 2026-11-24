package com.dows.bitacora2.restaurante.auditoria;

import com.dows.bitacora2.restaurante.config.AsyncConfig;
import com.dows.bitacora2.restaurante.model.domain.PedidoEstadoCambiado;
import com.dows.bitacora2.restaurante.persistence.document.EventoPedidoDocument;
import com.dows.bitacora2.restaurante.repository.EventoPedidoMongoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Registra en MongoDB cada cambio de estado de un pedido (RN-05).
 * <ul>
 *   <li>AFTER_COMMIT: solo se audita lo que realmente quedó guardado; un rollback no deja evento.</li>
 *   <li>Asíncrono en un pool propio: la escritura en Mongo no alarga la respuesta al cliente.</li>
 *   <li>Si Mongo falla, el error queda en el log con todos los datos del evento.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditoriaPedidoListener {

    private final EventoPedidoMongoRepository repositorio;

    @Async(AsyncConfig.AUDITORIA_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void registrar(PedidoEstadoCambiado evento) {
        try {
            repositorio.save(new EventoPedidoDocument(
                    evento.pedidoId(),
                    evento.estadoAnterior() != null ? evento.estadoAnterior().name() : null,
                    evento.estadoNuevo().name(),
                    evento.fecha(),
                    evento.descripcion(),
                    evento.usuario()));
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el evento de auditoría {}: {}", evento, e.getMessage(), e);
        }
    }
}
