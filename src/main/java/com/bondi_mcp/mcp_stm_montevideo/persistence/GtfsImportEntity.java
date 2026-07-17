package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Versión del GTFS ya importada. Fila única: evita reprocesar 88 MB en cada arranque. */
@Entity
@Table(name = "gtfs_import")
public class GtfsImportEntity {

    /** La tabla tiene un check que fuerza esta única fila. */
    public static final short FILA_UNICA = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "importado", nullable = false)
    private Instant importado;

    protected GtfsImportEntity() {
        // Requerido por JPA.
    }

    public GtfsImportEntity(String version, Instant importado) {
        this.id = FILA_UNICA;
        this.version = version;
        this.importado = importado;
    }

    public String getVersion() {
        return version;
    }
}
