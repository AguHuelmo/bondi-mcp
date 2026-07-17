package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;

/** Próximo arribo tal como lo ve el frontend. La posición del bus es para el mapa. */
public record ArriboResponse(String linea, String destino, long esperaEnMinutos,
        Integer distanciaMetros, String empresa, Double latitud, Double longitud) {

    public static ArriboResponse desde(Arribo arribo) {
        return new ArriboResponse(
                arribo.linea(),
                arribo.destino(),
                arribo.esperaEnMinutos(),
                arribo.distanciaMetros(),
                arribo.empresa(),
                arribo.ubicacion() == null ? null : arribo.ubicacion().latitud(),
                arribo.ubicacion() == null ? null : arribo.ubicacion().longitud());
    }
}
