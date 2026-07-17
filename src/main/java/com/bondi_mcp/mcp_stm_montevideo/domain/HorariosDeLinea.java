package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.List;
import java.util.Map;

/**
 * Los horarios teóricos de una línea en una parada, agrupados por tipo de día.
 *
 * <p>Teóricos: salen del GTFS, no del tiempo real. Responden "a qué hora está previsto que pase",
 * no "dónde viene el bondi ahora"; para lo segundo están los arribos.
 *
 * @param minutosPorDia minutos desde la medianoche del día de servicio, ordenados. Pueden superar
 *                      1440: un "24:30" del GTFS es el bondi de la 00:30 que pertenece al servicio
 *                      del día anterior. Los días sin horarios no aparecen como clave.
 */
public record HorariosDeLinea(long codigoParada, String linea, Map<TipoDia, List<Integer>> minutosPorDia) {

    public HorariosDeLinea {
        minutosPorDia = minutosPorDia.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    public boolean sinHorarios() {
        return minutosPorDia.values().stream().allMatch(List::isEmpty);
    }
}
