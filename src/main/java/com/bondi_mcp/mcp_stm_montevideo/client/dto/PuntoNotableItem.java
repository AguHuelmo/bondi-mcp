package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.LugarUbicado;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un punto de interés como lo devuelve {@code direcPuntoNotable} del servicio del IDE.
 *
 * <p>Ojo con la forma: este endpoint es de la v1 y trae {@code lat}/{@code lng} directo, mientras
 * que el de direcciones es de la v0 y trae {@code puntoY}/{@code puntoX}. Son el mismo servicio y
 * los dos hablan WGS84, pero no comparten contrato; por eso son dos DTO y no uno.
 *
 * <p>{@code inmueble} es el nombre del lugar ("ESTADIO CENTENARIO") y {@code address} lo repite
 * junto a la calle. Se usa {@code inmueble}: es el nombre solo, que es contra lo que hay que
 * comparar lo que buscó el usuario.
 *
 * <p>Este contrato no tiene forma de avisar que el match es flojo: {@code state} vale 1 y
 * {@code stateMsg} viene vacío tanto para "ESTADIO CENTENARIO" (que es lo pedido) como para el
 * "BROU 19 DE JUNIO" que contesta cuando se le pide "18 de julio". No hay acá un equivalente al
 * {@code error} de {@link DireccionItem}: filtrar lo que no corresponde es responsabilidad de
 * quien llama.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PuntoNotableItem(String inmueble, Double lat, Double lng) {

    public boolean esUtilizable() {
        return inmueble != null && !inmueble.isBlank() && lat != null && lng != null;
    }

    /** Solo tiene sentido sobre un item que ya pasó {@link #esUtilizable()}. */
    public LugarUbicado aLugarUbicado() {
        return new LugarUbicado(inmueble, new Coordenada(lat, lng));
    }
}
