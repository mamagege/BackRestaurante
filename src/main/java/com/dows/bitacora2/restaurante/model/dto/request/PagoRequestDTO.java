package com.dows.bitacora2.restaurante.model.dto.request;

import com.dows.bitacora2.restaurante.model.domain.MetodoPago;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PagoRequestDTO(
    @NotNull(message = "El método de pago es obligatorio (EFECTIVO, TARJETA o TRANSFERENCIA)")
    MetodoPago metodoPago,

    @NotNull(message = "El monto recibido es obligatorio")
    @PositiveOrZero(message = "El monto recibido no puede ser negativo")
    Double montoRecibido
) {}
