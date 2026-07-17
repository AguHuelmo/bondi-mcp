package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import com.bondi_mcp.mcp_stm_montevideo.domain.PuntoDeReferencia;

/**
 * El lugar que se buscó, ya ubicado, cuando no hay ninguna parada ahí mismo.
 *
 * <p>Va con coordenada porque es la referencia contra la que se miden las paradas cercanas: sin
 * poder dibujarla, una lista de paradas "a 27 m" no dice a 27 m de qué.
 *
 * @param nombre cómo lo llama el padrón ("GABRIEL A. PEREIRA 2470", "ESTADIO CENTENARIO");
 *               {@code null} para un cruce, que no tiene nombre oficial
 * @param tipo   qué clase de lugar es: {@code DIRECCION} (una puerta del padrón), {@code LUGAR}
 *               (un punto de interés) o {@code CRUCE} (una esquina que estimamos nosotros y que
 *               puede errarle)
 */
public record PuntoResponse(double latitud, double longitud, String nombre, String tipo) {

    public static PuntoResponse desde(PuntoDeReferencia punto) {
        return new PuntoResponse(
                punto.coordenada().latitud(),
                punto.coordenada().longitud(),
                punto.descripcion(),
                tipoDe(punto.origen()));
    }

    /** Se mapea a mano y no con {@code name()}: el nombre del enum es interno y puede cambiar. */
    private static String tipoDe(PuntoDeReferencia.Origen origen) {
        return switch (origen) {
            case DIRECCION_OFICIAL -> "DIRECCION";
            case LUGAR_CONOCIDO -> "LUGAR";
            case CRUCE_ESTIMADO -> "CRUCE";
        };
    }
}
