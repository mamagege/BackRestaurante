package com.dows.bitacora2.restaurante.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dows.bitacora2.restaurante.repository.UsuarioRepository;
import com.dows.bitacora2.restaurante.persistence.entity.UsuarioEntity;
import com.dows.bitacora2.restaurante.security.JwtAuthFilter;
import com.dows.bitacora2.restaurante.security.ProblemDetailSecurityHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(proxyTargetClass = true)
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UsuarioRepository usuarioRepository,
            JwtAuthFilter jwtAuthFilter, com.dows.bitacora2.restaurante.security.JwtUtil jwtUtil,
            ProblemDetailSecurityHandler problemDetailSecurityHandler,
            org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self'"))
                        .frameOptions(frame -> frame.deny()) // bloquea iframes (Clickjacking)
                        .xssProtection(xss -> xss.headerValue( // activa filtro XSS del navegador
                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)))
                .authorizeHttpRequests(auth -> auth
                        // ── Público ────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/platos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/menu", "/api/v1/menu/**").permitAll()

                        // ── Platos (administración de la carta) ────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/platos").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/platos/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/platos/*/disponibilidad").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/platos/*").hasRole("ADMIN")

                        // ── Pedidos ────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/resumen").hasAnyRole("ADMIN", "MESERO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/pedidos", "/api/v1/pedidos/*")
                        .hasAnyRole("MESERO", "COCINA", "ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").hasAnyRole("CLIENTE", "MESERO")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/pedidos/*").hasAnyRole("CLIENTE", "MESERO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/estado").hasAnyRole("COCINA", "MESERO")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/pedidos/*").hasAnyRole("MESERO", "ADMIN")

                        // ── Mesas ──────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/mesas", "/api/v1/mesas/*")
                        .hasAnyRole("CLIENTE", "MESERO", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/mesas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/mesas/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/mesas/*/estado").hasAnyRole("MESERO", "ADMIN")
                        // La mesa se ocupa al abrir una cuenta y se libera al pagarla (ver /cuentas)

                        // ── Cuentas ────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/v1/cuentas", "/api/v1/cuentas/*")
                        .hasAnyRole("CLIENTE", "MESERO", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/cuentas").hasAnyRole("CLIENTE", "MESERO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/cuentas/*/total").hasAnyRole("CLIENTE", "MESERO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/cuentas/*/pago").hasAnyRole("MESERO", "ADMIN")

                        // Cualquier otra ruta de la API se niega: un endpoint nuevo
                        // debe declararse aquí de forma explícita para quedar expuesto.
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemDetailSecurityHandler)
                        .accessDeniedHandler(problemDetailSecurityHandler))
                // No se usa STATELESS porque rompería OAuth2
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // Login con Google solo si el perfil "oauth" registró un cliente OAuth2
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(oAuth2SuccessHandler(usuarioRepository, jwtUtil)));
        }
        return http.build();
    }

    private AuthenticationSuccessHandler oAuth2SuccessHandler(UsuarioRepository usuarioRepository,
            com.dows.bitacora2.restaurante.security.JwtUtil jwtUtil) {
        return (request, response, authentication) -> {
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            String email = oauthUser.getAttribute("email");
            String nombre = oauthUser.getAttribute("name");

            // Crear o actualizar el usuario en la BD con email y nombre
            UsuarioEntity usuario = usuarioRepository.findByEmail(email).orElseGet(() -> {
                UsuarioEntity newUser = new UsuarioEntity();
                newUser.setEmail(email);
                newUser.setPassword("OAUTH2_USER"); // No usa contraseña
                newUser.setRol("CLIENTE"); // Rol por defecto
                log.info("Nuevo usuario registrado desde OAuth2: {}", email);
                return usuarioRepository.save(newUser);
            });

            log.info("Login OAuth2 exitoso para: {} ({})", nombre, email);

            String token = jwtUtil.generateToken(email, usuario.getRol());

            response.setContentType("application/json");
            response.getWriter()
                    .write("{\n  \"message\": \"Copia el token JWT de abajo y pegalo en Swagger\",\n  \"token\": \""
                            + token + "\"\n}");
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
