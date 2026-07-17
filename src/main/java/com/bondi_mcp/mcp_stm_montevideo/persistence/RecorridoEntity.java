package com.bondi_mcp.mcp_stm_montevideo.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una variante de recorrido de una línea: misma secuencia de paradas, distintos horarios. */
@Entity
@Table(name = "recorrido")
public class RecorridoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "linea", nullable = false)
    private String linea;

    @Column(name = "direccion")
    private String direccion;

    protected RecorridoEntity() {
        // Requerido por JPA.
    }

    public RecorridoEntity(String linea, String direccion) {
        this.linea = linea;
        this.direccion = direccion;
    }

    public Long getId() {
        return id;
    }
}
