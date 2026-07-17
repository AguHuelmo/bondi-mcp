package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Item de {@code GET /buses?busstopId={id}}: un bus en circulación que pasa por esa parada.
 *
 * <p>Solo nos interesa {@code line}, para saber qué líneas pedirle después a
 * {@code upcomingbuses}. El resto de los campos se ignora.
 *
 * <p>Ojo: la respuesta real no coincide con el {@code VehicleItem} de la spec — trae
 * {@code company} en vez de {@code companyName}, más {@code eType} y {@code speed}. Otra razón
 * para mapear solo lo que necesitamos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusEnParadaItem(String line) {
}
