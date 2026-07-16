package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item de {@code GET /buses/busstops}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusStopItem(
        Long busstopId,
        String street1,
        String street2,
        Long street1Id,
        Long street2Id,
        GeoPoint location) {

    public Parada aParada() {
        return new Parada(
                busstopId,
                street1 == null ? "" : street1.trim(),
                street2 == null ? null : street2.trim(),
                location == null ? null : location.aCoordenada());
    }
}
