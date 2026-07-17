package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.bondi_mcp.mcp_stm_montevideo.domain.HorariosDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;

import lombok.RequiredArgsConstructor;

/**
 * Horarios teóricos de una línea en una parada.
 *
 * <p>Complementa a {@link ArriboService}: los arribos dicen dónde viene el bondi ahora; esto dice
 * a qué hora está previsto que pase, que es lo que sirve de noche, con la línea sin unidades en
 * la calle, o para planificar mañana.
 */
@Service
@RequiredArgsConstructor
public class HorarioTeoricoService {

    private final HorarioTeoricoDao horarioTeoricoDao;

    public HorariosDeLinea horariosDe(long codigoParada, String linea) {
        // En mayúscula como está en la base; el usuario puede escribir "ce1".
        final String normalizada = linea.trim().toUpperCase(Locale.ROOT);
        return new HorariosDeLinea(codigoParada, normalizada,
                horarioTeoricoDao.minutosPorDia(codigoParada, normalizada));
    }
}
