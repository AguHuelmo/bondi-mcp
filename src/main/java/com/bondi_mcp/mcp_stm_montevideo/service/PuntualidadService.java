package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.HistorialDeEsperas;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ObservacionDeArriboDao;

import lombok.RequiredArgsConstructor;

/**
 * El historial de esperas observadas: se alimenta solo y responde con agregados.
 *
 * <p>La escritura cuelga de cada consulta de arribos ({@link ArriboService}), así que el dataset
 * crece con el uso normal — cada usuario, cada alerta de la guardia, cada agente MCP que
 * pregunta "¿cuándo viene?" deja una medición. Sin ninguna llamada extra a la Intendencia.
 */
@Service
@RequiredArgsConstructor
public class PuntualidadService {

    /** Una espera de más de dos horas es basura del feed, no un dato. */
    private static final int MAXIMA_ESPERA_CREIBLE = 120;

    /** Ventana diurna de referencia para la espera teórica: 07:00–22:00. */
    private static final int MINUTOS_DIURNOS = 15 * 60;

    private static final List<String> FRANJAS =
            List.of("madrugada", "mañana", "tarde", "noche");

    private final ObservacionDeArriboDao observacionDao;
    private final HorarioTeoricoDao horarioTeoricoDao;

    /**
     * Registra lo que el tiempo real dijo para una parada.
     *
     * <p>En transacción propia: la consulta de arribos que la dispara es de solo lectura, y un
     * fallo acá jamás debe voltearla — el que pregunta cuándo viene el bondi no tiene por qué
     * pagar los platos rotos del historial.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(long codigoParada, List<Arribo> arribos) {
        final List<Arribo> creibles = arribos.stream()
                .filter(arribo -> arribo.esperaEnMinutos() >= 0
                        && arribo.esperaEnMinutos() <= MAXIMA_ESPERA_CREIBLE)
                .toList();
        if (!creibles.isEmpty()) {
            observacionDao.insertar(Instant.now(), codigoParada, creibles);
        }
    }

    /** El historial de una línea, opcionalmente acotado a una parada. */
    @Transactional(readOnly = true)
    public HistorialDeEsperas historialDe(String linea, Long codigoParada) {
        final String normalizada = linea.trim().toUpperCase(Locale.ROOT);

        final Optional<ObservacionDeArriboDao.Resumen> resumen =
                observacionDao.resumir(normalizada, codigoParada);
        if (resumen.isEmpty()) {
            return HistorialDeEsperas.vacio(normalizada, codigoParada);
        }

        final List<HistorialDeEsperas.Franja> franjas =
                observacionDao.porFranja(normalizada, codigoParada).stream()
                        .map(fila -> new HistorialDeEsperas.Franja(
                                FRANJAS.get(fila.franja()),
                                fila.observaciones(),
                                (int) Math.round(fila.esperaMedia())))
                        .toList();

        final ObservacionDeArriboDao.Resumen datos = resumen.get();
        return new HistorialDeEsperas(
                normalizada,
                codigoParada,
                datos.observaciones(),
                datos.desde().atZone(HorarioTeoricoService.ZONA_MONTEVIDEO).toLocalDate(),
                datos.hasta().atZone(HorarioTeoricoService.ZONA_MONTEVIDEO).toLocalDate(),
                (int) Math.round(datos.esperaMedia()),
                (int) Math.round(datos.esperaMediana()),
                (int) Math.round(datos.esperaP90()),
                esperaTeorica(normalizada, codigoParada),
                franjas);
    }

    /**
     * La espera media que promete el papel: la mitad del intervalo entre salidas diurnas.
     *
     * <p>Solo tiene sentido por parada (los horarios son por parada); sin horarios importados
     * queda en {@code null}, no en un invento.
     */
    private Integer esperaTeorica(String linea, Long codigoParada) {
        if (codigoParada == null) {
            return null;
        }
        return horarioTeoricoDao.frecuencias(List.of(codigoParada)).stream()
                .filter(frecuencia -> frecuencia.linea().equals(linea))
                .filter(frecuencia -> frecuencia.salidasDiurnasHabil() > 0)
                .findFirst()
                .map(frecuencia -> Math.max(1, Math.round(
                        (float) MINUTOS_DIURNOS / frecuencia.salidasDiurnasHabil() / 2)))
                .orElse(null);
    }
}
