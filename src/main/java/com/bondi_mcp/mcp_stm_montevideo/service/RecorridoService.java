package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.RecorridoDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoRepository;

import lombok.RequiredArgsConstructor;

/**
 * El recorrido de una línea, para dibujarlo.
 *
 * <p>Trabaja sobre los recorridos del GTFS, como {@link ViajeService}, pero responde otra
 * pregunta: no "qué línea me lleva" sino "por dónde va esta línea".
 */
@Service
@RequiredArgsConstructor
public class RecorridoService {

    private final RecorridoRepository recorridoRepository;
    private final ParadaRepository paradaRepository;

    /**
     * El recorrido completo de una línea, un sentido por dirección.
     *
     * <p>Una línea tiene varias variantes por sentido (salidas cortas, desvíos); se muestra la
     * de más paradas, que es el recorrido entero. Una línea desconocida no es un error: devuelve
     * sentidos vacíos y quien consume lo explica.
     */
    @Transactional
    public RecorridoDeLinea recorridoDe(String linea) {
        final String normalizada = normalizar(linea);

        final Map<String, RecorridoRepository.VarianteDeLinea> masLargaPorSentido = new LinkedHashMap<>();
        for (final var variante : recorridoRepository.variantesDeLinea(normalizada)) {
            final String sentido = variante.getDireccion() == null ? "" : variante.getDireccion();
            masLargaPorSentido.merge(sentido, variante,
                    (actual, otra) -> otra.getCantidadParadas() > actual.getCantidadParadas() ? otra : actual);
        }

        final List<RecorridoDeLinea.Sentido> sentidos = masLargaPorSentido.values().stream()
                .map(variante -> aSentido(recorridoRepository.paradasDeRecorrido(variante.getId())))
                .filter(sentido -> !sentido.paradas().isEmpty())
                .toList();

        return new RecorridoDeLinea(normalizada, sentidos);
    }

    /**
     * Las paradas de un tramo subida→bajada de una línea, en orden.
     *
     * <p>Es lo que convierte el trazo recto del planificador en el camino real. Vacía si la línea
     * no une esas dos paradas en ese orden; el que dibuja vuelve al trazo recto.
     */
    @Transactional
    public List<Parada> tramoDe(String linea, long subida, long bajada) {
        return recorridoRepository.tramoEnRecorrido(normalizar(linea), subida, bajada)
                .map(tramo -> enOrden(recorridoRepository.paradasEntre(
                        tramo.getRecorridoId(), tramo.getDesde(), tramo.getHasta())))
                .orElse(List.of());
    }

    private RecorridoDeLinea.Sentido aSentido(List<Long> codigos) {
        final List<Parada> paradas = enOrden(codigos);
        final String destino = paradas.isEmpty() ? "" : paradas.getLast().descripcion();
        return new RecorridoDeLinea.Sentido(destino, paradas);
    }

    /** Carga las paradas respetando el orden de los códigos; las que no están en la base se saltean. */
    private List<Parada> enOrden(List<Long> codigos) {
        final Map<Long, Parada> porCodigo = new HashMap<>();
        paradaRepository.findAllById(codigos).forEach(entidad -> {
            final Parada parada = entidad.aParada();
            porCodigo.put(parada.codigo(), parada);
        });
        return codigos.stream().map(porCodigo::get).filter(Objects::nonNull).toList();
    }

    /** En mayúscula como está en la base; el usuario puede escribir "ce1". */
    private static String normalizar(String linea) {
        return linea.trim().toUpperCase(Locale.ROOT);
    }
}
