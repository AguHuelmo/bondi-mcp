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
     * Paradas cuyo texto normalizado matchea TODOS los patrones dados.
     *
     * <p>Nativa porque usa {@code LIKE ALL (array)} de Postgres: así "18 de julio ejido" matchea
     * sin importar el orden de las palabras, con una sola consulta y sin armar SQL dinámico.
     */
    @Query(value = """
            SELECT * FROM parada_cache
            WHERE busqueda LIKE ALL (:patrones)
            ORDER BY length(busqueda), codigo
            """, nativeQuery = true)
    List<ParadaEntity> buscarPorPatrones(@Param("patrones") String[] patrones, Limit limite);

    @Query("SELECT MAX(p.actualizado) FROM ParadaEntity p")
    Optional<Instant> ultimaActualizacion();
}
