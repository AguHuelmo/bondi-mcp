package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bondi_mcp.mcp_stm_montevideo.domain.HorariosDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;

import lombok.RequiredArgsConstructor;

/**
 * Horarios teóricos de una línea en una parada.
 *
 * <p>Complementa a {@link ArriboService}: los arribos dicen dónde viene el bondi ahora; esto dice
 * a qué hora está previsto que pase, que es lo que sirve de noche, con la línea sin unidades en
 * la calle, o para planificar mañana.
 */
@Service
@RequiredArgsConstructor
public class HorarioTeoricoService {

    /** Los horarios del STM viven en esta zona; el server puede estar en cualquier otra. */
    public static final ZoneId ZONA_MONTEVIDEO = ZoneId.of("America/Montevideo");

    /** Hasta cuántos días adelante buscar salidas: una semana cubre cualquier línea que corra. */
    private static final int DIAS_DE_BUSQUEDA = 7;

    private final HorarioTeoricoDao horarioTeoricoDao;

    public HorariosDeLinea horariosDe(long codigoParada, String linea) {
        final String normalizada = normalizar(linea);
        return new HorariosDeLinea(codigoParada, normalizada,
                horarioTeoricoDao.minutosPorDia(codigoParada, normalizada));
    }

    /** Las próximas salidas a partir de ahora, en hora de Montevideo. */
    public List<SalidaTeorica> proximasSalidas(long codigoParada, String linea, int cantidad) {
        return proximasSalidas(codigoParada, linea, cantidad, LocalDateTime.now(ZONA_MONTEVIDEO));
    }

    /**
     * Las próximas salidas después de {@code ahora}, ya resueltas a fecha y hora concretas.
     *
     * <p>Existe para que el consumidor —el LLM sobre todo— no tenga que razonar la trasnoche ni
     * el cambio de tipo de día, que es donde más se equivoca. Dos decisiones hacen el trabajo:
     *
     * <p>Se arranca en el día de AYER, porque su salida "24:30" ocurre hoy a la 00:30 y todavía
     * puede estar por venir. Y como de madrugada las salidas de dos días de servicio conviven (la
     * trasnoche del sábado con las primeras del domingo), se juntan todas las candidatas y se
     * ordenan por momento en vez de confiar en el orden de los días.
     */
    List<SalidaTeorica> proximasSalidas(long codigoParada, String linea, int cantidad, LocalDateTime ahora) {
        final Map<TipoDia, List<Integer>> porDia =
                horarioTeoricoDao.minutosPorDia(codigoParada, normalizar(linea));
        if (porDia.isEmpty() || cantidad < 1) {
            return List.of();
        }

        final List<SalidaTeorica> candidatas = new ArrayList<>();
        final LocalDate desde = ahora.toLocalDate().minusDays(1);
        final LocalDate hasta = ahora.toLocalDate().plusDays(DIAS_DE_BUSQUEDA);
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            final TipoDia tipo = TipoDia.de(dia.getDayOfWeek());
            for (final int minuto : porDia.getOrDefault(tipo, List.of())) {
                final LocalDateTime momento = dia.atStartOfDay().plusMinutes(minuto);
                if (!momento.isBefore(ahora)) {
                    candidatas.add(new SalidaTeorica(momento, tipo));
                }
            }
        }

        return candidatas.stream()
                .sorted(Comparator.comparing(SalidaTeorica::momento))
                .limit(cantidad)
                .toList();
    }

    /** En mayúscula como está en la base; el usuario puede escribir "ce1". */
    private static String normalizar(String linea) {
        return linea.trim().toUpperCase(Locale.ROOT);
    }
}
