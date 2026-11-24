package com.dows.bitacora2.restaurante.config;

import com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity;
import com.dows.bitacora2.restaurante.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea un usuario por rol para desarrollo local.
 * Solo se activa con SEED_ENABLED=true; en QA y producción queda apagado por defecto
 * para que nunca existan cuentas con contraseñas conocidas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        crearUsuario("admin@restaurante.com", "admin123", "ADMIN");
        crearUsuario("mesero@restaurante.com", "mesero123", "MESERO");
        crearUsuario("cocina@restaurante.com", "cocina123", "COCINA");
        crearUsuario("cliente@restaurante.com", "cliente123", "CLIENTE");
        log.warn("Usuarios de desarrollo creados (admin, mesero, cocina, cliente @restaurante.com). "
                + "No habilitar SEED_ENABLED fuera de desarrollo.");
    }

    private void crearUsuario(String email, String password, String rol) {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail(email);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuarioRepository.save(usuario);
    }
}
