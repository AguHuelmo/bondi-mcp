package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;

/**
 * Arribos de una parada tal como los ve el frontend.
 *
 * <p>{@code lineasQuePasan} viene siempre, aunque no haya arribos: es lo que permite mostrar
 * "ahora no viene ninguno, pero por acá pasan la 149 y la 163".
 */
public record ArribosResponse(long codigoParada, List<ArriboResponse> arribos, List<String> lineasQuePasan) {

    public static ArribosResponse desde(ArribosDeParada arribos) {
        return new ArribosResponse(
                arribos.codigoParada(),
                arribos.arribos().stream().map(ArriboResponse::desde).toList(),
                arribos.lineasQuePasan());
    }
}
