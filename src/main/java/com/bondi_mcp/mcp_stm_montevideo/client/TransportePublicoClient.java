package com.bondi_mcp.mcp_stm_montevideo.client;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.BusEnVivo;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;

/**
 * Puerto hacia la API de Transporte Público de la Intendencia de Montevideo.
 *
 * <p>Devuelve tipos del dominio, no el contrato externo: ningún detalle del formato de la
 * Intendencia se filtra hacia la capa de servicio. Cualquier fallo externo (red, auth, 5xx,
 * cambio de formato) sale como {@link TransportePublicoException} y nunca como una excepción
 * de la librería HTTP.
 */
public interface TransportePublicoClient {

    /**
     * Trae la colección completa de paradas del sistema.
     *
     * <p>La API no acepta filtros en este endpoint, así que esto es todo o nada; el resultado
     * se cachea localmente para poder buscar por texto.
     */
    List<Parada> obtenerTodasLasParadas();

    /** Líneas que pasan por una parada. */
    List<String> obtenerLineasDeParada(long codigoParada);

    /**
     * Próximos arribos a una parada, restringidos a las líneas indicadas.
     *
     * <p>La API exige el parámetro {@code lines}: no existe forma de pedir "todos los arribos
     * de esta parada" en una sola llamada.
     *
     * @param cantidadPorLinea cuántos buses devolver por cada línea
     */
    List<Arribo> obtenerProximosArribos(long codigoParada, List<String> lineas, int cantidadPorLinea);

    /**
     * Versión publicada del GTFS estático (ej. {@code "20260608"}).
     *
     * <p>Sirve para no rebajar ni reprocesar el zip si no cambió.
     */
    String obtenerVersionGtfs();

    /**
     * Baja el GTFS estático completo (zip de ~17 MB).
     *
     * <p>Es la única fuente de la relación línea↔parada: el endpoint que la daría está roto.
     */
    byte[] descargarGtfs();

    /**
     * Buses en circulación de las líneas indicadas, con su posición actual.
     *
     * <p>Trae los coches de toda la ciudad, en ambos sentidos: es para dibujar la línea en un
     * mapa, no para saber cuál viene a una parada (para eso están los arribos).
     */
    List<BusEnVivo> obtenerBusesDeLineas(List<String> lineas);
}
