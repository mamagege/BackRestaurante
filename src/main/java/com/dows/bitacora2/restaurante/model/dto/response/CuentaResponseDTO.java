package com.dows.bitacora2.restaurante.model.dto.response;

import java.time.LocalDateTime;

public record CuentaResponseDTO(
    Long id,
    Long idMesa,
    Double total,
    String estado,
    LocalDateTime fechaApertura,
    LocalDateTime fechaCierre,
    String metodoPago,
    Double montoRecibido,
    Double cambio,
    String referenciaPago
) {}
