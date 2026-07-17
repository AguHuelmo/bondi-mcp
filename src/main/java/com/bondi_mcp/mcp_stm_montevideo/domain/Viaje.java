package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.List;

/**
 * Una forma de ir de un punto a otro en ómnibus.
 *
 * @param tramos          uno si es directo, dos si hay transbordo
 * @param metrosCaminando total a pie: hasta la primera parada, en el transbordo, y desde la última
 */
public record Viaje(List<Tramo> tramos, int metrosCaminando) {

    public Viaje {
        tramos = List.copyOf(tramos);
    }

    public boolean esDirecto() {
        return tramos.size() == 1;
    }

    public int transbordos() {
        return tramos.size() - 1;
    }

    /** Las líneas a tomar, en orden. */
    public List<String> lineas() {
        return tramos.stream().map(Tramo::linea).toList();
    }

    /**
     * Un tramo arriba de una línea.
     *
     * @param subida parada donde se toma
     * @param bajada parada donde se baja
     */
    public record Tramo(String linea, Parada subida, Parada bajada) {
    }
}
