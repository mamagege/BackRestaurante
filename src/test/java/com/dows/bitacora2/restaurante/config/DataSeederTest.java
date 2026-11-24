package com.dows.bitacora2.restaurante.config;

import com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity;
import com.dows.bitacora2.restaurante.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DataSeederTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(UsuarioRepository.class, () -> mock(UsuarioRepository.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withUserConfiguration(DataSeeder.class);

    @Test
    @DisplayName("sin app.seed.enabled el seeder no se registra")
    void seeder_deshabilitadoPorDefecto() {
        contexto.run(ctx -> assertThat(ctx).doesNotHaveBean(DataSeeder.class));
    }

    @Test
    @DisplayName("con app.seed.enabled=false el seeder no se registra")
    void seeder_deshabilitadoExplicitamente() {
        contexto.withPropertyValues("app.seed.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DataSeeder.class));
    }

    @Test
    @DisplayName("con app.seed.enabled=true el seeder se registra")
    void seeder_habilitado() {
        contexto.withPropertyValues("app.seed.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DataSeeder.class));
    }

    @Test
    @DisplayName("crea un usuario por rol con la contraseña cifrada cuando no hay usuarios")
    void run_sinUsuarios_creaCuatroUsuariosCifrados() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.count()).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("hash");

        new DataSeeder(repo, encoder).run();

        verify(repo, times(4)).save(argThat((UsuarioEntity u) -> "hash".equals(u.getPassword())));
    }

    @Test
    @DisplayName("no crea usuarios si la tabla ya tiene datos")
    void run_conUsuarios_noCreaNada() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        when(repo.count()).thenReturn(3L);

        new DataSeeder(repo, mock(PasswordEncoder.class)).run();

        verify(repo, never()).save(any());
    }
}
