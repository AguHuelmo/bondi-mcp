package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.time.format.DateTimeFormatter;

import com.bondi_mcp.mcp_stm_montevideo.domain.PronosticoDeLlegada;

/**
 * El veredicto de "¿llego a tiempo?" para la API REST.
 *
 * <p>{@code pronostico} viene null cuando no se pudo pronosticar; {@code motivo} lo explica.
 */
public record LlegoResponse(Pronostico pronostico, String motivo) {

    public static LlegoResponse de(PronosticoDeLlegada pronostico) {
        return new LlegoResponse(new Pronostico(
                pronostico.llega(),
                pronostico.margenMinutos(),
                pronostico.llegadaEstimada().format(DateTimeFormatter.ofPattern("HH:mm")),
                pronostico.linea(),
                pronostico.subida().codigo(),
                pronostico.subida().descripcion(),
                pronostico.bajada().codigo(),
                pronostico.bajada().descripcion(),
                pronostico.caminataInicialMinutos(),
                pronostico.esperaMinutos(),
                pronostico.viajeMinutos(),
                pronostico.caminataFinalMinutos(),
                pronostico.esperaEnTiempoReal()), null);
    }

    public static LlegoResponse sin(String motivo) {
        return new LlegoResponse(null, motivo);
    }

    /** El desglose minuto a minuto del veredicto. */
    public record Pronostico(boolean llega, int margenMinutos, String llegadaEstimada,
            String linea, long codigoSubida, String subida, long codigoBajada, String bajada,
            int caminataInicialMinutos, int esperaMinutos, int viajeMinutos,
            int caminataFinalMinutos, boolean esperaEnTiempoReal) {
    }
}
