package com.dows.bitacora2.restaurante.integracion;

import com.dows.bitacora2.restaurante.repository.MesaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextoIntegracionTest extends IntegracionBase {

    @Autowired private MesaRepository mesaRepository;

    @Test
    @DisplayName("la aplicación arranca completa contra PostgreSQL y MongoDB reales")
    void contextoCarga() {
        assertEquals(0, mesaRepository.count());
    }
}
