package com.dows.bitacora2.restaurante.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controla los intentos fallidos de login por email.
 * <ul>
 *   <li>Tras {@code maxIntentos} fallos dentro de la ventana, el email queda bloqueado
 *       durante {@code duracionBloqueo}; al vencer, el contador se reinicia.</li>
 *   <li>Los fallos más antiguos que la ventana se olvidan.</li>
 *   <li>Las operaciones son atómicas ({@link ConcurrentHashMap#compute}).</li>
 * </ul>
 * El estado vive en memoria: con varias instancias de la API cada una lleva su propio
 * conteo. Para escalar horizontalmente debe migrarse a Redis o a la base de datos.
 */
@Component
public class LoginAttemptService {

    private record Registro(int fallos, Instant primerFallo, Instant bloqueadoHasta) {
        boolean bloqueadoEn(Instant ahora) {
            return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
        }
    }

    private final ConcurrentHashMap<String, Registro> registros = new ConcurrentHashMap<>();
    private final int maxIntentos;
    private final Duration duracionBloqueo;
    private final Clock clock;

    @Autowired
    public LoginAttemptService(@Value("${app.security.login.max-intentos:5}") int maxIntentos,
                               @Value("${app.security.login.bloqueo-minutos:15}") long bloqueoMinutos) {
        this(maxIntentos, Duration.ofMinutes(bloqueoMinutos), Clock.systemUTC());
    }

    LoginAttemptService(int maxIntentos, Duration duracionBloqueo, Clock clock) {
        this.maxIntentos = maxIntentos;
        this.duracionBloqueo = duracionBloqueo;
        this.clock = clock;
    }

    public boolean estaBloqueado(String email) {
        Registro registro = registros.get(normalizar(email));
        return registro != null && registro.bloqueadoEn(clock.instant());
    }

    public void registrarFallo(String email) {
        registros.compute(normalizar(email), (clave, actual) -> {
            Instant ahora = clock.instant();
            boolean reiniciar = actual == null
                    || (actual.bloqueadoHasta() != null && !actual.bloqueadoEn(ahora))
                    || actual.primerFallo().plus(duracionBloqueo).isBefore(ahora);
            int fallos = reiniciar ? 1 : actual.fallos() + 1;
            Instant primerFallo = reiniciar ? ahora : actual.primerFallo();
            Instant bloqueadoHasta = fallos >= maxIntentos ? ahora.plus(duracionBloqueo) : null;
            return new Registro(fallos, primerFallo, bloqueadoHasta);
        });
    }

    public void registrarExito(String email) {
        registros.remove(normalizar(email));
    }

    public long minutosDeBloqueo() {
        return duracionBloqueo.toMinutes();
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
