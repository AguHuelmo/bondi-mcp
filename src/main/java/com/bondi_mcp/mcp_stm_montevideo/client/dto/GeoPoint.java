package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GeoJSON Point tal como lo devuelve la Intendencia.
 *
 * <p>Ojo con el orden: {@code coordinates} viene [longitud, latitud], no al revés.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeoPoint(String type, List<Double> coordinates) {

    public GeoPoint {
        coordinates = coordinates == null ? List.of() : List.copyOf(coordinates);
    }

    /** {@code null} si el punto no trae coordenadas usables, en vez de reventar. */
    public Coordenada aCoordenada() {
        if (coordinates.size() < 2) {
            return null;
        }
        return new Coordenada(coordinates.get(1), coordinates.get(0));
    }
}
