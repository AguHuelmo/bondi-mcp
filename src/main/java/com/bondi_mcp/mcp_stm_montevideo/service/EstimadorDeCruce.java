package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;
import java.util.Optional;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;

/**
 * Estima dónde queda un cruce de calles a partir de las paradas de cada una.
 *
 * <p>Hace falta porque el geocoder de direcciones no resuelve esquinas: a "Gabriel Pereira y
 * Chucarro" le ignora la segunda calle y contesta un punto cualquiera de la primera. Lo que sí
 * tenemos son las coordenadas de las ~4.900 paradas, y con eso alcanza para aproximar: si una
 * parada de la calle A y una de la calle B están a media cuadra, el cruce está entre las dos.
 *
 * <p>Es una heurística y puede errarle. Por eso solo estima cuando el par más cercano está
 * razonablemente cerca: dos calles que nunca se cruzan darían un punto inventado en el medio de
 * la nada, y es preferible no responder a responder cualquier cosa.
 */
public final class EstimadorDeCruce {

    /**
     * Máxima distancia entre las dos paradas para creerse que las calles se cruzan ahí.
     *
     * <p>Una cuadra en Montevideo ronda los 80-100 m. Con 250 m se toleran cruces donde las
     * paradas están corridas, sin llegar a unir calles que no se tocan.
     */
    private static final double MAXIMA_SEPARACION_METROS = 250;

    private EstimadorDeCruce() {
    }

    /**
     * Estima el punto donde se cruzan dos calles.
     *
     * @param deUnaCalle paradas candidatas de la primera calle
     * @param deLaOtra   paradas candidatas de la segunda
     * @return el cruce estimado, o vacío si las paradas están demasiado lejos como para creerlo
     */
    public static Optional<Coordenada> estimar(List<Parada> deUnaCalle, List<Parada> deLaOtra) {
        Coordenada mejorA = null;
        Coordenada mejorB = null;
        double menorDistancia = Double.MAX_VALUE;

        // Fuerza bruta: son a lo sumo 20 x 20 candidatas por el límite de la búsqueda.
        for (final Parada a : deUnaCalle) {
            if (a.ubicacion() == null) {
                continue;
            }
            for (final Parada b : deLaOtra) {
                if (b.ubicacion() == null) {
                    continue;
                }
                final double distancia = Distancias.entre(a.ubicacion(), b.ubicacion());
                if (distancia < menorDistancia) {
                    menorDistancia = distancia;
                    mejorA = a.ubicacion();
                    mejorB = b.ubicacion();
                }
            }
        }

        if (mejorA == null || menorDistancia > MAXIMA_SEPARACION_METROS) {
            return Optional.empty();
        }
        return Optional.of(Distancias.puntoMedio(mejorA, mejorB));
    }
}
