package com.dows.bitacora2.restaurante.model.dto.response;

import com.dows.bitacora2.restaurante.model.domain.ResumenVentas;

import java.time.LocalDate;
import java.util.List;

public record ResumenDiaDTO(
    LocalDate fecha,
    long totalPedidos,
    double ingresoTotal,
    List<VentaPlatoDTO> porPlato,
    List<VentaUsuarioDTO> porUsuario
) {
    public record VentaPlatoDTO(String plato, long cantidad, double ingresos) {}

    public record VentaUsuarioDTO(String usuario, long pedidos, double ingresos) {}

    public static ResumenDiaDTO desde(ResumenVentas r) {
        return new ResumenDiaDTO(r.fecha(), r.totalPedidos(), r.ingresoTotal(),
                r.porPlato().stream().map(p -> new VentaPlatoDTO(p.plato(), p.cantidad(), p.ingresos())).toList(),
                r.porUsuario().stream().map(u -> new VentaUsuarioDTO(u.usuario(), u.pedidos(), u.ingresos())).toList());
    }
}
