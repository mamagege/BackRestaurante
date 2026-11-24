package com.dows.bitacora2.restaurante.model.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Reporte de ventas de un día (RF6): no incluye pedidos cancelados.
 * {@code porUsuario} agrupa por quien registró el pedido (mesero o cliente).
 */
public record ResumenVentas(
        LocalDate fecha,
        long totalPedidos,
        double ingresoTotal,
        List<VentaPlato> porPlato,
        List<VentaUsuario> porUsuario
) {
    public record VentaPlato(String plato, long cantidad, double ingresos) {}

    public record VentaUsuario(String usuario, long pedidos, double ingresos) {}
}
