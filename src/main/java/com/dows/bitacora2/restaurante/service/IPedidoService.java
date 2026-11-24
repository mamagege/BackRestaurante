package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.domain.Pedido;
import com.dows.bitacora2.restaurante.model.domain.ResumenVentas;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface IPedidoService {
    List<Pedido> obtenerTodos();
    /** Pedidos ordenados por llegada, filtrados por estados y/o cuenta (ambos opcionales). */
    List<Pedido> buscar(Collection<EstadoPedido> estados, Long idCuenta);
    Pedido obtenerPorId(Long id);
    Pedido crear(Pedido pedido);
    Pedido actualizar(Long id, Pedido pedido);
    Pedido cambiarEstado(Long id, String estado);
    void eliminar(Long id);
    /** Ventas del día indicado, sin pedidos cancelados (RF6). */
    ResumenVentas resumenDelDia(LocalDate fecha);
}
