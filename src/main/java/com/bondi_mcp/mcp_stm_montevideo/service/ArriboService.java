package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaLineaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consulta de próximos arribos a una parada. Lógica compartida por MCP y REST.
 *
 * <p>La API de la Intendencia exige el parámetro {@code lines} en {@code upcomingbuses}: no
 * existe "dame todos los arribos de esta parada" en una sola llamada. Acá se encadenan las dos
 * consultas para que los consumidores vean una sola operación simple.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArriboService {

    private static final int BUSES_POR_LINEA_POR_DEFECTO = 2;

    private final TransportePublicoClient client;
    private final ParadaLineaRepository paradaLineaRepository;
    private final PuntualidadService puntualidadService;

    /**
     * Próximos arribos a una parada, más las líneas que pasan por ella.
     *
     * <p>Las líneas a consultar salen de los buses en circulación y no del GTFS, a propósito: solo
     * una línea con buses en la calle puede producir un arribo, y mandarle a la API una línea que
     * no reconoce hace que responda 400 y se caiga toda la consulta.
     *
     * <p>El listado de líneas que pasan, en cambio, sale del GTFS: tiene que estar completo aunque
     * no venga ningún bus.
     */
    @Transactional(readOnly = true)
    public ArribosDeParada proximosArribos(long codigoParada) {
        return proximosArribos(codigoParada, BUSES_POR_LINEA_POR_DEFECTO);
    }

    @Transactional(readOnly = true)
    public ArribosDeParada proximosArribos(long codigoParada, int cantidadPorLinea) {
        final List<String> lineasQuePasan = paradaLineaRepository.lineasDeParada(codigoParada);
        final List<String> enCirculacion = client.obtenerLineasDeParada(codigoParada);

        if (enCirculacion.isEmpty()) {
            log.debug("La parada {} no tiene buses circulando", codigoParada);
            return new ArribosDeParada(codigoParada, List.of(), lineasQuePasan);
        }

        final List<Arribo> arribos = client.obtenerProximosArribos(codigoParada, enCirculacion, cantidadPorLinea);

        // Cada consulta alimenta el historial de esperas, gratis: los datos ya vinieron. En
        // transacción propia y con el fallo tragado, porque el historial es un subproducto y el
        // que pregunta cuándo viene el bondi no puede pagar sus platos rotos.
        try {
            puntualidadService.registrar(codigoParada, arribos);
        }
        catch (RuntimeException ex) {
            log.debug("No se pudo registrar la observación de la parada {}: {}", codigoParada,
                    ex.getMessage());
        }

        return new ArribosDeParada(codigoParada, arribos, lineasQuePasan);
    }

    /** Solo las líneas que pasan por una parada, según el GTFS. */
    @Transactional(readOnly = true)
    public List<String> lineasQuePasan(long codigoParada) {
        return paradaLineaRepository.lineasDeParada(codigoParada);
    }
}
