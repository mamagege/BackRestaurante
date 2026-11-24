package com.dows.bitacora2.restaurante.controller;

import com.dows.bitacora2.restaurante.model.dto.auth.LoginRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.auth.PerfilResponseDTO;
import com.dows.bitacora2.restaurante.model.dto.auth.RegistroRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.auth.TokenResponseDTO;
import com.dows.bitacora2.restaurante.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Login, registro de clientes y perfil del usuario autenticado")
public class AuthController {

    private static final String PREFIJO_ROL = "ROLE_";

    private final IAuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión y obtener un JWT")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas")
    @ApiResponse(responseCode = "429", description = "Usuario bloqueado temporalmente por intentos fallidos")
    public ResponseEntity<TokenResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        return ResponseEntity.ok(new TokenResponseDTO(authService.autenticarYGenerarToken(dto)));
    }

    @PostMapping("/registro")
    @Operation(summary = "Registrar un cliente nuevo y obtener su JWT")
    @ApiResponse(responseCode = "201", description = "Cliente registrado")
    @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese email")
    public ResponseEntity<TokenResponseDTO> registrar(@RequestBody @Valid RegistroRequestDTO dto) {
        String token = authService.registrarCliente(dto.email(), dto.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new TokenResponseDTO(token));
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado")
    @ApiResponse(responseCode = "401", description = "No autenticado o token inválido")
    public ResponseEntity<PerfilResponseDTO> perfil(Authentication auth) {
        String rol = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith(PREFIJO_ROL))
                .map(a -> a.substring(PREFIJO_ROL.length()))
                .findFirst()
                .orElse("");
        return ResponseEntity.ok(new PerfilResponseDTO(auth.getName(), rol));
    }
}
