package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.time.Duration;

/**
 * Próxima llegada de un ómnibus a una parada.
 *
 * @param linea            nombre de la línea (ej. "116")
 * @param destino          destino de la variante
 * @param espera           tiempo estimado de arribo
 * @param distanciaMetros  distancia del bus a la parada, o {@code null} si no se informa
 * @param empresa          empresa operadora, o {@code null} si no se informa
 */
public record Arribo(String linea, String destino, Duration espera, Integer distanciaMetros, String empresa) {

    public long esperaEnMinutos() {
        return espera.toMinutes();
    }
}
