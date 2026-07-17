package com.bondi_mcp.mcp_stm_montevideo.domain;

/**
 * Una parada con su distancia a un punto de referencia.
 *
 * @param distanciaMetros distancia en línea recta, no caminando: la real siempre es algo mayor
 */
public record ParadaCercana(Parada parada, int distanciaMetros) {

    /** Texto corto y legible para mostrarle a una persona. */
    public String distanciaLegible() {
        if (distanciaMetros < 1000) {
            return distanciaMetros + " m";
        }
        return String.format("%.1f km", distanciaMetros / 1000.0);
    }
}
