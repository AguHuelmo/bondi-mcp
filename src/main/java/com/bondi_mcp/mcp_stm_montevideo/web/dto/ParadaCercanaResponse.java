package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;

/** Parada con su distancia al punto consultado. */
public record ParadaCercanaResponse(long codigo, String descripcion, Double latitud, Double longitud,
        int distanciaMetros, String distanciaLegible) {

    public static ParadaCercanaResponse desde(ParadaCercana cercana) {
        final var parada = cercana.parada();
        return new ParadaCercanaResponse(
                parada.codigo(),
                parada.descripcion(),
                parada.ubicacion() == null ? null : parada.ubicacion().latitud(),
                parada.ubicacion() == null ? null : parada.ubicacion().longitud(),
                cercana.distanciaMetros(),
                cercana.distanciaLegible());
    }
}
