package com.bondi_mcp.mcp_stm_montevideo.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bondi_mcp.mcp_stm_montevideo.domain.HistorialDeEsperas;
import com.bondi_mcp.mcp_stm_montevideo.service.PuntualidadService;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * El historial de esperas observadas por HTTP.
 *
 * <p>Sin datos no es 404: el historial vacío es una respuesta válida (el dataset se construye
 * con el uso) y {@code observaciones: 0} lo dice.
 */
@RestController
@RequestMapping("/api/puntualidad")
@RequiredArgsConstructor
public class PuntualidadController {

    private final PuntualidadService puntualidadService;

    /** {@code GET /api/puntualidad?linea=185&parada=3977} (la parada es opcional). */
    @GetMapping
    public HistorialResponse historial(
            @RequestParam("linea") @NotBlank String linea,
            @RequestParam(value = "parada", required = false) Long parada) {
        return HistorialResponse.desde(puntualidadService.historialDe(linea, parada));
    }

    /** El historial tal como lo ve un integrador. */
    public record HistorialResponse(String linea, Long codigoParada, long observaciones,
            String primeraObservacion, String ultimaObservacion, Integer esperaMediaMinutos,
            Integer esperaMedianaMinutos, Integer esperaP90Minutos, Integer esperaTeoricaMinutos,
            List<FranjaResponse> porFranja) {

        static HistorialResponse desde(HistorialDeEsperas historial) {
            return new HistorialResponse(
                    historial.linea(),
                    historial.codigoParada(),
                    historial.observaciones(),
                    historial.primeraObservacion() == null ? null : historial.primeraObservacion().toString(),
                    historial.ultimaObservacion() == null ? null : historial.ultimaObservacion().toString(),
                    historial.esperaMediaMinutos(),
                    historial.esperaMedianaMinutos(),
                    historial.esperaP90Minutos(),
                    historial.esperaTeoricaMinutos(),
                    historial.porFranja().stream()
                            .map(franja -> new FranjaResponse(franja.nombre(),
                                    franja.observaciones(), franja.esperaMediaMinutos()))
                            .toList());
        }
    }

    /** Una franja horaria del historial. */
    public record FranjaResponse(String franja, long observaciones, int esperaMediaMinutos) {
    }
}
