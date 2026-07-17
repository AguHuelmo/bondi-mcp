package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cómo ir de un punto a otro en ómnibus.
 *
 * <p>Trabaja sobre los recorridos del GTFS, no sobre el tiempo real: responde "qué línea te lleva",
 * no "cuándo pasa". Para lo segundo está {@link ArriboService}.
 *
 * <p>Nunca alcanza con cruzar las líneas de las dos paradas. La 62 pasa por Gabriel Pereira y por
 * 18 de Julio, pero en viajes distintos —son los dos sentidos—, así que "está en las dos listas"
 * no implica "te lleva de una a la otra". Por eso todo sale de recorridos ordenados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViajeService {

    /**
     * Radio alrededor del origen y del destino donde se aceptan paradas.
     *
     * <p>Nadie pide un viaje "desde esta parada exacta": pide desde una esquina. Las paradas de
     * las dos veredas y las de la cuadra siguiente sirven igual, y sin esto un cruce sin parada
     * propia no tendría ningún viaje.
     */
    private static final int RADIO_PUNTA_METROS = 400;

    /** Cuánto se acepta caminar para hacer un transbordo. */
    private static final int TRANSBORDO_METROS = 300;

    /** Caja de prefiltro para el transbordo: ~0,004° cubre holgado los 300 m del haversine. */
    private static final double GRADOS_CAJA_TRANSBORDO = 0.004;

    /** Tope de candidatos por punta: acota el IN de la consulta y la explosión del transbordo. */
    private static final int PARADAS_POR_PUNTA = 12;

    /** Cuántos viajes devolver. */
    private static final int MAXIMOS_VIAJES = 5;

    /** Tope de filas del transbordo antes de rankear; la consulta ya las trae ordenadas. */
    private static final int CANDIDATOS_TRANSBORDO = 200;

    private final RecorridoRepository recorridoRepository;
    private final ParadaRepository paradaRepository;
    private final ParadaService paradaService;

    /**
     * Busca cómo ir de un punto a otro.
     *
     * <p>Primero directos. Solo si no hay ninguno busca con transbordo: un viaje directo siempre
     * le gana a uno con cambio, así que no tiene sentido pagar la consulta cara al pedo.
     */
    @Transactional
    public List<Viaje> comoLlegar(Coordenada origen, Coordenada destino) {
        final List<ParadaCercana> desde = paradaService.cercanasA(origen, PARADAS_POR_PUNTA).stream()
                .filter(p -> p.distanciaMetros() <= RADIO_PUNTA_METROS)
                .toList();
        final List<ParadaCercana> hasta = paradaService.cercanasA(destino, PARADAS_POR_PUNTA).stream()
                .filter(p -> p.distanciaMetros() <= RADIO_PUNTA_METROS)
                .toList();

        if (desde.isEmpty() || hasta.isEmpty()) {
            log.debug("Sin paradas a menos de {} m de alguna de las dos puntas", RADIO_PUNTA_METROS);
            return List.of();
        }

        final Map<Long, Integer> caminataDesde = aCaminata(desde);
        final Map<Long, Integer> caminataHasta = aCaminata(hasta);

        final List<Viaje> directos = buscarDirectos(caminataDesde, caminataHasta);
        if (!directos.isEmpty()) {
            return directos;
        }
        return buscarConTransbordo(caminataDesde, caminataHasta);
    }

    private List<Viaje> buscarDirectos(Map<Long, Integer> desde, Map<Long, Integer> hasta) {
        final Map<Long, Parada> paradas = cargarParadas(List.copyOf(desde.keySet()), List.copyOf(hasta.keySet()));

        return recorridoRepository.viajesDirectos(List.copyOf(desde.keySet()), List.copyOf(hasta.keySet()))
                .stream()
                .filter(t -> paradas.containsKey(t.getParadaSubida()) && paradas.containsKey(t.getParadaBajada()))
                .map(t -> new Viaje(
                        List.of(new Viaje.Tramo(t.getLinea(), paradas.get(t.getParadaSubida()),
                                paradas.get(t.getParadaBajada()))),
                        desde.get(t.getParadaSubida()) + hasta.get(t.getParadaBajada())))
                // Una línea puede servir por varias combinaciones de paradas; nos quedamos con la
                // que menos hace caminar y no repetimos la línea en la respuesta.
                .sorted(Comparator.comparingInt(Viaje::metrosCaminando))
                .collect(LinkedHashMap<String, Viaje>::new,
                        (mapa, viaje) -> mapa.putIfAbsent(viaje.lineas().getFirst(), viaje),
                        LinkedHashMap::putAll)
                .values().stream()
                .limit(MAXIMOS_VIAJES)
                .toList();
    }

    private List<Viaje> buscarConTransbordo(Map<Long, Integer> desde, Map<Long, Integer> hasta) {
        final var candidatos = recorridoRepository.viajesConTransbordo(
                List.copyOf(desde.keySet()), List.copyOf(hasta.keySet()),
                TRANSBORDO_METROS, GRADOS_CAJA_TRANSBORDO, Limit.of(CANDIDATOS_TRANSBORDO));

        if (candidatos.isEmpty()) {
            return List.of();
        }

        final List<Long> codigos = candidatos.stream()
                .flatMap(c -> List.of(c.getParadaSubida(), c.getParadaTransbordoBaja(),
                        c.getParadaTransbordoSube(), c.getParadaBajada()).stream())
                .distinct()
                .toList();
        final Map<Long, Parada> paradas = cargarParadas(codigos, List.of());

        return candidatos.stream()
                .filter(c -> paradas.keySet().containsAll(List.of(c.getParadaSubida(),
                        c.getParadaTransbordoBaja(), c.getParadaTransbordoSube(), c.getParadaBajada())))
                .map(c -> new Viaje(
                        List.of(
                                new Viaje.Tramo(c.getLinea1(), paradas.get(c.getParadaSubida()),
                                        paradas.get(c.getParadaTransbordoBaja())),
                                new Viaje.Tramo(c.getLinea2(), paradas.get(c.getParadaTransbordoSube()),
                                        paradas.get(c.getParadaBajada()))),
                        desde.get(c.getParadaSubida())
                                + (int) Math.round(c.getMetrosCaminando())
                                + hasta.get(c.getParadaBajada())))
                .sorted(Comparator.comparingInt(Viaje::metrosCaminando))
                // Sin esto la respuesta serían veinte variantes del mismo par de líneas.
                .collect(LinkedHashMap<String, Viaje>::new,
                        (mapa, viaje) -> mapa.putIfAbsent(String.join(">", viaje.lineas()), viaje),
                        LinkedHashMap::putAll)
                .values().stream()
                .limit(MAXIMOS_VIAJES)
                .toList();
    }

    /** Caminata en metros hasta cada parada de una punta. */
    private static Map<Long, Integer> aCaminata(List<ParadaCercana> cercanas) {
        final Map<Long, Integer> metros = new LinkedHashMap<>();
        cercanas.forEach(c -> metros.put(c.parada().codigo(), c.distanciaMetros()));
        return metros;
    }

    private Map<Long, Parada> cargarParadas(List<Long> unos, List<Long> otros) {
        final List<Long> todos = java.util.stream.Stream.concat(unos.stream(), otros.stream())
                .distinct()
                .toList();
        final Map<Long, Parada> paradas = new LinkedHashMap<>();
        paradaRepository.findAllById(todos).forEach(entidad -> {
            final Parada parada = entidad.aParada();
            paradas.put(parada.codigo(), parada);
        });
        return paradas;
    }

    /**
     * Resuelve un texto ("18 de julio y ejido", "gabriel pereira 2470") a un punto, usando la
     * búsqueda de paradas.
     *
     * <p>Cuando la búsqueda ubicó el lugar pedido, ese punto le gana a la primera parada que
     * matcheó por texto: la parada es una coincidencia de palabras y puede estar a cuadras del
     * lugar, mientras que el punto es la dirección del padrón o el cruce estimado. Para un viaje
     * la diferencia se paga caminando.
     */
    @Transactional
    public Optional<Coordenada> ubicar(String texto) {
        final ResultadoBusqueda resultado = paradaService.buscar(texto);
        if (resultado.punto() != null) {
            return Optional.of(resultado.punto().coordenada());
        }
        return resultado.paradas().stream()
                .map(Parada::ubicacion)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }
}
