package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Una línea que pasa por una parada, según el GTFS. */
@Entity
@Table(name = "parada_linea")
@IdClass(ParadaLineaEntity.ParadaLineaId.class)
public class ParadaLineaEntity {

    @Id
    @Column(name = "codigo_parada", nullable = false)
    private Long codigoParada;

    @Id
    @Column(name = "linea", nullable = false)
    private String linea;

    protected ParadaLineaEntity() {
        // Requerido por JPA.
    }

    public ParadaLineaEntity(Long codigoParada, String linea) {
        this.codigoParada = codigoParada;
        this.linea = linea;
    }

    public String getLinea() {
        return linea;
    }

    /** Clave compuesta. */
    public static class ParadaLineaId implements Serializable {

        private Long codigoParada;
        private String linea;

        public ParadaLineaId() {
        }

        public ParadaLineaId(Long codigoParada, String linea) {
            this.codigoParada = codigoParada;
            this.linea = linea;
        }

        @Override
        public boolean equals(Object otro) {
            if (this == otro) {
                return true;
            }
            if (!(otro instanceof ParadaLineaId id)) {
                return false;
            }
            return Objects.equals(codigoParada, id.codigoParada) && Objects.equals(linea, id.linea);
        }

        @Override
        public int hashCode() {
            return Objects.hash(codigoParada, linea);
        }
    }
}
