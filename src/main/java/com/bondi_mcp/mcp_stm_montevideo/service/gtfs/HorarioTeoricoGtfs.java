package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;

/**
 * Los horarios teóricos de una línea en una parada para un tipo de día.
 *
 * @param minutos minutos desde la medianoche del día de servicio, ordenados y sin repetidos.
 *                Pueden superar 1440: el GTFS escribe "24:30" para el bondi de la 00:30 que
 *                pertenece al servicio del día anterior.
 */
public record HorarioTeoricoGtfs(long parada, String linea, TipoDia tipoDia, List<Integer> minutos) {

    public HorarioTeoricoGtfs {
        minutos = List.copyOf(minutos);
    }
}
