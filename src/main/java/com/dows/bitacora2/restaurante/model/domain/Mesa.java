package com.dows.bitacora2.restaurante.model.domain;

import com.dows.bitacora2.restaurante.exception.ConflictoException;
import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;

public class Mesa {
    private Long id;
    private Integer numero;
    private Integer capacidad;
    private EstadoMesa estado;
    private Boolean cuentaAbierta;

    public Mesa() {}

    public Mesa(Long id, Integer numero, Integer capacidad, EstadoMesa estado, Boolean cuentaAbierta) {
        this.id = id;
        this.numero = numero;
        this.capacidad = capacidad;
        this.estado = estado;
        this.cuentaAbierta = cuentaAbierta;
    }

    /**
     * Cambio de estado manual (reservar o liberar una reserva). La ocupación la gobierna
     * la cuenta: OCUPADA se asigna al abrirla y la mesa se libera solo al pagarla (RN-07).
     */
    public void cambiarEstadoManual(EstadoMesa nuevo) {
        if (Boolean.TRUE.equals(cuentaAbierta)) {
            throw new ConflictoException("La mesa tiene una cuenta abierta; se libera al registrar el pago.");
        }
        if (nuevo == EstadoMesa.OCUPADA) {
            throw new ReglaDeNegocioException("El estado OCUPADA se asigna automáticamente al abrir una cuenta.");
        }
        this.estado = nuevo;
    }

    public boolean estaDisponible() {
        return this.estado == EstadoMesa.DISPONIBLE && !Boolean.TRUE.equals(cuentaAbierta);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public Integer getCapacidad() { return capacidad; }
    public void setCapacidad(Integer capacidad) { this.capacidad = capacidad; }
    public EstadoMesa getEstado() { return estado; }
    public void setEstado(EstadoMesa estado) { this.estado = estado; }
    public Boolean getCuentaAbierta() { return cuentaAbierta; }
    public void setCuentaAbierta(Boolean cuentaAbierta) { this.cuentaAbierta = cuentaAbierta; }
}
