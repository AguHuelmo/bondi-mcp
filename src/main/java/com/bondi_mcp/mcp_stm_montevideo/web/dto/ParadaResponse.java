package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;

/** Parada tal como la ve el frontend. */
public record ParadaResponse(long codigo, String descripcion, String calle, String esquina,
        Double latitud, Double longitud) {

    public static ParadaResponse desde(Parada parada) {
        return new ParadaResponse(
                parada.codigo(),
                parada.descripcion(),
                parada.calle(),
                parada.esquina(),
                parada.ubicacion() == null ? null : parada.ubicacion().latitud(),
                parada.ubicacion() == null ? null : parada.ubicacion().longitud());
    }
}
