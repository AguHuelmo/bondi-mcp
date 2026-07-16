package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.Optional;

/**
 * Una parada de ómnibus del STM.
 *
 * @param codigo    identificador de parada ({@code busstopId} en la API de la Intendencia)
 * @param calle     calle principal
 * @param esquina   calle que hace esquina; puede faltar en algunas paradas
 * @param ubicacion posición geográfica; puede faltar si la API no la informa
 */
public record Parada(long codigo, String calle, String esquina, Coordenada ubicacion) {

    /** Descripción legible del tipo "AV 18 DE JULIO y EJIDO", como la muestra la app Como Ir. */
    public String descripcion() {
        return (esquina == null || esquina.isBlank()) ? calle : calle + " y " + esquina;
    }

    public Optional<Coordenada> ubicacionOptional() {
        return Optional.ofNullable(ubicacion);
    }
}
