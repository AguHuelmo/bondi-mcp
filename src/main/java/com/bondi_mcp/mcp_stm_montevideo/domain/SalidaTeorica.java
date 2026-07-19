package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.time.LocalDateTime;

/**
 * Una salida teórica ya resuelta a un momento concreto del calendario.
 *
 * <p>Es el paso que le falta a {@link HorariosDeLinea}: ahí los minutos cuentan desde la
 * medianoche del día de servicio y la trasnoche pasa de 1440; acá eso ya está convertido a fecha
 * y hora reales. El "24:30" del sábado acá es el domingo a las 00:30.
 *
 * @param momento cuándo sale, en hora de Montevideo
 * @param tipoDia día de servicio al que pertenece la salida; en la trasnoche NO coincide con el
 *                día calendario de {@code momento}
 */
public record SalidaTeorica(LocalDateTime momento, TipoDia tipoDia) {
}
