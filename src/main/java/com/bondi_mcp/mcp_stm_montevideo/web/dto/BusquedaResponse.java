package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;

/**
 * Resultado de buscar paradas.
 *
 * @param cercanasAlPunto solo viene cuando no hay ninguna parada en el lugar pedido pero sí se
 *                        pudo ubicar dónde queda: son las alternativas a ofrecerle al usuario
 * @param punto           dónde queda el lugar pedido, para poder mostrarlo junto a esas paradas;
 *                        {@code null} si no se pudo ubicar o si no hizo falta
 */
public record BusquedaResponse(List<ParadaResponse> paradas, boolean soloAproximadas,
        List<ParadaCercanaResponse> cercanasAlPunto, PuntoResponse punto) {

    public static BusquedaResponse desde(ResultadoBusqueda resultado) {
        return new BusquedaResponse(
                resultado.paradas().stream().map(ParadaResponse::desde).toList(),
                resultado.soloAproximadas(),
                resultado.cercanasAlPunto().stream().map(ParadaCercanaResponse::desde).toList(),
                resultado.punto() == null ? null : PuntoResponse.desde(resultado.punto()));
    }
}
