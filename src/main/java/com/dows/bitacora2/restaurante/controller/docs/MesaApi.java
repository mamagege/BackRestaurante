package com.dows.bitacora2.restaurante.controller.docs;

import com.dows.bitacora2.restaurante.model.dto.request.MesaRequestDTO;
import com.dows.bitacora2.restaurante.model.dto.response.MesaResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import java.util.List;

@Tag(name = "Mesas", description = "Administración de mesas del restaurante")
public interface MesaApi {
    @Operation(summary = "Listar todas las mesas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de mesas"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente.")
    })
    ResponseEntity<List<MesaResponseDTO>> listar();

    @Operation(summary = "Obtener mesa por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mesa encontrada"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Mesa no existe")
    })
    ResponseEntity<MesaResponseDTO> obtener(Long id);

    @Operation(summary = "Crear una nueva mesa")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Mesa creada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "409", description = "Número de mesa duplicado")
    })
    ResponseEntity<MesaResponseDTO> crear(MesaRequestDTO dto);

    @Operation(summary = "Actualizar mesa")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mesa actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Mesa no existe"),
        @ApiResponse(responseCode = "409", description = "Número duplicado")
    })
    ResponseEntity<MesaResponseDTO> actualizar(Long id, MesaRequestDTO dto);

    @Operation(summary = "Cambiar estado de una mesa",
            description = "Solo para marcar una mesa libre como RESERVADA o DISPONIBLE. El estado OCUPADA se asigna "
                    + "al abrir una cuenta (POST /api/v1/cuentas) y la mesa se libera al registrar el pago "
                    + "(PATCH /api/v1/cuentas/{id}/pago).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Falta el parámetro estado"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Mesa no existe"),
        @ApiResponse(responseCode = "409", description = "La mesa tiene una cuenta abierta"),
        @ApiResponse(responseCode = "422", description = "Estado inexistente o estado OCUPADA asignado manualmente")
    })
    ResponseEntity<MesaResponseDTO> cambiarEstado(Long id, String estado);
}
