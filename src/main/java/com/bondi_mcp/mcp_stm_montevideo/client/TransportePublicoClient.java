package com.bondi_mcp.mcp_stm_montevideo.client;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
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

    // Punto de extensión v1: posición en tiempo real de una línea.
    // La API ya lo expone con GET /buses?lines=... -> VehicleItem[] (id, location, timestamp,
    // line, origin, destination, companyName). No se implementa en v0.
    // List<Vehiculo> obtenerPosicionDeLinea(String linea);
}
