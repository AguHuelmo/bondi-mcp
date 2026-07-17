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

    public void borrarTodo() {
        jdbcTemplate.update("DELETE FROM horario_teorico");
    }

    public int contar() {
        final Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM horario_teorico", Integer.class);
        return total == null ? 0 : total;
    }
}
