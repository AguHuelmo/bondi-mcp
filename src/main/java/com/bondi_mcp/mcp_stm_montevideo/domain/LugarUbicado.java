package com.bondi_mcp.mcp_stm_montevideo.domain;

/**
 * Un lugar conocido de Montevideo: el Estadio Centenario, la Terminal Tres Cruces, una facultad.
 *
 * <p>Es un candidato, no una certeza: quien lo pide tiene que decidir si de verdad es lo que se
 * buscó. Ver {@link com.bondi_mcp.mcp_stm_montevideo.client.Geocoder#buscarLugares(String)}.
 *
 * @param nombre cómo se llama en el padrón ("ESTADIO CENTENARIO")
 */
public record LugarUbicado(String nombre, Coordenada coordenada) {
}
