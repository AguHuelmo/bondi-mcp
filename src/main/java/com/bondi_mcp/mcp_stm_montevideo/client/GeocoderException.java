package com.bondi_mcp.mcp_stm_montevideo.client;

/**
 * Falla del servicio de geocodificación: red, timeout, 5xx o un formato que no entendemos.
 *
 * <p>No significa "esa dirección no existe" (eso es un {@code Optional} vacío), sino "no pudimos
 * preguntar".
 */
public class GeocoderException extends RuntimeException {

    public GeocoderException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
