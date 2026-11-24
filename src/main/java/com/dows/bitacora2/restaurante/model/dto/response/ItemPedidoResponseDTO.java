package com.dows.bitacora2.restaurante.model.dto.response;

import java.util.List;

public record ItemPedidoResponseDTO(
    Long idPlato,
    String nombrePlato,
    Integer cantidad,
    Double precioUnitario,
    Double subtotal,
    String masa,
    String salsa,
    List<String> salsasAdicionales,
    List<String> proteinas,
    List<String> toppings,
    String notas
) {}
