package com.dows.bitacora2.restaurante.integration;

import com.dows.bitacora2.restaurante.model.domain.MetodoPago;
import com.dows.bitacora2.restaurante.service.IPasarelaPago;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador simulado de la pasarela de pago: aprueba todas las transacciones y genera una
 * referencia. Reemplazarlo por el adaptador del proveedor real cuando exista la integración.
 */
@Slf4j
@Component
public class PasarelaPagoSimulada implements IPasarelaPago {

    @Override
    public ResultadoPago procesar(Long idCuenta, MetodoPago metodo, double monto) {
        String referencia = "SIM-" + UUID.randomUUID();
        log.warn("Pasarela de pago SIMULADA: cuenta id={} {} por {} aprobado con referencia {}",
                idCuenta, metodo, monto, referencia);
        return ResultadoPago.aprobado(referencia);
    }
}
