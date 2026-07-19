package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Las esperas reales observadas de una línea, contra lo que promete el papel.
 *
 * <p>No es puntualidad estricta (prometido vs cumplido de cada coche): es algo más útil para
 * quien espera en la esquina — cuánto falta DE VERDAD para el próximo bondi, medido cada vez
 * que alguien consultó el tiempo real. El dataset se construye solo con el uso y no se puede
 * reconstruir hacia atrás: cada día que pasa vale más.
 *
 * @param linea                  la línea observada
 * @param codigoParada           la parada, o {@code null} si el resumen es de toda la línea
 * @param observaciones          cuántas mediciones hay
 * @param primeraObservacion     desde cuándo se junta el dato
 * @param ultimaObservacion      hasta cuándo
 * @param esperaMediaMinutos     promedio de la espera al próximo bondi
 * @param esperaMedianaMinutos   la espera típica (mediana): el número que hay que contar
 * @param esperaP90Minutos       la espera con mala suerte: el 10% de las veces fue peor que esto
 * @param esperaTeoricaMinutos   la espera media que promete el horario programado diurno
 *                               (frecuencia/2); {@code null} sin parada o sin horarios
 * @param porFranja              el desglose por franja horaria, solo las que tienen datos
 */
public record HistorialDeEsperas(String linea, Long codigoParada, long observaciones,
        LocalDate primeraObservacion, LocalDate ultimaObservacion, Integer esperaMediaMinutos,
        Integer esperaMedianaMinutos, Integer esperaP90Minutos, Integer esperaTeoricaMinutos,
        List<Franja> porFranja) {

    public HistorialDeEsperas {
        porFranja = List.copyOf(porFranja);
    }

    /** Una franja horaria del día, con su espera media observada. */
    public record Franja(String nombre, long observaciones, int esperaMediaMinutos) {
    }

    /** Todavía no se observó nada de esta línea (acá o en general). */
    public static HistorialDeEsperas vacio(String linea, Long codigoParada) {
        return new HistorialDeEsperas(linea, codigoParada, 0, null, null, null, null, null, null,
                List.of());
    }

    public boolean sinDatos() {
        return observaciones == 0;
    }
}
