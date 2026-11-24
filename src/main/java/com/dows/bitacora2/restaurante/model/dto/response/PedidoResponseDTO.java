package com.dows.bitacora2.restaurante.model.dto.response;
import java.time.LocalDateTime;
import java.util.List;
public record PedidoResponseDTO(
    Long id,
    Long idMesa,
    Long idCuenta,
    String estado,
    LocalDateTime timestamp,
    String creadoPor,
    Double total,
    List<ItemPedidoResponseDTO> items
) {}