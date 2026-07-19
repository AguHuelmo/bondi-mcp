package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;

import lombok.RequiredArgsConstructor;

/**
 * Escritura y lectura del historial de esperas observadas.
 *
 * <p>Por JDBC como los otros DAO masivos: las observaciones son filas sin identidad propia que
 * se insertan de a lotes y se leen agregadas, nunca una por una.
 */
@Component
@RequiredArgsConstructor
public class ObservacionDeArriboDao {

    private final JdbcTemplate jdbcTemplate;

    /** Deja anotado lo que el tiempo real decía en este momento para una parada. */
    public void insertar(Instant observadoEn, long codigoParada, List<Arribo> arribos) {
        final List<Object[]> filas = arribos.stream()
                .map(arribo -> new Object[] {
                        Timestamp.from(observadoEn),
                        codigoParada,
                        arribo.linea(),
                        (short) arribo.esperaEnMinutos(),
                        arribo.distanciaMetros(),
                        null, // bus_id: la API lo trae en el detalle del bus, todavía no acá
                })
                .toList();
        jdbcTemplate.batchUpdate("""
                INSERT INTO observacion_arribo
                    (observado_en, codigo_parada, linea, espera_minutos, distancia_metros, bus_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, filas);
    }

    /** El resumen agregado de una línea, opcionalmente acotado a una parada. Vacío sin datos. */
    public Optional<Resumen> resumir(String linea, Long codigoParada) {
        final StringBuilder sql = new StringBuilder("""
                SELECT count(*) AS observaciones,
                       avg(espera_minutos) AS media,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY espera_minutos) AS mediana,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY espera_minutos) AS p90,
                       min(observado_en) AS desde,
                       max(observado_en) AS hasta
                FROM observacion_arribo
                WHERE linea = ?
                """);
        final List<Object> parametros = new ArrayList<>(List.of(linea));
        if (codigoParada != null) {
            sql.append(" AND codigo_parada = ?");
            parametros.add(codigoParada);
        }

        return jdbcTemplate.query(sql.toString(), rs -> {
            if (!rs.next() || rs.getLong("observaciones") == 0) {
                return Optional.<Resumen>empty();
            }
            return Optional.of(new Resumen(
                    rs.getLong("observaciones"),
                    rs.getDouble("media"),
                    rs.getDouble("mediana"),
                    rs.getDouble("p90"),
                    rs.getTimestamp("desde").toInstant(),
                    rs.getTimestamp("hasta").toInstant()));
        }, parametros.toArray());
    }

    /**
     * Las esperas por franja horaria de Montevideo: madrugada (0–6), mañana (6–12),
     * tarde (12–18) y noche (18–24). Las franjas sin observaciones no aparecen.
     */
    public List<FranjaFila> porFranja(String linea, Long codigoParada) {
        final StringBuilder sql = new StringBuilder("""
                SELECT div(extract(hour FROM observado_en AT TIME ZONE 'America/Montevideo')::int, 6) AS franja,
                       count(*) AS observaciones,
                       avg(espera_minutos) AS media
                FROM observacion_arribo
                WHERE linea = ?
                """);
        final List<Object> parametros = new ArrayList<>(List.of(linea));
        if (codigoParada != null) {
            sql.append(" AND codigo_parada = ?");
            parametros.add(codigoParada);
        }
        sql.append(" GROUP BY franja ORDER BY franja");

        return jdbcTemplate.query(sql.toString(),
                (rs, fila) -> new FranjaFila(rs.getInt("franja"), rs.getLong("observaciones"),
                        rs.getDouble("media")),
                parametros.toArray());
    }

    /** El agregado global de una línea (o línea+parada). */
    public record Resumen(long observaciones, double esperaMedia, double esperaMediana,
            double esperaP90, Instant desde, Instant hasta) {
    }

    /** Una franja horaria: 0=madrugada, 1=mañana, 2=tarde, 3=noche. */
    public record FranjaFila(int franja, long observaciones, double esperaMedia) {
    }
}
