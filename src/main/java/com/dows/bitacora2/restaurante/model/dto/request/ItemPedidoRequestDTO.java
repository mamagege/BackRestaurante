package com.dows.bitacora2.restaurante.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Ítem de un pedido. Para pizza y pasta, masa y salsa son obligatorias (RN-01); los
 * límites de toppings, proteínas y salsas son reglas de negocio y se validan en el dominio.
 * Aquí solo van validaciones sintácticas (tamaños máximos para evitar abusos).
 */
public record ItemPedidoRequestDTO(
    @NotNull(message = "El id del plato es obligatorio")
    Long idPlato,

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    Integer cantidad,

    @Size(max = 50, message = "La masa no puede exceder 50 caracteres")
    String masa,

    @Size(max = 50, message = "La salsa no puede exceder 50 caracteres")
    String salsa,

    @Size(max = 10, message = "Máximo 10 salsas adicionales")
    List<@NotBlank(message = "Las salsas adicionales no pueden estar vacías")
         @Size(max = 50, message = "Cada salsa adicional admite máximo 50 caracteres") String> salsasAdicionales,

    @Size(max = 10, message = "Máximo 10 proteínas")
    List<@NotBlank(message = "Las proteínas no pueden estar vacías")
         @Size(max = 50, message = "Cada proteína admite máximo 50 caracteres") String> proteinas,

    @Size(max = 20, message = "Máximo 20 toppings")
    List<@NotBlank(message = "Los toppings no pueden estar vacíos")
         @Size(max = 50, message = "Cada topping admite máximo 50 caracteres") String> toppings,

    @Size(max = 200, message = "Las notas no pueden exceder 200 caracteres")
    String notas
) {}
