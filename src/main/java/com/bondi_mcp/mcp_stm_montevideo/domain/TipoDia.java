package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.time.DayOfWeek;

/**
 * Los tres tipos de día del servicio de ómnibus: los horarios de un martes y un jueves son
 * iguales, los de un sábado y un domingo no.
 */
public enum TipoDia {
    HABIL, SABADO, DOMINGO;

    public static TipoDia de(DayOfWeek dia) {
        return switch (dia) {
            case SATURDAY -> SABADO;
            case SUNDAY -> DOMINGO;
            default -> HABIL;
        };
    }
}
