package com.dows.bitacora2.restaurante.model.domain;

import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.util.CalculoUtils;

import java.time.LocalDateTime;

/**
 * Cuenta de una mesa. Se cierra únicamente al registrar un pago válido (RN-07):
 * <ul>
 *   <li>el monto recibido debe cubrir el total;</li>
 *   <li>con tarjeta o transferencia debe ser exactamente el total (no hay cambio);</li>
 *   <li>con efectivo puede ser mayor y se calcula el cambio.</li>
 * </ul>
 */
public class Cuenta {
    private Long id;
    private Long idMesa;
    private Double total;
    private EstadoCuenta estado;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
    private MetodoPago metodoPago;
    private Double montoRecibido;
    private Double cambio;
    private String referenciaPago;

    public Cuenta() {}

    public Cuenta(Long id, Long idMesa, Double total, EstadoCuenta estado, LocalDateTime fechaApertura) {
        this.id = id;
        this.idMesa = idMesa;
        this.total = total;
        this.estado = estado;
        this.fechaApertura = fechaApertura;
    }

    public Double calcularTotal() {
        return this.total != null ? this.total : 0.0;
    }

    public boolean estaAbierta() {
        return this.estado == EstadoCuenta.ABIERTA;
    }

    /** Verifica que el pago sea aceptable sin modificar la cuenta (se usa antes de cobrar). */
    public void validarPago(MetodoPago metodo, Double monto) {
        if (!estaAbierta()) {
            throw new ReglaDeNegocioException("La cuenta ya se encuentra pagada.");
        }
        if (metodo == null || monto == null) {
            throw new ReglaDeNegocioException("El método de pago y el monto recibido son obligatorios.");
        }
        double totalCuenta = calcularTotal();
        if (CalculoUtils.redondear(monto) < totalCuenta) {
            throw new ReglaDeNegocioException("El monto recibido (" + monto
                    + ") es menor que el total de la cuenta (" + totalCuenta + ").");
        }
        if (!metodo.admiteCambio() && CalculoUtils.redondear(monto) != totalCuenta) {
            throw new ReglaDeNegocioException("Con " + metodo.name().toLowerCase()
                    + " el monto debe ser exactamente el total de la cuenta (" + totalCuenta + ").");
        }
    }

    /** Registra el pago y cierra la cuenta (RN-07). */
    public void registrarPago(MetodoPago metodo, Double monto, String referencia, LocalDateTime fecha) {
        validarPago(metodo, monto);
        this.metodoPago = metodo;
        this.montoRecibido = CalculoUtils.redondear(monto);
        this.cambio = CalculoUtils.redondear(monto - calcularTotal());
        this.referenciaPago = referencia;
        this.fechaCierre = fecha;
        this.estado = EstadoCuenta.CERRADA;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdMesa() { return idMesa; }
    public void setIdMesa(Long idMesa) { this.idMesa = idMesa; }
    public Double getTotal() { return total; }
    public void setTotal(Double total) { this.total = total; }
    public EstadoCuenta getEstado() { return estado; }
    public void setEstado(EstadoCuenta estado) { this.estado = estado; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public MetodoPago getMetodoPago() { return metodoPago; }
    public void setMetodoPago(MetodoPago metodoPago) { this.metodoPago = metodoPago; }
    public Double getMontoRecibido() { return montoRecibido; }
    public void setMontoRecibido(Double montoRecibido) { this.montoRecibido = montoRecibido; }
    public Double getCambio() { return cambio; }
    public void setCambio(Double cambio) { this.cambio = cambio; }
    public String getReferenciaPago() { return referenciaPago; }
    public void setReferenciaPago(String referenciaPago) { this.referenciaPago = referenciaPago; }
}
