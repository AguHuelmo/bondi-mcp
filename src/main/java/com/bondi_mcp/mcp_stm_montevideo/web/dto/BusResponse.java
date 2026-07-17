package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.BusEnVivo;

/** Un bus en circulación tal como lo ve el frontend. Solo llegan los que traen posición. */
public record BusResponse(Integer id, String linea, String destino, double latitud, double longitud) {

    public static BusResponse desde(BusEnVivo bus) {
        return new BusResponse(
                bus.id(),
                bus.linea(),
                bus.destino(),
                bus.ubicacion().latitud(),
                bus.ubicacion().longitud());
    }
}
