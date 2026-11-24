package com.dows.bitacora2.restaurante.model.domain;

import com.dows.bitacora2.restaurante.exception.ReglaDeNegocioException;
import com.dows.bitacora2.restaurante.exception.ToppingsExcedidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reglas de personalización del concepto italiano, probadas como lógica pura de dominio. */
class ItemPedidoTest {

    private static Plato plato(String nombre, String categoria, double precio, boolean disponible) {
        return new Plato(1L, nombre, precio, categoria, disponible, null);
    }

    private static final Plato PIZZA = plato("Margarita", "Pizza", 30000, true);
    private static final Plato PASTA = plato("Carbonara", "Pasta", 28000, true);
    private static final Plato BEBIDA = plato("Limonada", "Bebida", 8000, true);

    private static ItemPedido item(String masa, String salsa) {
        ItemPedido item = new ItemPedido();
        item.setIdPlato(1L);
        item.setCantidad(2);
        item.setMasa(masa);
        item.setSalsa(salsa);
        return item;
    }

    private static List<String> lista(int n) {
        return Collections.nCopies(n, "x");
    }

    @Test
    @DisplayName("RN-03: al aplicar el plato se congelan nombre y precio")
    void aplicarPlato_congelaNombreYPrecio() {
        Plato margarita = plato("Margarita", "Pizza", 30000, true);
        ItemPedido item = item("Delgada", "Tomate");
        item.aplicarPlato(margarita);

        margarita.setPrecio(99999.0); // un cambio posterior en la carta no afecta el ítem
        assertEquals("Margarita", item.getNombrePlato());
        assertEquals(30000.0, item.getPrecioCongelado());
        assertEquals(60000.0, item.subtotal());
    }

    @Test
    @DisplayName("un plato no disponible no se puede ordenar")
    void platoNoDisponible_lanza() {
        ReglaDeNegocioException ex = assertThrows(ReglaDeNegocioException.class,
                () -> item("Delgada", "Tomate").aplicarPlato(plato("Diavola", "Pizza", 1, false)));
        assertTrue(ex.getMessage().contains("no está disponible"));
    }

    @Nested
    @DisplayName("Pizza")
    class Pizza {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("RN-01: sin masa se rechaza")
        void sinMasa_lanza(String masa) {
            ReglaDeNegocioException ex = assertThrows(ReglaDeNegocioException.class,
                    () -> item(masa, "Tomate").aplicarPlato(PIZZA));
            assertTrue(ex.getMessage().startsWith("RN-01"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("RN-01: sin salsa se rechaza")
        void sinSalsa_lanza(String salsa) {
            assertThrows(ReglaDeNegocioException.class, () -> item("Delgada", salsa).aplicarPlato(PIZZA));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 5})
        @DisplayName("RN-02: hasta 5 toppings es válido")
        void hastaCincoToppings_ok(int cantidad) {
            ItemPedido item = item("Delgada", "Tomate");
            item.setToppings(lista(cantidad));
            assertDoesNotThrow(() -> item.aplicarPlato(PIZZA));
        }

        @Test
        @DisplayName("RN-02: 6 toppings se rechaza")
        void seisToppings_lanza() {
            ItemPedido item = item("Delgada", "Tomate");
            item.setToppings(lista(6));
            ToppingsExcedidosException ex = assertThrows(ToppingsExcedidosException.class, () -> item.aplicarPlato(PIZZA));
            assertTrue(ex.getMessage().contains("se enviaron 6"));
        }

        @Test
        @DisplayName("proteínas y salsas adicionales no aplican a pizza")
        void proteinasEnPizza_lanza() {
            ItemPedido conProteinas = item("Delgada", "Tomate");
            conProteinas.setProteinas(List.of("Pollo"));
            assertThrows(ReglaDeNegocioException.class, () -> conProteinas.aplicarPlato(PIZZA));

            ItemPedido conSalsas = item("Delgada", "Tomate");
            conSalsas.setSalsasAdicionales(List.of("Pesto"));
            assertThrows(ReglaDeNegocioException.class, () -> conSalsas.aplicarPlato(PIZZA));
        }
    }

    @Nested
    @DisplayName("Pasta")
    class Pasta {

        @Test
        @DisplayName("RN-01: sin tipo de pasta se rechaza")
        void sinMasa_lanza() {
            assertThrows(ReglaDeNegocioException.class, () -> item(null, "Alfredo").aplicarPlato(PASTA));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2})
        @DisplayName("hasta 2 proteínas es válido")
        void hastaDosProteinas_ok(int cantidad) {
            ItemPedido item = item("Spaghetti", "Alfredo");
            item.setProteinas(lista(cantidad));
            assertDoesNotThrow(() -> item.aplicarPlato(PASTA));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 1, 2})
        @DisplayName("hasta 3 salsas combinadas (primaria + 2 adicionales) es válido")
        void hastaTresSalsas_ok(int adicionales) {
            ItemPedido item = item("Penne", "Napolitana");
            item.setSalsasAdicionales(lista(adicionales));
            assertDoesNotThrow(() -> item.aplicarPlato(PASTA));
        }

        @Test
        @DisplayName("la pasta admite toppings sin el límite de la pizza")
        void pastaConToppings_ok() {
            ItemPedido item = item("Penne", "Napolitana");
            item.setToppings(lista(6));
            assertDoesNotThrow(() -> item.aplicarPlato(PASTA));
        }
    }

    @Nested
    @DisplayName("Plato no personalizable")
    class NoPersonalizable {

        @Test
        @DisplayName("sin personalización es válido")
        void sinPersonalizacion_ok() {
            assertDoesNotThrow(() -> item(null, null).aplicarPlato(BEBIDA));
        }

        @Test
        @DisplayName("con masa, salsa o extras se rechaza")
        void conPersonalizacion_lanza() {
            assertThrows(ReglaDeNegocioException.class, () -> item("Delgada", null).aplicarPlato(BEBIDA));
            ItemPedido conToppings = item(null, null);
            conToppings.setToppings(List.of("Hielo"));
            assertThrows(ReglaDeNegocioException.class, () -> conToppings.aplicarPlato(BEBIDA));
        }
    }

    @Test
    @DisplayName("las listas nulas se normalizan a vacías y se copian (sin aliasing)")
    void listas_seNormalizanYCopian() {
        ItemPedido item = new ItemPedido();
        item.setToppings(null);
        assertNotNull(item.getToppings());
        assertTrue(item.getToppings().isEmpty());

        List<String> original = new java.util.ArrayList<>(List.of("Albahaca"));
        item.setToppings(original);
        original.add("Otro");
        assertEquals(1, item.getToppings().size());
    }
}
