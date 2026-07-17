package com.bondi_mcp.mcp_stm_montevideo.domain;

/**
 * Una dirección con número de puerta, ya ubicada en el mapa.
 *
 * @param direccionOficial cómo figura en el padrón ("GABRIEL A. PEREIRA 2470"), que casi nunca es
 *                         como la escribió el usuario ("gabriel pereira 2470")
 * @param coordenada       dónde queda esa puerta
 */
public record DireccionUbicada(String direccionOficial, Coordenada coordenada) {
}
