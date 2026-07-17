package com.bondi_mcp.mcp_stm_montevideo.service;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;

/** Distancias sobre la superficie terrestre. */
public final class Distancias {

    private static final double RADIO_TIERRA_METROS = 6_371_000;

    private Distancias() {
    }

    /** Distancia en línea recta entre dos puntos, en metros. */
    public static double entre(Coordenada a, Coordenada b) {
        final double dLat = Math.toRadians(b.latitud() - a.latitud());
        final double dLon = Math.toRadians(b.longitud() - a.longitud());
        final double lat1 = Math.toRadians(a.latitud());
        final double lat2 = Math.toRadians(b.latitud());

        final double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return RADIO_TIERRA_METROS * 2 * Math.asin(Math.sqrt(h));
    }

    /** Punto medio aproximado entre dos coordenadas. */
    public static Coordenada puntoMedio(Coordenada a, Coordenada b) {
        // A escala de cuadras, promediar lat/lon alcanza: el error por la curvatura es de
        // centímetros y acá estamos estimando una esquina, no navegando.
        return new Coordenada((a.latitud() + b.latitud()) / 2, (a.longitud() + b.longitud()) / 2);
    }
}
