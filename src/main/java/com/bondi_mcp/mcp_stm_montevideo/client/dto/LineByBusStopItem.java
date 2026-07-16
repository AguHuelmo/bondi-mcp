package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Item de {@code GET /buses/busstops/{busstopId}/lines}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineByBusStopItem(String line, String lineId) {
}
