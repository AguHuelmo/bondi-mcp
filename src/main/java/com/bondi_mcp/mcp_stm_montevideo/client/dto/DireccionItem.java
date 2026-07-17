package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.DireccionUbicada;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Una dirección como la devuelve {@code BusquedaDireccion} del servicio de direcciones del IDE.
 *
 * <p>{@code puntoX} es la longitud y {@code puntoY} la latitud, al revés que en el resto del
 * proyecto: por eso {@link #aDireccionUbicada()} las cruza al mapear a {@link Coordenada}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DireccionItem(Direccion direccion, Double puntoX, Double puntoY, Integer srid,
        String error) {

    /** El único SRID que sabemos leer: los grados decimales de siempre. */
    private static final int WGS84 = 4326;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Direccion(Calle calle, Numero numero) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Calle(@JsonProperty("nombre_normalizado") String nombreNormalizado) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Numero(@JsonProperty("nro_puerta") Integer nroPuerta) {
    }

    /**
     * {@code true} si el servicio encontró la puerta exacta que se pidió.
     *
     * <p>{@code error} es la única señal confiable, y hay que mirarla sí o sí: cuando la puerta no
     * existe el servicio no falla ni devuelve vacío, sino que "aproxima por calle" y contesta otra
     * puerta cualquiera —a veces de otra calle parecida— con
     * {@code error = "PUNTO NO ENCONTRADO. APROXIMADO POR CALLE"}. "Gabriel Pereira 99999" contesta
     * la puerta 3346, y "Calle Que No Existe 123" contesta "CALLE N 2570". En los dos casos
     * {@code idTipoClasificacion} vale 1, igual que en una respuesta buena, así que ese campo no
     * sirve para distinguirlas.
     *
     * <p>Se descartan también las aproximaciones que caen sobre la calle pedida (pedir la puerta
     * 3222 y que conteste la 3223, a metros de distancia): serían útiles, pero no hay forma de
     * separarlas de las inventadas sin volver a comparar nombres de calle a mano, que es
     * justamente lo que este servicio nos vino a sacar de encima. Ante la duda no ubicamos, y la
     * búsqueda por texto sigue respondiendo igual.
     */
    public boolean esExacta() {
        return (error == null || error.isBlank())
                && puntoX != null
                && puntoY != null
                // Si algún día contestaran en otra proyección, estos números no serían grados.
                && (srid == null || srid == WGS84)
                && direccion != null
                && direccion.calle() != null
                && direccion.calle().nombreNormalizado() != null
                && direccion.numero() != null
                && direccion.numero().nroPuerta() != null;
    }

    /** Solo tiene sentido sobre un item que ya pasó {@link #esExacta()}. */
    public DireccionUbicada aDireccionUbicada() {
        return new DireccionUbicada(
                direccion.calle().nombreNormalizado() + " " + direccion.numero().nroPuerta(),
                new Coordenada(puntoY, puntoX));
    }
}
