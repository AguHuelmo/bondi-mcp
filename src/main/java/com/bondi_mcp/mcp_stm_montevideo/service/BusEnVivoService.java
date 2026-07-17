package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.domain.BusEnVivo;

import lombok.RequiredArgsConstructor;

/**
 * Dónde están los coches de una línea ahora mismo.
 *
 * <p>Complementa a {@link ArriboService}: los arribos dicen cuándo llega el bus a UNA parada;
 * esto muestra la línea entera en el mapa, en ambos sentidos, para verla moverse.
 */
@Service
@RequiredArgsConstructor
public class BusEnVivoService {

    private final TransportePublicoClient client;

    public List<BusEnVivo> deLinea(String linea) {
        // En mayúscula: la API es case-sensitive y rechaza "ce1" con un 400.
        return client.obtenerBusesDeLineas(List.of(linea.trim().toUpperCase(Locale.ROOT)));
    }
}
