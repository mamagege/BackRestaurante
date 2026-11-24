package com.dows.bitacora2.restaurante.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private static final String EMAIL = "admin@restaurante.com";

    /** Reloj controlable para simular el paso del tiempo. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-01-01T12:00:00Z");
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final RelojManual reloj = new RelojManual();
    private final LoginAttemptService servicio = new LoginAttemptService(5, Duration.ofMinutes(15), reloj);

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) servicio.registrarFallo(EMAIL);
    }

    @Test
    @DisplayName("con 4 fallos el usuario aún no está bloqueado")
    void cuatroFallos_noBloquea() {
        fallar(4);
        assertFalse(servicio.estaBloqueado(EMAIL));
    }

    @Test
    @DisplayName("al quinto fallo el usuario queda bloqueado")
    void cincoFallos_bloquea() {
        fallar(5);
        assertTrue(servicio.estaBloqueado(EMAIL));
    }

    @Test
    @DisplayName("el bloqueo expira y el contador se reinicia")
    void bloqueo_expiraYReinicia() {
        fallar(5);
        reloj.avanzar(Duration.ofMinutes(15));

        assertFalse(servicio.estaBloqueado(EMAIL));
        servicio.registrarFallo(EMAIL);
        assertFalse(servicio.estaBloqueado(EMAIL), "un fallo tras expirar no debe re-bloquear de inmediato");
    }

    @Test
    @DisplayName("los fallos fuera de la ventana se olvidan")
    void fallosAntiguos_seOlvidan() {
        fallar(4);
        reloj.avanzar(Duration.ofMinutes(16));
        servicio.registrarFallo(EMAIL);

        assertFalse(servicio.estaBloqueado(EMAIL));
    }

    @Test
    @DisplayName("un login exitoso limpia los fallos")
    void exito_limpiaFallos() {
        fallar(4);
        servicio.registrarExito(EMAIL);
        fallar(4);

        assertFalse(servicio.estaBloqueado(EMAIL));
    }

    @Test
    @DisplayName("el email se normaliza: mayúsculas y espacios cuentan como el mismo usuario")
    void email_seNormaliza() {
        for (int i = 0; i < 5; i++) servicio.registrarFallo("  ADMIN@Restaurante.com ");
        assertTrue(servicio.estaBloqueado(EMAIL));
    }
}
