package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.BloqueoTemporalException;
import com.dows.bitacora2.restaurante.model.dto.auth.LoginRequestDTO;
import com.dows.bitacora2.restaurante.security.JwtUtil;
import com.dows.bitacora2.restaurante.security.LoginAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final LoginRequestDTO LOGIN = new LoginRequestDTO("mesero@restaurante.com", "mesero123");

    @Mock private AuthenticationManager authManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private LoginAttemptService intentos;
    @Mock private com.dows.bitacora2.restaurante.repository.UsuarioRepository usuarioRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @InjectMocks private AuthServiceImpl authService;

    @Test
    @DisplayName("login correcto genera el token y limpia los intentos")
    void login_exitoso_generaTokenYLimpiaIntentos() {
        UserDetails user = User.withUsername("mesero@restaurante.com").password("x").roles("MESERO").build();
        when(authManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(jwtUtil.generateToken("mesero@restaurante.com", "ROLE_MESERO")).thenReturn("jwt");

        assertEquals("jwt", authService.autenticarYGenerarToken(LOGIN));
        verify(intentos).registrarExito("mesero@restaurante.com");
    }

    @Test
    @DisplayName("credenciales malas registran el fallo y propagan la excepción")
    void login_fallido_registraFallo() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThrows(BadCredentialsException.class, () -> authService.autenticarYGenerarToken(LOGIN));
        verify(intentos).registrarFallo("mesero@restaurante.com");
    }

    @Test
    @DisplayName("un usuario bloqueado no llega a autenticarse")
    void login_bloqueado_noAutentica() {
        when(intentos.estaBloqueado("mesero@restaurante.com")).thenReturn(true);
        when(intentos.minutosDeBloqueo()).thenReturn(15L);

        BloqueoTemporalException ex = assertThrows(BloqueoTemporalException.class,
                () -> authService.autenticarYGenerarToken(LOGIN));
        assertTrue(ex.getMessage().contains("15 minutos"));
        verifyNoInteractions(authManager);
    }

    @Test
    @DisplayName("registro: crea un CLIENTE con email normalizado y password cifrado, y devuelve su token")
    void registrar_creaClienteYDevuelveToken() {
        when(usuarioRepository.findByEmail("nuevo@correo.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode("secreto123")).thenReturn("hash");
        when(jwtUtil.generateToken("nuevo@correo.com", "ROLE_CLIENTE")).thenReturn("jwt");

        assertEquals("jwt", authService.registrarCliente("  Nuevo@Correo.com ", "secreto123"));

        org.mockito.ArgumentCaptor<com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity> captor =
                org.mockito.ArgumentCaptor.forClass(com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("nuevo@correo.com", captor.getValue().getEmail());
        assertEquals("hash", captor.getValue().getPassword());
        assertEquals("CLIENTE", captor.getValue().getRol());
    }

    @Test
    @DisplayName("registro: un email ya registrado responde conflicto")
    void registrar_emailDuplicado_lanzaConflicto() {
        when(usuarioRepository.findByEmail("mesero@restaurante.com"))
                .thenReturn(java.util.Optional.of(new com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity()));

        assertThrows(com.dows.bitacora2.restaurante.exception.ConflictoException.class,
                () -> authService.registrarCliente("MESERO@restaurante.com", "secreto123"));
        verify(usuarioRepository, never()).save(any());
    }
}
