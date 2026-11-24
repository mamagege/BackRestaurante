package com.dows.bitacora2.restaurante.model.domain;

import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.exception.ToppingsExcedidosException;

import java.util.ArrayList;
import java.util.List;

/**
 * Línea de un pedido. Congela nombre y precio del plato al agregarse (RN-03) y protege
 * las reglas de personalización del concepto italiano:
 * <ul>
 *   <li>RN-01: pizza y pasta exigen masa base (o tipo de pasta) y salsa primaria.</li>
 *   <li>RN-02: una pizza lleva máximo {@value #MAX_TOPPINGS_PIZZA} toppings.</li>
 *   <li>Pasta: máximo {@value #MAX_PROTEINAS_PASTA} proteínas y máximo
 *       {@value #MAX_SALSAS_PASTA} salsas combinadas (primaria + adicionales).</li>
 *   <li>Las proteínas y salsas adicionales solo aplican a pasta.</li>
 *   <li>Un plato no personalizable (bebida, postre, …) no acepta masa, salsas ni extras.</li>
 * </ul>
 */
public class ItemPedido {

    public static final int MAX_TOPPINGS_PIZZA = 5;
    public static final int MAX_PROTEINAS_PASTA = 2;
    public static final int MAX_SALSAS_PASTA = 3;

    private Long idPlato;
    private String nombrePlato;
    private Double precioCongelado;
    private Integer cantidad;
    private String masa;
    private String salsa;
    private List<String> salsasAdicionales = new ArrayList<>();
    private List<String> proteinas = new ArrayList<>();
    private List<String> toppings = new ArrayList<>();
    private String notas;

    public ItemPedido() {}

    public ItemPedido(Long idPlato, String nombrePlato, Double precioCongelado, Integer cantidad, String masa, String salsa, List<String> toppings) {
        this.idPlato = idPlato;
        this.nombrePlato = nombrePlato;
        this.precioCongelado = precioCongelado;
        this.cantidad = cantidad;
        this.masa = masa;
        this.salsa = salsa;
        setToppings(toppings);
    }

    /**
     * Asocia el ítem al plato de la carta: verifica disponibilidad y personalización,
     * y congela nombre y precio (RN-03). Si algo no cumple, lanza ReglaDeNegocioException.
     */
    public void aplicarPlato(Plato plato) {
        if (!plato.estaDisponible()) {
            throw new ReglaDeNegocioException("El plato '" + plato.getNombre() + "' no está disponible.");
        }
        validarPersonalizacion(plato.getNombre(), plato.tipoPersonalizacion());
        this.nombrePlato = plato.getNombre();
        this.precioCongelado = plato.getPrecio();
    }

    private void validarPersonalizacion(String nombre, TipoPersonalizacion tipo) {
        if (!tipo.esPersonalizable()) {
            if (tieneTexto(masa) || tieneTexto(salsa) || !salsasAdicionales.isEmpty()
                    || !proteinas.isEmpty() || !toppings.isEmpty()) {
                throw new ReglaDeNegocioException("El plato '" + nombre
                        + "' no admite personalización (masa, salsas, proteínas ni toppings).");
            }
            return;
        }

        if (!tieneTexto(masa) || !tieneTexto(salsa)) {
            throw new ReglaDeNegocioException("RN-01: '" + nombre
                    + "' es personalizable y debe tener masa base y salsa primaria.");
        }

        if (tipo == TipoPersonalizacion.PIZZA) {
            if (!proteinas.isEmpty() || !salsasAdicionales.isEmpty()) {
                throw new ReglaDeNegocioException("Las proteínas y salsas adicionales solo aplican a pasta; "
                        + "en pizza use toppings.");
            }
            if (toppings.size() > MAX_TOPPINGS_PIZZA) {
                throw new ToppingsExcedidosException("RN-02: Una pizza no puede llevar más de "
                        + MAX_TOPPINGS_PIZZA + " toppings (se enviaron " + toppings.size() + ").");
            }
        }

        if (tipo == TipoPersonalizacion.PASTA) {
            if (proteinas.size() > MAX_PROTEINAS_PASTA) {
                throw new ReglaDeNegocioException("Límite de proteínas: una pasta puede tener máximo "
                        + MAX_PROTEINAS_PASTA + " proteínas (se enviaron " + proteinas.size() + ").");
            }
            int totalSalsas = 1 + salsasAdicionales.size();
            if (totalSalsas > MAX_SALSAS_PASTA) {
                throw new ReglaDeNegocioException("Límite de salsas: una pasta puede combinar máximo "
                        + MAX_SALSAS_PASTA + " salsas (se enviaron " + totalSalsas + ").");
            }
        }
    }

    public Double subtotal() {
        return (this.precioCongelado != null ? this.precioCongelado : 0.0) * (this.cantidad != null ? this.cantidad : 0);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static List<String> copia(List<String> valores) {
        return valores == null ? new ArrayList<>() : new ArrayList<>(valores);
    }

    public Long getIdPlato() { return idPlato; }
    public void setIdPlato(Long idPlato) { this.idPlato = idPlato; }
    public String getNombrePlato() { return nombrePlato; }
    public void setNombrePlato(String nombrePlato) { this.nombrePlato = nombrePlato; }
    public Double getPrecioCongelado() { return precioCongelado; }
    public void setPrecioCongelado(Double precioCongelado) { this.precioCongelado = precioCongelado; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getMasa() { return masa; }
    public void setMasa(String masa) { this.masa = masa; }
    public String getSalsa() { return salsa; }
    public void setSalsa(String salsa) { this.salsa = salsa; }
    public List<String> getSalsasAdicionales() { return salsasAdicionales; }
    public void setSalsasAdicionales(List<String> salsasAdicionales) { this.salsasAdicionales = copia(salsasAdicionales); }
    public List<String> getProteinas() { return proteinas; }
    public void setProteinas(List<String> proteinas) { this.proteinas = copia(proteinas); }
    public List<String> getToppings() { return toppings; }
    public void setToppings(List<String> toppings) { this.toppings = copia(toppings); }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
