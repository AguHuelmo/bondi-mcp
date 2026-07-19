package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.List;

/**
 * Qué tan bien servido por ómnibus está un punto de Montevideo.
 *
 * <p>Es el dato que no da ninguna app de transporte: no "cuándo viene el bondi" sino "¿me
 * conviene vivir/alquilar/poner un local acá?". Se calcula entero con datos propios (paradas,
 * recorridos y horarios teóricos del GTFS), sin ninguna llamada externa.
 *
 * @param puntaje                    0 a 100; ver los componentes en ConectividadService
 * @param nivel                      etiqueta legible del puntaje ("excelente", "buena", ...)
 * @param paradasCercanas            cuántas paradas hay a menos de 400 m
 * @param metrosALaParadaMasCercana  distancia en línea recta a la más próxima; null sin paradas
 * @param lineas                     las líneas distintas que sirven la zona
 * @param salidasSemanales           salidas de ómnibus por semana sumando todas las líneas (cada
 *                                   línea contada en su mejor parada cercana)
 * @param esperaMediaDiurnaMinutos   cada cuántos minutos sale un bondi (el que sea) en un día
 *                                   hábil de 07:00 a 22:00; null si no hay horarios importados
 * @param salidasNocturnasSemanales  salidas por semana entre las 23:00 y las 05:00
 * @param paradasAlcanzables         a cuántas paradas de la ciudad se llega sin transbordo
 * @param totalParadas               cuántas paradas tiene la ciudad, para leer el alcance
 */
public record Conectividad(int puntaje, String nivel, int paradasCercanas,
        Integer metrosALaParadaMasCercana, List<String> lineas, int salidasSemanales,
        Integer esperaMediaDiurnaMinutos, int salidasNocturnasSemanales,
        long paradasAlcanzables, long totalParadas) {

    public Conectividad {
        lineas = List.copyOf(lineas);
    }

    /** Un punto sin ninguna parada a distancia caminable. */
    public static Conectividad sinServicio() {
        return new Conectividad(0, "sin servicio", 0, null, List.of(), 0, null, 0, 0, 0);
    }

    public boolean sinParadasCerca() {
        return paradasCercanas == 0;
    }

    /** Qué parte de la ciudad queda a un solo bondi de distancia, como porcentaje entero. */
    public int porcentajeAlcanzable() {
        return totalParadas == 0 ? 0 : (int) Math.round(100.0 * paradasAlcanzables / totalParadas);
    }
}
