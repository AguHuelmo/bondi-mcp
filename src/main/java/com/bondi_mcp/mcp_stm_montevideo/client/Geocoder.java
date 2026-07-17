package com.bondi_mcp.mcp_stm_montevideo.client;

import java.util.List;
import java.util.Optional;

import com.bondi_mcp.mcp_stm_montevideo.domain.DireccionUbicada;
import com.bondi_mcp.mcp_stm_montevideo.domain.LugarUbicado;

/**
 * Puerto hacia un servicio de geocodificación de direcciones de Montevideo.
 *
 * <p>Hace falta porque la API de Transporte Público no ubica direcciones: solo publica paradas.
 * Sin esto, "Gabriel Pereira 2470" no se puede convertir en un punto y la única forma de buscar
 * es por cruce de calles.
 *
 * <p>Cualquier fallo externo sale como {@link GeocoderException} y nunca como una excepción de la
 * librería HTTP.
 */
public interface Geocoder {

    /**
     * Ubica una dirección con número de puerta ("Gabriel Pereira 2470") en Montevideo.
     *
     * <p>Tolera que la calle esté mal escrita o incompleta: el servicio la resuelve contra el
     * padrón ("gabriel pereira" → "GABRIEL A. PEREIRA").
     *
     * @return la dirección ubicada, o vacío si esa puerta no existe
     */
    Optional<DireccionUbicada> ubicar(String direccion);

    /**
     * Lugares conocidos de Montevideo cuyo nombre se parece al pedido ("estadio centenario",
     * "terminal tres cruces").
     *
     * <p>Devuelve candidatos y no un resultado, al revés que {@link #ubicar(String)}, y la
     * diferencia es a propósito: el servicio de direcciones avisa cuándo no encontró la puerta
     * exacta, pero el de lugares matchea flojo y no avisa nada. Pedirle "18 de julio" contesta
     * una sucursal del BROU en Minas, con la misma pinta de acierto que cualquier otro resultado.
     * Decidir a cuál creerle no es algo que se pueda resolver acá adentro: depende de qué se
     * buscó.
     *
     * @return los candidatos, de mejor a peor, o vacío si no hay ninguno
     */
    List<LugarUbicado> buscarLugares(String nombre);
}
