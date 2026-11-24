package com.dows.bitacora2.restaurante.integration;

import com.dows.bitacora2.restaurante.model.domain.MetodoPago;
import com.dows.bitacora2.restaurante.service.IPasarelaPago.ResultadoPago;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasarelaPagoSimuladaTest {

    @Test
    void procesar_apruebaConReferenciaUnica() {
        PasarelaPagoSimulada pasarela = new PasarelaPagoSimulada();

        ResultadoPago r1 = pasarela.procesar(1L, MetodoPago.TARJETA, 58000);
        ResultadoPago r2 = pasarela.procesar(1L, MetodoPago.TARJETA, 58000);

        assertTrue(r1.aprobado());
        assertTrue(r1.referencia().startsWith("SIM-"));
        assertNotEquals(r1.referencia(), r2.referencia());
        assertNull(r1.motivoRechazo());
    }
}
