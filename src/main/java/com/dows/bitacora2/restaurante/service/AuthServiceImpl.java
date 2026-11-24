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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService intentos;

    @Override
    public String autenticarYGenerarToken(LoginRequestDTO dto) {
        String email = dto.email();
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
}
