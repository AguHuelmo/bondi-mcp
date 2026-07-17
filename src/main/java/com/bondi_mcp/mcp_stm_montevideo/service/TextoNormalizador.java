package com.bondi_mcp.mcp_stm_montevideo.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    /** Tope de dígitos de un número de puerta. En Montevideo no pasan de cinco. */
    private static final int MAXIMOS_DIGITOS_DE_PUERTA = 5;

    private TextoNormalizador() {
    }

    /**
     * {@code true} si la consulta tiene forma de dirección con número de puerta
     * ("gabriel pereira 2470"): algo que hay que geocodificar y no buscar por texto.
     *
     * <p>Pide que el número esté al final y que antes haya al menos una palabra que no sea un
     * número, lo que descarta los otros dos formatos que recibimos. Un código de parada suelto
     * ("3977") no tiene calle adelante. Un cruce ("18 de julio y ejido") no termina en número, y
     * si además tiene conector no es una dirección aunque lo parezca.
     *
     * <p>Cuidado con "18 de julio 1360": las dos partes tienen números y solo la última es la
     * puerta. Por eso mira la última palabra y no "si hay algún número".
     */
    public static boolean pareceDireccionConNumero(String texto) {
        final List<String> palabras = enPalabras(texto);
        if (palabras.size() < 2 || palabras.stream().anyMatch(CONECTORES::contains)) {
            return false;
        }
        final List<String> calle = palabras.subList(0, palabras.size() - 1);
        return esNumeroDePuerta(palabras.getLast())
                && calle.stream().anyMatch(palabra -> !esNumero(palabra));
    }

    private static boolean esNumeroDePuerta(String palabra) {
        return esNumero(palabra) && palabra.length() <= MAXIMOS_DIGITOS_DE_PUERTA;
    }

    private static boolean esNumero(String palabra) {
        return !palabra.isEmpty() && palabra.chars().allMatch(Character::isDigit);
    }

    /**
     * Parte una consulta de cruce en sus dos calles, si tiene forma de cruce.
     *
     * <p>"gabriel pereira y chucarro" → ["GABRIEL PEREIRA", "CHUCARRO"]. Se corta por el primer
     * conector: es lo que separa las dos calles cuando alguien escribe una esquina.
     *
     * @return las dos calles, o vacío si no hay conector o alguna parte queda sin palabras útiles
     */
    public static Optional<List<String>> partirEnCalles(String texto) {
        final List<String> palabras = enPalabras(texto);
        for (int i = 0; i < palabras.size(); i++) {
            if (!CONECTORES.contains(palabras.get(i))) {
                continue;
            }
            final List<String> izquierda = sinConectores(palabras.subList(0, i));
            final List<String> derecha = sinConectores(palabras.subList(i + 1, palabras.size()));
            if (!izquierda.isEmpty() && !derecha.isEmpty()) {
                return Optional.of(List.of(String.join(" ", izquierda), String.join(" ", derecha)));
            }
        }
        return Optional.empty();
    }

    private static List<String> sinConectores(List<String> palabras) {
        return palabras.stream()
                .filter(palabra -> palabra.length() > 1)
                .filter(palabra -> !CONECTORES.contains(palabra))
                .toList();
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
     * Palabras de una consulta que sirven para buscar: las de {@link #enPalabras} sin conectores
     * ni palabras de una sola letra.
     *
     * <p>Lo de una letra son iniciales ("GABRIEL A PEREIRA", "PEDRO F BERRO"): como la búsqueda
     * puntúa por coincidencias, un patrón "%A%" matchearía casi todas las paradas y ensuciaría
     * el ranking sin aportar nada.
     *
     * <p>Si no queda ninguna palabra útil, devuelve vacío en vez de matchear todo.
     */
    public static List<String> enPalabrasDeBusqueda(String texto) {
        return enPalabras(texto).stream()
                .filter(palabra -> palabra.length() > 1)
                .filter(palabra -> !CONECTORES.contains(palabra))
                .toList();
    }
}
