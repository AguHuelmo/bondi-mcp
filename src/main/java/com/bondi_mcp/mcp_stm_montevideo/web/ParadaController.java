package com.bondi_mcp.mcp_stm_montevideo.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ArribosResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.BusquedaResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.HorariosResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ParadaCercanaResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * API REST para el frontend.
 *
 * <p>Fachada delgada sobre los mismos servicios que usan las tools MCP.
 */
@RestController
@RequestMapping("/api/paradas")
@RequiredArgsConstructor
public class ParadaController {

    private static final int CERCANAS_POR_DEFECTO = 8;

    private final ParadaService paradaService;
    private final ArriboService arriboService;
    private final HorarioTeoricoService horarioTeoricoService;

    /** {@code GET /api/paradas?query=18 de julio y ejido} */
    @GetMapping
    public BusquedaResponse buscar(@RequestParam("query") @NotBlank String query) {
        return BusquedaResponse.desde(paradaService.buscar(query));
    }

    /**
     * {@code GET /api/paradas/cercanas?lat=-34.9&lon=-56.19}
     *
     * <p>Paradas más cercanas a un punto, ordenadas por distancia en línea recta.
     */
    @GetMapping("/cercanas")
    public List<ParadaCercanaResponse> cercanas(
            @RequestParam("lat") @Min(-90) @Max(90) double lat,
            @RequestParam("lon") @Min(-180) @Max(180) double lon,
            @RequestParam(value = "limite", defaultValue = "" + CERCANAS_POR_DEFECTO)
            @Min(1) @Max(50) int limite) {

        return paradaService.cercanasA(new Coordenada(lat, lon), limite).stream()
                .map(ParadaCercanaResponse::desde)
                .toList();
    }

    /** {@code GET /api/paradas/{codigo}/arribos} */
    @GetMapping("/{codigo}/arribos")
    public ArribosResponse arribos(@PathVariable long codigo) {
        return ArribosResponse.desde(arriboService.proximosArribos(codigo));
    }

    /**
     * {@code GET /api/paradas/{codigo}/lineas/{linea}/horarios}
     *
     * <p>Horarios teóricos de una línea en la parada, por tipo de día. Una línea que no pasa por
     * ahí no es un error: responde con {@code porDia} vacío y el frontend lo explica.
     */
    @GetMapping("/{codigo}/lineas/{linea}/horarios")
    public HorariosResponse horarios(@PathVariable long codigo, @PathVariable @NotBlank String linea) {
        return HorariosResponse.desde(horarioTeoricoService.horariosDe(codigo, linea));
    }
}
