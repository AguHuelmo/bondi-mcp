package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.List;

/**
 * Respuesta completa de una consulta de arribos.
 *
 * <p>{@code lineasQuePasan} sale del GTFS y está siempre, haya o no arribos: cuando no viene
 * ningún bus, saber qué líneas paran ahí es justamente lo que uno necesita para decidir si
 * esperar o caminar hasta otra parada.
 *
 * @param arribos        próximos buses, ordenados por tiempo de espera; vacío si no viene ninguno
 * @param lineasQuePasan todas las líneas de la parada; vacío si el GTFS todavía no se importó
 */
public record ArribosDeParada(long codigoParada, List<Arribo> arribos, List<String> lineasQuePasan) {

    public ArribosDeParada {
        arribos = List.copyOf(arribos);
        lineasQuePasan = List.copyOf(lineasQuePasan);
    }

    public boolean hayArribos() {
        return !arribos.isEmpty();
    }
}
