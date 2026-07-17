package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParadaLineaRepository extends JpaRepository<ParadaLineaEntity, ParadaLineaEntity.ParadaLineaId> {

    /** Líneas que pasan por una parada, ordenadas de forma estable. */
    @Query("SELECT pl.linea FROM ParadaLineaEntity pl WHERE pl.codigoParada = :codigo ORDER BY pl.linea")
    List<String> lineasDeParada(@Param("codigo") long codigo);

    /** Códigos de las paradas por donde pasa una línea. */
    @Query("SELECT pl.codigoParada FROM ParadaLineaEntity pl WHERE pl.linea = :linea")
    List<Long> paradasDeLinea(@Param("linea") String linea);
}
