package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.BusEnVivo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Item de {@code GET /buses?lines=...}: un bus en circulación con su posición.
 *
 * <p>La spec declara {@code VehicleItem}, pero la respuesta real difiere (trae {@code company},
 * {@code eType} y {@code speed} que la spec no menciona, igual que en {@link BusEnParadaItem}):
 * mapeamos solo lo que usamos y el resto se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusItem(Integer busId, String line, String destination, GeoPoint location) {

    /** {@code true} si trae lo mínimo para pintarlo en un mapa: línea y coordenadas. */
    public boolean esUtilizable() {
        return line != null && !line.isBlank()
                && location != null && location.aCoordenada() != null;
    }

    public BusEnVivo aBusEnVivo() {
        return new BusEnVivo(busId, line, destination, location.aCoordenada());
    }
}
