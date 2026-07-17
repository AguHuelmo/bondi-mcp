package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Escritura masiva de las paradas de cada recorrido.
 *
 * <p>Por JDBC en lote y no por JPA: son ~60.800 filas sin identidad propia ni ciclo de vida, y
 * meterlas de a una por el EntityManager cuesta un orden de magnitud más.
 */
@Component
@RequiredArgsConstructor
public class RecorridoParadaWriter {

    private static final int TAMANIO_LOTE = 1000;

    private final JdbcTemplate jdbcTemplate;

    /** Inserta las paradas de un recorrido, en orden. */
    public void insertar(long recorridoId, List<Long> paradas) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO recorrido_parada (recorrido_id, orden, codigo_parada) VALUES (?, ?, ?)",
                new BatchPreparedStatementSetter() {

                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        ps.setLong(1, recorridoId);
                        ps.setInt(2, i);
                        ps.setLong(3, paradas.get(i));
                    }

                    @Override
                    public int getBatchSize() {
                        return paradas.size();
                    }
                });
    }

    /** Vacía la tabla. El {@code ON DELETE CASCADE} de recorrido también la limpiaría. */
    public void borrarTodo() {
        jdbcTemplate.update("DELETE FROM recorrido_parada");
    }

    public int contar() {
        final Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM recorrido_parada", Integer.class);
        return total == null ? 0 : total;
    }

    public int tamanioLote() {
        return TAMANIO_LOTE;
    }
}
