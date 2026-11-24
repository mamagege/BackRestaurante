package com.dows.bitacora2.restaurante.service;

import com.dows.bitacora2.restaurante.exception.BloqueoTemporalException;
import com.dows.bitacora2.restaurante.model.dto.auth.LoginRequestDTO;
import com.dows.bitacora2.restaurante.security.JwtUtil;
import com.dows.bitacora2.restaurante.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity;
import com.dows.bitacora2.restaurante.repository.UsuarioRepository;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private static final String ROL_CLIENTE = "CLIENTE";

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService intentos;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public String autenticarYGenerarToken(LoginRequestDTO dto) {
        String email = normalizar(dto.email());
        if (intentos.estaBloqueado(email)) {
            log.warn("Login rechazado por bloqueo temporal: {}", email);
            throw new BloqueoTemporalException("Usuario temporalmente bloqueado por demasiados intentos fallidos. "
                    + "Intente de nuevo en " + intentos.minutosDeBloqueo() + " minutos.");
        }

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, dto.password()));
            UserDetails user = (UserDetails) auth.getPrincipal();
            String token = jwtUtil.generateToken(user.getUsername(),
                    user.getAuthorities().iterator().next().getAuthority());
            intentos.registrarExito(email);
            log.info("Login exitoso: {}", email);
            return token;
        } catch (AuthenticationException e) {
            intentos.registrarFallo(email);
            log.warn("Login fallido: {}", email);
            throw e; // GlobalExceptionHandler la traduce a 401
        }
    }

    @Override
    @Transactional
    public String registrarCliente(String email, String password) {
        String normalizado = normalizar(email);
        if (usuarioRepository.findByEmail(normalizado).isPresent()) {
            throw new ConflictoException("Ya existe una cuenta registrada con el email " + normalizado);
        }
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail(normalizado);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(ROL_CLIENTE);
        usuarioRepository.save(usuario);
        log.info("Cliente registrado: {}", normalizado);
        return jwtUtil.generateToken(normalizado, "ROLE_" + ROL_CLIENTE);
    }

    private static String normalizar(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
