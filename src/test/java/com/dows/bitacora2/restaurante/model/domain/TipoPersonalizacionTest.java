package com.dows.bitacora2.restaurante.model.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TipoPersonalizacionTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "Pizza, PIZZA", "PIZZAS, PIZZA", "pízza, PIZZA", "'  pizza ', PIZZA",
            "Pasta, PASTA", "pastas, PASTA", "PÁSTA, PASTA",
            "Bebida, NINGUNA", "Postre, NINGUNA", "'', NINGUNA", "Pizzería, NINGUNA"
    })
    void deCategoria(String categoria, TipoPersonalizacion esperado) {
        assertEquals(esperado, TipoPersonalizacion.deCategoria(categoria));
    }

    @ParameterizedTest
    @NullSource
    void deCategoria_nula_esNinguna(String categoria) {
        assertEquals(TipoPersonalizacion.NINGUNA, TipoPersonalizacion.deCategoria(categoria));
    }
}
