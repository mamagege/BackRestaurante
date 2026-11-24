package com.dows.bitacora2.restaurante.model.domain;

import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CuentaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 24, 21, 0);

    private static Cuenta abierta(double total) {
        return new Cuenta(1L, 2L, total, EstadoCuenta.ABIERTA, AHORA.minusHours(1));
    }

    @Test
    @DisplayName("efectivo con monto mayor al total: cierra la cuenta y calcula el cambio")
    void efectivo_conCambio() {
        Cuenta cuenta = abierta(58000);
        cuenta.registrarPago(MetodoPago.EFECTIVO, 60000.0, null, AHORA);

        assertEquals(EstadoCuenta.CERRADA, cuenta.getEstado());
        assertEquals(60000.0, cuenta.getMontoRecibido());
        assertEquals(2000.0, cuenta.getCambio());
        assertEquals(MetodoPago.EFECTIVO, cuenta.getMetodoPago());
        assertEquals(AHORA, cuenta.getFechaCierre());
    }

    @ParameterizedTest
    @EnumSource(MetodoPago.class)
    @DisplayName("cualquier método con el monto exacto cierra la cuenta sin cambio")
    void montoExacto_cierra(MetodoPago metodo) {
        Cuenta cuenta = abierta(58000);
        cuenta.registrarPago(metodo, 58000.0, "REF-1", AHORA);

        assertFalse(cuenta.estaAbierta());
        assertEquals(0.0, cuenta.getCambio());
        assertEquals("REF-1", cuenta.getReferenciaPago());
    }

    @ParameterizedTest
    @EnumSource(MetodoPago.class)
    @DisplayName("un monto menor al total se rechaza y la cuenta sigue abierta")
    void montoInsuficiente_lanza(MetodoPago metodo) {
        Cuenta cuenta = abierta(58000);
        ReglaDeNegocioException ex = assertThrows(ReglaDeNegocioException.class,
                () -> cuenta.registrarPago(metodo, 57999.99, null, AHORA));

        assertTrue(ex.getMessage().contains("menor que el total"));
        assertTrue(cuenta.estaAbierta());
    }

    @ParameterizedTest
    @EnumSource(value = MetodoPago.class, names = {"TARJETA", "TRANSFERENCIA"})
    @DisplayName("tarjeta y transferencia no admiten un monto mayor al total")
    void pagoElectronico_conExcedente_lanza(MetodoPago metodo) {
        Cuenta cuenta = abierta(58000);
        assertThrows(ReglaDeNegocioException.class, () -> cuenta.registrarPago(metodo, 60000.0, null, AHORA));
        assertTrue(cuenta.estaAbierta());
    }

    @Test
    @DisplayName("una cuenta cerrada no se puede pagar de nuevo")
    void cuentaCerrada_lanza() {
        Cuenta cuenta = abierta(1000);
        cuenta.registrarPago(MetodoPago.EFECTIVO, 1000.0, null, AHORA);

        ReglaDeNegocioException ex = assertThrows(ReglaDeNegocioException.class,
                () -> cuenta.registrarPago(MetodoPago.EFECTIVO, 1000.0, null, AHORA));
        assertTrue(ex.getMessage().contains("pagada"));
    }

    @Test
    @DisplayName("una cuenta sin consumos se puede cerrar con monto 0")
    void cuentaSinConsumo_seCierraConCero() {
        Cuenta cuenta = abierta(0);
        cuenta.registrarPago(MetodoPago.EFECTIVO, 0.0, null, AHORA);
        assertFalse(cuenta.estaAbierta());
    }

    @Test
    @DisplayName("sin método o sin monto se rechaza")
    void datosFaltantes_lanza() {
        Cuenta cuenta = abierta(1000);
        assertThrows(ReglaDeNegocioException.class, () -> cuenta.validarPago(null, 1000.0));
        assertThrows(ReglaDeNegocioException.class, () -> cuenta.validarPago(MetodoPago.EFECTIVO, null));
    }
}
