package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;

import lombok.RequiredArgsConstructor;

/**
 * Lectura y escritura masiva de los horarios teóricos.
 *
 * <p>Por JDBC y no por JPA, igual que {@link RecorridoParadaWriter}: son ~2 millones de filas sin
 * identidad propia ni ciclo de vida, y pasarlas por el EntityManager cuesta un orden de magnitud
 * más.
 */
@Component
@RequiredArgsConstructor
public class HorarioTeoricoDao {

    private static final int TAMANIO_LOTE = 5000;

    private final JdbcTemplate jdbcTemplate;

    /** Una fila de la tabla, tal cual se inserta. */
    public record Fila(long codigoParada, String linea, TipoDia tipoDia, int minuto) {
    }

    /** Inserta todas las filas, en lotes: una por una serían ~2 millones de idas a la base. */
    public void insertar(List<Fila> filas) {
        final List<Object[]> lote = new ArrayList<>(TAMANIO_LOTE);
        for (final Fila fila : filas) {
            lote.add(new Object[] {fila.codigoParada(), fila.linea(), fila.tipoDia().name(), fila.minuto()});
            if (lote.size() == TAMANIO_LOTE) {
                volcar(lote);
            }
        }
        volcar(lote);
    }

    private void volcar(List<Object[]> lote) {
        if (lote.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO horario_teorico (codigo_parada, linea, tipo_dia, minuto) VALUES (?, ?, ?, ?)",
                lote);
        lote.clear();
    }

    /**
     * Minutos de salida de una línea en una parada, por tipo de día y en orden.
     *
     * <p>Los tipos de día sin horarios no aparecen en el mapa.
     */
    public Map<TipoDia, List<Integer>> minutosPorDia(long codigoParada, String linea) {
        final Map<TipoDia, List<Integer>> porDia = new EnumMap<>(TipoDia.class);
        jdbcTemplate.query(
                "SELECT tipo_dia, minuto FROM horario_teorico "
                        + "WHERE codigo_parada = ? AND linea = ? ORDER BY minuto",
                rs -> {
                    porDia.computeIfAbsent(TipoDia.valueOf(rs.getString(1)), dia -> new ArrayList<>())
                            .add(rs.getInt(2));
                },
                codigoParada, linea);
        return porDia;
    }

    /**
     * Cuánto servicio tiene cada línea en cada una de las paradas dadas.
     *
     * <p>Es la materia prima del índice de conectividad: cuántas salidas por semana hay, cuántas
     * en el día hábil diurno (07:00–22:00) y cuántas de noche. Las semanales cuentan el día
     * hábil por cinco; la trasnoche (minuto ≥ 1440) y la madrugada temprana cuentan como noche.
     */
    public List<Frecuencia> frecuencias(List<Long> codigosParada) {
        if (codigosParada.isEmpty()) {
            return List.of();
        }
        final String placeholders = String.join(",", java.util.Collections.nCopies(codigosParada.size(), "?"));
        return jdbcTemplate.query("""
                SELECT codigo_parada, linea,
                       SUM(CASE WHEN tipo_dia = 'HABIL' THEN 5 ELSE 1 END) AS semanales,
                       SUM(CASE WHEN tipo_dia = 'HABIL' AND minuto BETWEEN 420 AND 1320
                           THEN 1 ELSE 0 END) AS diurnas_habil,
                       SUM(CASE WHEN minuto >= 1380 OR minuto <= 300
                           THEN CASE WHEN tipo_dia = 'HABIL' THEN 5 ELSE 1 END
                           ELSE 0 END) AS nocturnas
                FROM horario_teorico
                WHERE codigo_parada IN (%s)
                GROUP BY codigo_parada, linea
                """.formatted(placeholders),
                (rs, fila) -> new Frecuencia(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getInt(4), rs.getInt(5)),
                codigosParada.toArray());
    }

    /**
     * El servicio de una línea en una parada.
     *
     * @param salidasSemanales      salidas totales en una semana tipo
     * @param salidasDiurnasHabil   salidas de UN día hábil entre 07:00 y 22:00
     * @param salidasNocturnasSemanales salidas semanales entre 23:00 y 05:00
     */
    public record Frecuencia(long codigoParada, String linea, int salidasSemanales,
            int salidasDiurnasHabil, int salidasNocturnasSemanales) {
    }

    public void borrarTodo() {
        jdbcTemplate.update("DELETE FROM horario_teorico");
    }

    public int contar() {
        final Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM horario_teorico", Integer.class);
        return total == null ? 0 : total;
    }
}
