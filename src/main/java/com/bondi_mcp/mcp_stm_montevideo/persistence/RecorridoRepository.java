package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecorridoRepository extends JpaRepository<RecorridoEntity, Long> {

    /**
     * Viajes directos entre dos conjuntos de paradas.
     *
     * <p>La condición que importa es {@code destino.orden > origen.orden}: no alcanza con que la
     * línea toque las dos paradas, tiene que tocarlas EN ESE ORDEN dentro del mismo recorrido. La
     * línea 62 pasa por Gabriel Pereira y por 18 de Julio, pero en viajes distintos (ida y
     * vuelta); sin esta condición diríamos que te lleva, y no te lleva.
     *
     * @param origenes códigos de parada donde puede subir
     * @param destinos códigos de parada donde le sirve bajar
     */
    @Query(value = """
            SELECT DISTINCT r.linea AS linea,
                            o.codigo_parada AS paradaSubida,
                            d.codigo_parada AS paradaBajada
            FROM recorrido_parada o
            JOIN recorrido_parada d ON d.recorrido_id = o.recorrido_id AND d.orden > o.orden
            JOIN recorrido r ON r.id = o.recorrido_id
            WHERE o.codigo_parada IN (:origenes) AND d.codigo_parada IN (:destinos)
            """, nativeQuery = true)
    List<TramoDirecto> viajesDirectos(@Param("origenes") List<Long> origenes,
            @Param("destinos") List<Long> destinos);

    /**
     * Viajes con un transbordo.
     *
     * <p>Busca un par de paradas cercanas entre sí donde bajarse de una línea y tomar otra: la
     * primera alcanzable desde el origen, la segunda desde donde se llega al destino. El filtro
     * por caja (lat/lon) va antes del haversine a propósito: descarta la enorme mayoría de los
     * pares con comparaciones baratas, y solo sobre los que quedan se calcula la distancia real.
     *
     * @param metrosTransbordo cuánto se acepta caminar para hacer el cambio
     */
    @Query(value = """
            WITH tramo1 AS (
                SELECT DISTINCT r.linea AS linea, o.codigo_parada AS subida, p.codigo_parada AS bajada
                FROM recorrido_parada o
                JOIN recorrido_parada p ON p.recorrido_id = o.recorrido_id AND p.orden > o.orden
                JOIN recorrido r ON r.id = o.recorrido_id
                WHERE o.codigo_parada IN (:origenes)
            ),
            tramo2 AS (
                SELECT DISTINCT r.linea AS linea, q.codigo_parada AS subida, d.codigo_parada AS bajada
                FROM recorrido_parada q
                JOIN recorrido_parada d ON d.recorrido_id = q.recorrido_id AND d.orden > q.orden
                JOIN recorrido r ON r.id = q.recorrido_id
                WHERE d.codigo_parada IN (:destinos)
            )
            SELECT t1.linea AS linea1,
                   t1.subida AS paradaSubida,
                   t1.bajada AS paradaTransbordoBaja,
                   t2.linea AS linea2,
                   t2.subida AS paradaTransbordoSube,
                   t2.bajada AS paradaBajada,
                   6371000 * 2 * asin(sqrt(
                       power(sin(radians(pb.latitud - ps.latitud) / 2), 2)
                       + cos(radians(ps.latitud)) * cos(radians(pb.latitud))
                       * power(sin(radians(pb.longitud - ps.longitud) / 2), 2)
                   )) AS metrosCaminando
            FROM tramo1 t1
            JOIN parada_cache pb ON pb.codigo = t1.bajada
            JOIN tramo2 t2 ON t2.linea <> t1.linea
            JOIN parada_cache ps ON ps.codigo = t2.subida
            WHERE pb.latitud IS NOT NULL AND ps.latitud IS NOT NULL
              AND abs(pb.latitud - ps.latitud) < :gradosCaja
              AND abs(pb.longitud - ps.longitud) < :gradosCaja
              AND 6371000 * 2 * asin(sqrt(
                      power(sin(radians(pb.latitud - ps.latitud) / 2), 2)
                      + cos(radians(ps.latitud)) * cos(radians(pb.latitud))
                      * power(sin(radians(pb.longitud - ps.longitud) / 2), 2)
                  )) <= :metrosTransbordo
            ORDER BY metrosCaminando
            """, nativeQuery = true)
    List<TramoConTransbordo> viajesConTransbordo(@Param("origenes") List<Long> origenes,
            @Param("destinos") List<Long> destinos,
            @Param("metrosTransbordo") double metrosTransbordo,
            @Param("gradosCaja") double gradosCaja,
            Limit limite);

    /**
     * Las variantes de una línea con cuántas paradas tiene cada una.
     *
     * <p>Devuelve todas; quién consulta elige con cuál quedarse (típicamente la más larga por
     * sentido, porque las cortas son salidas parciales del mismo camino).
     */
    @Query(value = """
            SELECT r.id AS id, r.direccion AS direccion, count(*) AS cantidadParadas
            FROM recorrido r
            JOIN recorrido_parada rp ON rp.recorrido_id = r.id
            WHERE r.linea = :linea
            GROUP BY r.id, r.direccion
            """, nativeQuery = true)
    List<VarianteDeLinea> variantesDeLinea(@Param("linea") String linea);

    /** Los códigos de parada de un recorrido, en el orden en que el bus los toca. */
    @Query(value = """
            SELECT codigo_parada FROM recorrido_parada
            WHERE recorrido_id = :recorridoId
            ORDER BY orden
            """, nativeQuery = true)
    List<Long> paradasDeRecorrido(@Param("recorridoId") long recorridoId);

    /**
     * Dónde cae un tramo subida→bajada dentro de algún recorrido de la línea.
     *
     * <p>Se elige el recorrido donde el tramo es más corto: si varios lo contienen, el de menos
     * paradas intermedias es el camino directo y los otros dan alguna vuelta.
     */
    @Query(value = """
            SELECT o.recorrido_id AS recorridoId, o.orden AS desde, d.orden AS hasta
            FROM recorrido_parada o
            JOIN recorrido_parada d ON d.recorrido_id = o.recorrido_id AND d.orden > o.orden
            JOIN recorrido r ON r.id = o.recorrido_id
            WHERE r.linea = :linea AND o.codigo_parada = :subida AND d.codigo_parada = :bajada
            ORDER BY d.orden - o.orden
            LIMIT 1
            """, nativeQuery = true)
    Optional<TramoEnRecorrido> tramoEnRecorrido(@Param("linea") String linea,
            @Param("subida") long subida, @Param("bajada") long bajada);

    /**
     * A cuántas paradas distintas de la ciudad se llega SIN transbordo subiendo en alguna de las
     * paradas dadas.
     *
     * <p>Es la métrica de alcance del índice de conectividad: mide adónde te lleva la esquina,
     * no cuántos bondis pasan. Usa el orden real de los recorridos, igual que el planificador.
     */
    @Query(value = """
            SELECT count(DISTINCT d.codigo_parada)
            FROM recorrido_parada o
            JOIN recorrido_parada d ON d.recorrido_id = o.recorrido_id AND d.orden > o.orden
            WHERE o.codigo_parada IN (:origenes)
            """, nativeQuery = true)
    long paradasAlcanzablesDesde(@Param("origenes") List<Long> origenes);

    /** Los códigos de parada de un pedazo de recorrido, en orden. */
    @Query(value = """
            SELECT codigo_parada FROM recorrido_parada
            WHERE recorrido_id = :recorridoId AND orden BETWEEN :desde AND :hasta
            ORDER BY orden
            """, nativeQuery = true)
    List<Long> paradasEntre(@Param("recorridoId") long recorridoId,
            @Param("desde") int desde, @Param("hasta") int hasta);

    /** Proyección de una variante de recorrido de una línea. */
    interface VarianteDeLinea {

        long getId();

        String getDireccion();

        long getCantidadParadas();
    }

    /** Proyección de la posición de un tramo dentro de un recorrido. */
    interface TramoEnRecorrido {

        long getRecorridoId();

        int getDesde();

        int getHasta();
    }

    /** Proyección de un viaje sin transbordo. */
    interface TramoDirecto {

        String getLinea();

        long getParadaSubida();

        long getParadaBajada();
    }

    /** Proyección de un viaje con un transbordo. */
    interface TramoConTransbordo {

        String getLinea1();

        long getParadaSubida();

        long getParadaTransbordoBaja();

        String getLinea2();

        long getParadaTransbordoSube();

        long getParadaBajada();

        double getMetrosCaminando();
    }
}
