package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.model.domain.Cuenta;
import com.dows.bitacora2.restaurante.model.domain.MetodoPago;
import java.util.List;

public interface ICuentaService {
    List<Cuenta> obtenerTodas();
    Cuenta obtenerPorId(Long id);
    Cuenta crear(Cuenta cuenta);
    Cuenta actualizarTotal(Long id);
    /** Registra el pago y cierra la cuenta liberando la mesa (RN-07). */
    Cuenta pagar(Long id, MetodoPago metodo, Double montoRecibido);
    /** Cuenta ABIERTA de la mesa; si no hay, lanza ReglaDeNegocioException (422). */
    Cuenta obtenerAbiertaPorMesa(Long idMesa);
}
