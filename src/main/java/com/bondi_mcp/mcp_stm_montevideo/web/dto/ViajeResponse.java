package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;

/** Una forma de hacer el viaje, tal como la ve el frontend. */
public record ViajeResponse(List<String> lineas, int transbordos, List<TramoResponse> tramos,
        int metrosCaminando) {

    public static ViajeResponse desde(Viaje viaje) {
        return new ViajeResponse(
                viaje.lineas(),
                viaje.transbordos(),
                viaje.tramos().stream().map(TramoResponse::desde).toList(),
                viaje.metrosCaminando());
    }

    /**
     * Un tramo arriba de una línea.
     *
     * <p>Las paradas van enteras y no solo su código: el frontend las dibuja en el mapa y necesita
     * las coordenadas.
     */
    public record TramoResponse(String linea, ParadaResponse subida, ParadaResponse bajada) {

        public static TramoResponse desde(Viaje.Tramo tramo) {
            return new TramoResponse(
                    tramo.linea(),
                    ParadaResponse.desde(tramo.subida()),
                    ParadaResponse.desde(tramo.bajada()));
        }
    }
}
