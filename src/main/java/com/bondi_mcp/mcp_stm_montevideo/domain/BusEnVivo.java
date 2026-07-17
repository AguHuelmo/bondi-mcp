package com.bondi_mcp.mcp_stm_montevideo.domain;

/**
 * Un ómnibus en circulación, con su posición actual.
 *
 * <p>Es una foto del momento: la posición vence en segundos y no tiene sentido persistirla.
 *
 * @param id      identificador del coche, o {@code null} si la API no lo informa; sirve para
 *                seguir al mismo bus entre dos consultas y verlo "moverse" en el mapa
 * @param linea   línea que está cubriendo
 * @param destino hacia dónde va, o {@code null} si no se informa
 */
public record BusEnVivo(Integer id, String linea, String destino, Coordenada ubicacion) {
}
