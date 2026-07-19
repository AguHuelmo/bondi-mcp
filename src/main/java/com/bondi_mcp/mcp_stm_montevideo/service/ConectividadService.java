package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.domain.Conectividad;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaLineaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoRepository;

import lombok.RequiredArgsConstructor;

/**
 * El índice de conectividad: mide qué tan bien servido por ómnibus está un punto.
 *
 * <p>Cuatro componentes, todos calculados con datos propios:
 *
 * <ul>
 * <li><b>Cercanía (0–25)</b>: a cuántos metros queda la parada más próxima. 25 puntos hasta los
 * 100 m, bajando en línea recta a 0 en los 400 m (el radio que ya usa el planificador).
 * <li><b>Variedad (0–25)</b>: cuántas líneas distintas sirven la zona, 2,5 puntos por línea.
 * <li><b>Frecuencia (0–30)</b>: cada cuántos minutos sale un bondi (el que sea) en un día hábil
 * de 07:00 a 22:00. 30 puntos hasta 4 minutos de espera, 0 desde los 30.
 * <li><b>Alcance (0–20)</b>: a qué parte de la ciudad se llega sin transbordo. Puntaje completo
 * desde el 35% de las paradas.
 * </ul>
 *
 * <p>Cada línea se cuenta en su <b>mejor</b> parada cercana y no en todas: las paradas de las dos
 * veredas y las consecutivas repiten el mismo servicio, y sumarlas inflaría la frecuencia.
 */
@Service
@RequiredArgsConstructor
public class ConectividadService {

    /** Radio caminable, el mismo que acepta el planificador para las puntas de un viaje. */
    private static final int RADIO_METROS = 400;

    private static final int MAXIMAS_PARADAS = 12;

    /** La ventana diurna de un día hábil: 07:00 a 22:00, quince horas. */
    private static final int MINUTOS_DIURNOS = 15 * 60;

    private final ParadaService paradaService;
    private final ParadaLineaRepository paradaLineaRepository;
    private final HorarioTeoricoDao horarioTeoricoDao;
    private final RecorridoRepository recorridoRepository;
    private final ParadaRepository paradaRepository;

    /** Mide la conectividad de un punto. Nunca falla: sin paradas cerca devuelve "sin servicio". */
    @Transactional
    public Conectividad medir(Coordenada punto) {
        final List<ParadaCercana> cercanas = paradaService.cercanasA(punto, MAXIMAS_PARADAS).stream()
                .filter(cercana -> cercana.distanciaMetros() <= RADIO_METROS)
                .toList();
        if (cercanas.isEmpty()) {
            return Conectividad.sinServicio();
        }

        final List<Long> codigos = cercanas.stream().map(c -> c.parada().codigo()).toList();
        final int distanciaMinima = cercanas.getFirst().distanciaMetros();

        // La mejor parada de cada línea: máximo, no suma, para no contar dos veces las dos
        // veredas del mismo recorrido.
        final Map<String, Integer> semanalesPorLinea = new HashMap<>();
        final Map<String, Integer> diurnasPorLinea = new HashMap<>();
        final Map<String, Integer> nocturnasPorLinea = new HashMap<>();
        for (final HorarioTeoricoDao.Frecuencia frecuencia : horarioTeoricoDao.frecuencias(codigos)) {
            semanalesPorLinea.merge(frecuencia.linea(), frecuencia.salidasSemanales(), Math::max);
            diurnasPorLinea.merge(frecuencia.linea(), frecuencia.salidasDiurnasHabil(), Math::max);
            nocturnasPorLinea.merge(frecuencia.linea(), frecuencia.salidasNocturnasSemanales(), Math::max);
        }

        // Sin horarios importados igual se responde: las líneas salen del GTFS de recorridos y
        // la frecuencia queda honestamente en "no sé" (null).
        final TreeSet<String> lineas = new TreeSet<>(semanalesPorLinea.keySet());
        if (lineas.isEmpty()) {
            codigos.forEach(codigo -> lineas.addAll(paradaLineaRepository.lineasDeParada(codigo)));
        }

        final int salidasSemanales = semanalesPorLinea.values().stream().mapToInt(Integer::intValue).sum();
        final int salidasDiurnas = diurnasPorLinea.values().stream().mapToInt(Integer::intValue).sum();
        final int nocturnas = nocturnasPorLinea.values().stream().mapToInt(Integer::intValue).sum();
        final Integer esperaMedia = salidasDiurnas == 0
                ? null
                : Math.max(1, Math.round((float) MINUTOS_DIURNOS / salidasDiurnas));

        final long alcanzables = recorridoRepository.paradasAlcanzablesDesde(codigos);
        final long totalParadas = paradaRepository.count();

        final int puntaje = puntajeDeCercania(distanciaMinima)
                + puntajeDeVariedad(lineas.size())
                + puntajeDeFrecuencia(esperaMedia)
                + puntajeDeAlcance(alcanzables, totalParadas);

        return new Conectividad(puntaje, nivelDe(puntaje), cercanas.size(), distanciaMinima,
                List.copyOf(lineas), salidasSemanales, esperaMedia, nocturnas, alcanzables,
                totalParadas);
    }

    private static int puntajeDeCercania(int metros) {
        if (metros <= 100) {
            return 25;
        }
        return Math.max(0, Math.round(25f * (RADIO_METROS - metros) / (RADIO_METROS - 100)));
    }

    private static int puntajeDeVariedad(int lineas) {
        return Math.min(25, Math.round(lineas * 2.5f));
    }

    private static int puntajeDeFrecuencia(Integer esperaMedia) {
        if (esperaMedia == null) {
            return 0;
        }
        if (esperaMedia <= 4) {
            return 30;
        }
        return Math.max(0, Math.round(30f * (30 - esperaMedia) / 26));
    }

    private static int puntajeDeAlcance(long alcanzables, long total) {
        if (total == 0) {
            return 0;
        }
        final double proporcion = (double) alcanzables / total;
        return (int) Math.min(20, Math.round(20 * proporcion / 0.35));
    }

    private static String nivelDe(int puntaje) {
        if (puntaje >= 80) {
            return "excelente";
        }
        if (puntaje >= 60) {
            return "muy buena";
        }
        if (puntaje >= 40) {
            return "buena";
        }
        if (puntaje >= 20) {
            return "regular";
        }
        return "baja";
    }
}
