package com.dows.bitacora2.restaurante.model.domain;

import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MesaTest {

    private static Mesa mesa(EstadoMesa estado, boolean cuentaAbierta) {
        return new Mesa(1L, 5, 4, estado, cuentaAbierta);
    }

    @Test
    @DisplayName("una mesa libre se puede reservar y volver a liberar")
    void reservarYLiberar() {
        Mesa m = mesa(EstadoMesa.DISPONIBLE, false);
        m.cambiarEstadoManual(EstadoMesa.RESERVADA);
        assertEquals(EstadoMesa.RESERVADA, m.getEstado());
        m.cambiarEstadoManual(EstadoMesa.DISPONIBLE);
        assertTrue(m.estaDisponible());
    }

    @ParameterizedTest
    @EnumSource(EstadoMesa.class)
    @DisplayName("RN-07: con cuenta abierta ningún cambio manual está permitido")
    void conCuentaAbierta_lanzaConflicto(EstadoMesa nuevo) {
        Mesa m = mesa(EstadoMesa.OCUPADA, true);
        assertThrows(ConflictoException.class, () -> m.cambiarEstadoManual(nuevo));
        assertEquals(EstadoMesa.OCUPADA, m.getEstado());
    }

    @Test
    @DisplayName("OCUPADA no se asigna a mano: la asigna la apertura de cuenta")
    void ocupadaManual_lanza() {
        Mesa m = mesa(EstadoMesa.DISPONIBLE, false);
        assertThrows(ReglaDeNegocioException.class, () -> m.cambiarEstadoManual(EstadoMesa.OCUPADA));
        assertEquals(EstadoMesa.DISPONIBLE, m.getEstado());
    }
}
