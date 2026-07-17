package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import java.util.List;

/**
 * Una secuencia ordenada de paradas de una línea, tal como sale del GTFS.
 *
 * @param linea     nombre de la línea, en MAYÚSCULA
 * @param direccion {@code direction_id} del GTFS: separa ida de vuelta
 * @param paradas   códigos de parada en el orden en que las recorre
 */
public record RecorridoGtfs(String linea, String direccion, List<Long> paradas) {

    public RecorridoGtfs {
        paradas = List.copyOf(paradas);
    }
}
