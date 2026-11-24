package com.dows.bitacora2.restaurante.model.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Qué tipo de personalización admite un plato según su categoría (concepto italiano).
 * La categoría se compara sin distinguir mayúsculas, tildes ni plural: "Pizza", "PIZZAS"
 * y "pízza" son PIZZA. Cualquier otra categoría no admite personalización.
 */
public enum TipoPersonalizacion {
    PIZZA,
    PASTA,
    NINGUNA;

    public static TipoPersonalizacion deCategoria(String categoria) {
        if (categoria == null) {
            return NINGUNA;
        }
        String normalizada = Normalizer.normalize(categoria.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return switch (normalizada) {
            case "pizza", "pizzas" -> PIZZA;
            case "pasta", "pastas" -> PASTA;
            default -> NINGUNA;
        };
    }

    public boolean esPersonalizable() {
        return this != NINGUNA;
    }
}
