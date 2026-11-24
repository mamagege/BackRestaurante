package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.model.domain.MetodoPago;

/**
 * Puerto hacia la pasarela de pago externa (sistema externo del diagrama de contexto).
 * El servicio de cuentas depende de esta interfaz y no de un proveedor concreto: cambiar
 * de pasarela es escribir otro adaptador, sin tocar la lógica de negocio.
 */
public interface IPasarelaPago {

    ResultadoPago procesar(Long idCuenta, MetodoPago metodo, double monto);

    record ResultadoPago(boolean aprobado, String referencia, String motivoRechazo) {
        public static ResultadoPago aprobado(String referencia) {
            return new ResultadoPago(true, referencia, null);
        }

        public static ResultadoPago rechazado(String motivo) {
            return new ResultadoPago(false, null, motivo);
        }
    }
}
