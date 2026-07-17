package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.RecorridoDeLinea;

/** El recorrido de una línea tal como lo ve el frontend: un sentido por dirección. */
public record RecorridoLineaResponse(String linea, List<SentidoResponse> sentidos) {

    public static RecorridoLineaResponse desde(RecorridoDeLinea recorrido) {
        return new RecorridoLineaResponse(
                recorrido.linea(),
                recorrido.sentidos().stream().map(SentidoResponse::desde).toList());
    }

    /** Un sentido, nombrado por su última parada ("hacia Portones"). */
    public record SentidoResponse(String destino, List<ParadaResponse> paradas) {

        static SentidoResponse desde(RecorridoDeLinea.Sentido sentido) {
            return new SentidoResponse(
                    sentido.destino(),
                    sentido.paradas().stream().map(ParadaResponse::desde).toList());
        }
    }
}
