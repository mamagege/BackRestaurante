package com.dows.bitacora2.restaurante.model.domain;

public enum MetodoPago {
    EFECTIVO,
    TARJETA,
    TRANSFERENCIA;

    /** Solo el efectivo admite recibir más del total y devolver cambio. */
    public boolean admiteCambio() {
        return this == EFECTIVO;
    }
}
