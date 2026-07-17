package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.List;

/**
 * El recorrido de una línea, un sentido por dirección del GTFS.
 *
 * <p>De las variantes de cada sentido (salidas cortas, desvíos) acá va solo la más larga: es el
 * recorrido completo y en la práctica contiene a las demás.
 *
 * @param sentidos normalmente dos (ida y vuelta); vacío si la línea no existe o no se importó
 */
public record RecorridoDeLinea(String linea, List<Sentido> sentidos) {

    public RecorridoDeLinea {
        sentidos = List.copyOf(sentidos);
    }

    /**
     * Un sentido del recorrido.
     *
     * @param destino descripción de la última parada, que es como la gente nombra el sentido
     *                ("la 185 hacia Portones")
     * @param paradas en el orden en que el bus las toca
     */
    public record Sentido(String destino, List<Parada> paradas) {

        public Sentido {
            paradas = List.copyOf(paradas);
        }
    }
}
