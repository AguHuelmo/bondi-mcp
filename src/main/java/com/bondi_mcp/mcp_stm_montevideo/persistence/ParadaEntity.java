package com.bondi_mcp.mcp_stm_montevideo.persistence;

import java.time.Instant;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila del caché de paradas.
 *
 * <p>Es el único punto mutable del modelo, porque JPA exige constructor sin argumentos y campos
 * no finales. Esa mutabilidad no sale de acá: {@link #aParada()} devuelve un record del dominio
 * y los servicios nunca ven la entidad.
 */
@Entity
@Table(name = "parada_cache")
public class ParadaEntity {

    @Id
    @Column(name = "codigo", nullable = false)
    private Long codigo;

    @Column(name = "calle", nullable = false)
    private String calle;

    @Column(name = "esquina")
    private String esquina;

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

    @Column(name = "busqueda", nullable = false)
    private String busqueda;

    @Column(name = "actualizado", nullable = false)
    private Instant actualizado;

    protected ParadaEntity() {
        // Requerido por JPA.
    }

    private ParadaEntity(Long codigo, String calle, String esquina, Double latitud, Double longitud,
            String busqueda, Instant actualizado) {
        this.codigo = codigo;
        this.calle = calle;
        this.esquina = esquina;
        this.latitud = latitud;
        this.longitud = longitud;
        this.busqueda = busqueda;
        this.actualizado = actualizado;
    }

    public static ParadaEntity desde(Parada parada, String busqueda, Instant actualizado) {
        return new ParadaEntity(
                parada.codigo(),
                parada.calle(),
                parada.esquina(),
                parada.ubicacion() == null ? null : parada.ubicacion().latitud(),
                parada.ubicacion() == null ? null : parada.ubicacion().longitud(),
                busqueda,
                actualizado);
    }

    public Parada aParada() {
        final Coordenada ubicacion = (latitud == null || longitud == null)
                ? null
                : new Coordenada(latitud, longitud);
        return new Parada(codigo, calle, esquina, ubicacion);
    }

    public Instant getActualizado() {
        return actualizado;
    }
}
