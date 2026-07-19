package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.time.LocalDateTime;

/**
 * El veredicto de "¿llego a tiempo?": si se llega, con cuánto margen, y el desglose que lo
 * justifica minuto a minuto.
 *
 * <p>Es una estimación honesta, no una promesa: la espera sale del tiempo real (o de los
 * horarios teóricos si no hay buses transmitiendo), el viaje se estima por la cantidad de
 * paradas del tramo, y las caminatas por distancia en línea recta.
 *
 * @param llega                  si la llegada estimada es antes de la hora objetivo
 * @param margenMinutos          minutos de sobra (negativo: cuántos faltan)
 * @param llegadaEstimada        cuándo se estaría llegando
 * @param linea                  la línea a tomar
 * @param subida                 dónde subir
 * @param bajada                 dónde bajar
 * @param caminataInicialMinutos caminata hasta la parada de subida
 * @param esperaMinutos          desde ahora hasta que el bondi alcanzable pasa (incluye la
 *                               caminata inicial: solo cuentan los que pasan después de llegar)
 * @param viajeMinutos           arriba del bondi
 * @param caminataFinalMinutos   de la bajada al destino
 * @param esperaEnTiempoReal     {@code true} si la espera sale de un bus transmitiendo ahora;
 *                               {@code false} si sale de los horarios programados
 */
public record PronosticoDeLlegada(boolean llega, int margenMinutos, LocalDateTime llegadaEstimada,
        String linea, Parada subida, Parada bajada, int caminataInicialMinutos, int esperaMinutos,
        int viajeMinutos, int caminataFinalMinutos, boolean esperaEnTiempoReal) {
}
