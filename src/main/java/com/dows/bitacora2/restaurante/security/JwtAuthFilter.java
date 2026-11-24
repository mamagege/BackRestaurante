package com.dows.bitacora2.restaurante.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica la petición si trae un JWT válido.
 * Si el token falta, es inválido, expiró o su usuario ya no existe, la petición sigue
 * SIN autenticar: las reglas de SecurityConfig deciden (las rutas públicas responden
 * normal y las protegidas devuelven 401 desde el AuthenticationEntryPoint).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UsuarioDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws IOException, ServletException {

        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIJO)) {
            autenticar(header.substring(PREFIJO.length()), req);
        }
        chain.doFilter(req, res);
    }

    private void autenticar(String token, HttpServletRequest req) {
        if (!jwtUtil.isValid(token)) {
            return;
        }
        String email = jwtUtil.extractClaims(token).getSubject();
        try {
            UserDetails user = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (UsernameNotFoundException e) {
            log.warn("JWT válido de un usuario que ya no existe: {}", email);
        }
    }
}
