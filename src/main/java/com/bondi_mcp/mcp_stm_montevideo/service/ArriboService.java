package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consulta de próximos arribos a una parada. Lógica compartida por MCP y REST.
 *
 * <p>La API de la Intendencia exige el parámetro {@code lines} en {@code upcomingbuses}: no
 * existe "dame todos los arribos de esta parada" en una sola llamada. Acá se encadenan las dos
 * consultas (primero qué líneas paran ahí, después los arribos de esas líneas) para que los
 * consumidores vean una sola operación simple.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArriboService {

    private static final int BUSES_POR_LINEA_POR_DEFECTO = 2;

    private final TransportePublicoClient client;

    /** Próximos arribos a una parada, sobre todas las líneas que pasan por ella. */
    public List<Arribo> proximosArribos(long codigoParada) {
        return proximosArribos(codigoParada, BUSES_POR_LINEA_POR_DEFECTO);
    }

    public List<Arribo> proximosArribos(long codigoParada, int cantidadPorLinea) {
        final List<String> lineas = client.obtenerLineasDeParada(codigoParada);
        if (lineas.isEmpty()) {
            log.debug("La parada {} no tiene líneas asociadas", codigoParada);
            return List.of();
        }
        return client.obtenerProximosArribos(codigoParada, lineas, cantidadPorLinea);
    }

    /** Próximos arribos filtrando por líneas puntuales (ej. solo la 116). */
    public List<Arribo> proximosArribos(long codigoParada, List<String> lineas, int cantidadPorLinea) {
        return client.obtenerProximosArribos(codigoParada, lineas, cantidadPorLinea);
    }
}
