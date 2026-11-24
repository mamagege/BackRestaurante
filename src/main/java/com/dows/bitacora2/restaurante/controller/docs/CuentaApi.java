package com.dows.bitacora2.restaurante.controller.docs;

import com.dows.bitacora2.restaurante.model.dto.request.CuentaRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.request.PagoRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.response.CuentaResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import java.util.List;

@Tag(name = "Cuentas", description = "Administración de cuentas y pagos del restaurante")
public interface CuentaApi {
    @Operation(summary = "Listar todas las cuentas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de cuentas"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente.")
    })
    ResponseEntity<List<CuentaResponseDTO>> listar();

    @Operation(summary = "Obtener cuenta por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta encontrada"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Cuenta no existe")
    })
    ResponseEntity<CuentaResponseDTO> obtener(Long id);

    @Operation(summary = "Crear/abrir una nueva cuenta")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cuenta creada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Mesa no existe"),
        @ApiResponse(responseCode = "409", description = "La mesa ya tiene una cuenta abierta (RN-06)")
    })
    ResponseEntity<CuentaResponseDTO> crear(CuentaRequestDTO dto);

    @Operation(summary = "Actualizar total de la cuenta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Total actualizado"),

        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),

        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Cuenta no existe"),
        @ApiResponse(responseCode = "422", description = "La cuenta ya está pagada")
    })
    ResponseEntity<CuentaResponseDTO> actualizarTotal(Long id);

    @Operation(summary = "Registrar el pago y cerrar la cuenta",
            description = "Cierra la cuenta y libera la mesa (RN-07). Con EFECTIVO el monto puede superar el total "
                    + "y se calcula el cambio; con TARJETA o TRANSFERENCIA debe ser exactamente el total y se "
                    + "procesa por la pasarela de pago.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta pagada"),
        @ApiResponse(responseCode = "400", description = "Falta el método de pago o el monto, o tienen formato inválido"),

        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),

        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Cuenta no existe"),
        @ApiResponse(responseCode = "422", description = "Cuenta ya pagada, pedidos sin entregar, monto insuficiente "
                + "o pago rechazado por la pasarela")
    })
    ResponseEntity<CuentaResponseDTO> pagar(Long id, PagoRequestDTO dto);
}
