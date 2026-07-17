package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lo que sacamos del GTFS, en una sola lectura del zip.
 *
 * @param lineasPorParada qué líneas pasan por cada parada (para "por acá pasan la 149 y la 163")
 * @param recorridos      secuencias ordenadas de paradas (para "¿me lleva de acá hasta allá?")
 * @param horarios        horarios teóricos por parada, línea y tipo de día (para "¿cuándo pasa?")
 */
public record DatosGtfs(Map<Long, Set<String>> lineasPorParada, List<RecorridoGtfs> recorridos,
        List<HorarioTeoricoGtfs> horarios) {

    public DatosGtfs {
        lineasPorParada = Map.copyOf(lineasPorParada);
        recorridos = List.copyOf(recorridos);
        horarios = List.copyOf(horarios);
    }
}
