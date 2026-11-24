package com.dows.bitacora2.restaurante.model.domain;

import java.time.LocalDateTime;

/**
 * Evento de dominio: el estado de un pedido cambió (RN-05 / RNF5).
 * Lleva quién hizo el cambio y cuándo; {@code estadoAnterior} es nulo al crear el pedido.
 */
public record PedidoEstadoCambiado(
        Long pedidoId,
        EstadoPedido estadoAnterior,
        EstadoPedido estadoNuevo,
        String usuario,
        LocalDateTime fecha,
        String descripcion
) {}
