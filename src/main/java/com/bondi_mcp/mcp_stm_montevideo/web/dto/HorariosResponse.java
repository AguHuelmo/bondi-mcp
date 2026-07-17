package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.util.Arrays;
import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.HorariosDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;

/**
 * Horarios teóricos de una línea en una parada, tal como los ve el frontend.
 *
 * <p>Los minutos van crudos (desde la medianoche del día de servicio, pueden pasar de 1440 en la
 * trasnoche): el frontend los formatea y necesita el número para resaltar el próximo.
 */
public record HorariosResponse(long codigoParada, String linea, List<HorariosDeDia> porDia) {

    public static HorariosResponse desde(HorariosDeLinea horarios) {
        // En el orden del enum (hábil, sábado, domingo), que es el que espera ver una persona.
        final List<HorariosDeDia> porDia = Arrays.stream(TipoDia.values())
                .filter(dia -> horarios.minutosPorDia().containsKey(dia))
                .map(dia -> new HorariosDeDia(dia.name(), horarios.minutosPorDia().get(dia)))
                .toList();
        return new HorariosResponse(horarios.codigoParada(), horarios.linea(), porDia);
    }

    /** Los horarios de un tipo de día. */
    public record HorariosDeDia(String tipoDia, List<Integer> minutos) {
    }
}
