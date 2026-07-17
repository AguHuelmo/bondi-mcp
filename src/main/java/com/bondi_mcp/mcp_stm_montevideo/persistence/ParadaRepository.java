package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParadaRepository extends JpaRepository<ParadaEntity, Long> {

    /**
     * Paradas ordenadas por cuántos de los patrones dados coinciden.
     *
     * <p>Usa {@code LIKE ANY} y no {@code LIKE ALL} a propósito: exigir todas las palabras deja
     * sin un solo resultado a quien le erra a una, que es justo el que más necesita buscar.
     * "gabriel pereira y chucarro", cuando el cruce real es "GABRIEL A PEREIRA Y PEDRO F BERRO",
     * tiene que devolver igual esa parada por sus dos palabras coincidentes.
     *
     * <p>El orden hace el trabajo fino: las que coinciden en más palabras van primero, así que una
     * búsqueda exacta sigue dando en el clavo y las aproximadas quedan abajo como candidatas. A
     * igual cantidad, gana la descripción más corta, que suele ser la menos ruidosa.
     *
     * <p>Nativa porque {@code LIKE ANY (array)} y {@code unnest} son de Postgres.
     */
    @Query(value = """
            SELECT p.* FROM parada_cache p
            WHERE p.busqueda LIKE ANY (:patrones)
            ORDER BY (SELECT count(*) FROM unnest(:patrones) AS patron
                      WHERE p.busqueda LIKE patron) DESC,
                     length(p.busqueda),
                     p.codigo
            """, nativeQuery = true)
    List<ParadaEntity> buscarPorPatrones(@Param("patrones") String[] patrones, Limit limite);

    /**
     * Paradas más cercanas a un punto, de la más cerca a la más lejos.
     *
     * <p>Distancia por haversine directo en SQL: con ~5.000 paradas el escaneo completo es
     * instantáneo y evita depender de PostGIS o earthdistance, que habría que instalar aparte.
     * Es distancia en línea recta, no caminando.
     */
    @Query(value = """
            SELECT p.*,
                   6371000 * 2 * asin(sqrt(
                       power(sin(radians(:latitud - p.latitud) / 2), 2)
                       + cos(radians(p.latitud)) * cos(radians(:latitud))
                       * power(sin(radians(:longitud - p.longitud) / 2), 2)
                   )) AS distancia
            FROM parada_cache p
            WHERE p.latitud IS NOT NULL AND p.longitud IS NOT NULL
            ORDER BY distancia
            """, nativeQuery = true)
    List<ParadaConDistancia> cercanasA(@Param("latitud") double latitud,
            @Param("longitud") double longitud, Limit limite);

    /** Proyección de {@link #cercanasA}: la parada más la distancia calculada. */
    interface ParadaConDistancia {

        long getCodigo();

        String getCalle();

        String getEsquina();

        Double getLatitud();

        Double getLongitud();

        double getDistancia();
    }

    @Query("SELECT MAX(p.actualizado) FROM ParadaEntity p")
    Optional<Instant> ultimaActualizacion();
}
