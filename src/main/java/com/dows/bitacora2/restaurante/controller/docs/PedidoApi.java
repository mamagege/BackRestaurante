package com.dows.bitacora2.restaurante.controller.docs;

import com.dows.bitacora2.restaurante.model.domain.EstadoPedido;
import com.dows.bitacora2.restaurante.model.dto.request.PedidoRequestDTO;
import java.time.LocalDate;
import com.dows.bitacora2.restaurante.model.dto.response.PedidoResponseDTO;
import com.dows.bitacora2.restaurante.model.dto.response.ResumenDiaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import java.util.List;

@Tag(name = "Pedidos", description = "Administración de pedidos del restaurante")
public interface PedidoApi {
    @Operation(summary = "Listar pedidos",
            description = "Ordenados por hora de llegada. Filtros opcionales: estado (uno o varios, por ejemplo "
                    + "?estado=RECIBIDO,EN_PREPARACION para el tablero de cocina) e idCuenta.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de pedidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente.")
    })
    ResponseEntity<List<PedidoResponseDTO>> listar(List<EstadoPedido> estados, Long idCuenta);

    @Operation(summary = "Obtener pedido por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Pedido no existe")
    })
    ResponseEntity<PedidoResponseDTO> obtener(Long id);

    @Operation(summary = "Crear un nuevo pedido")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pedido creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "La mesa o alguno de los platos no existe"),
        @ApiResponse(responseCode = "422", description = "Plato no disponible o personalización inválida (RN-01, RN-02)")
    })
    ResponseEntity<PedidoResponseDTO> crear(PedidoRequestDTO dto);

    @Operation(summary = "Actualizar pedido")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Pedido no existe"),
        @ApiResponse(responseCode = "422", description = "El pedido ya no está en estado RECIBIDO (RN-04) o personalización inválida")
    })
    ResponseEntity<PedidoResponseDTO> actualizar(Long id, PedidoRequestDTO dto);

    @Operation(summary = "Cambiar estado de un pedido")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado actualizado"),
        @ApiResponse(responseCode = "400", description = "Falta el parámetro estado"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Pedido no existe"),
        @ApiResponse(responseCode = "422", description = "Transición no permitida o estado inexistente")
    })
    ResponseEntity<PedidoResponseDTO> cambiarEstado(Long id, String estado);

    @Operation(summary = "Cancelar pedido")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Pedido cancelado"),

        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),

        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente."),
        @ApiResponse(responseCode = "404", description = "Pedido no existe"),
        @ApiResponse(responseCode = "422", description = "El pedido ya no está en estado RECIBIDO y no se puede cancelar")
    })
    ResponseEntity<Void> eliminar(Long id);

    @Operation(summary = "Resumen de ventas del día",
            description = "Ventas por plato y por usuario que registró el pedido, sin pedidos cancelados. "
                    + "Parámetro opcional fecha (AAAA-MM-DD); por defecto, hoy.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Total de pedidos, ingresos y platos más pedidos"),
        @ApiResponse(responseCode = "401", description = "No autenticado o token inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado. Motivo: Rol insuficiente.")
    })
    ResponseEntity<ResumenDiaDTO> resumenDelDia(LocalDate fecha);
}
