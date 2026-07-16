package com.bondi_mcp.mcp_stm_montevideo.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Normaliza texto para la búsqueda de paradas: mayúsculas, sin tildes y sin puntuación.
 *
 * <p>Se aplica igual al indexar y al consultar, así "Coruña" matchea "CORUNA" y "18 de Julio"
 * matchea "18 DE JULIO".
 */
public final class TextoNormalizador {

    /**
     * Palabras con las que la gente une dos calles al escribir un cruce.
     *
     * <p>Se descartan de la consulta: exigir que aparezcan haría que "18 de julio y ejido" no
     * matchee ninguna parada, porque "ESQUINA" o "CON" no forman parte del nombre de las calles.
     */
    private static final Set<String> CONECTORES = Set.of("Y", "ESQ", "ESQUINA", "CON");

    private TextoNormalizador() {
    }

    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        final String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Divide la consulta normalizada en palabras; devuelve vacío si no queda nada útil. */
    public static List<String> enPalabras(String texto) {
        final String normalizado = normalizar(texto);
        if (normalizado.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalizado.split(" ")).filter(p -> !p.isBlank()).toList();
    }

    /**
     * Palabras de una consulta que sirven para buscar: las de {@link #enPalabras} sin conectores.
     *
     * <p>Si la consulta es solo conectores, devuelve vacío en vez de matchear todas las paradas.
     */
    public static List<String> enPalabrasDeBusqueda(String texto) {
        return enPalabras(texto).stream().filter(palabra -> !CONECTORES.contains(palabra)).toList();
    }
}
