package com.bondi_mcp.mcp_stm_montevideo.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.service.ConectividadService;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ConectividadResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * El índice de conectividad por HTTP.
 *
 * <p>Pensado para integradores (portales inmobiliarios, mapas, análisis urbano): una dirección o
 * una coordenada entran, un puntaje explicable sale.
 */
@RestController
@RequestMapping("/api/conectividad")
@RequiredArgsConstructor
public class ConectividadController {

    private final ConectividadService conectividadService;
    private final ViajeService viajeService;

    /**
     * {@code GET /api/conectividad?query=gabriel pereira 2470}
     *
     * <p>La dirección se resuelve con la misma búsqueda que el resto de la app (padrón oficial,
     * cruces estimados, lugares conocidos). 404 si no se pudo ubicar: no es un error nuestro, es
     * que esa dirección no existe.
     */
    @GetMapping
    public ResponseEntity<ConectividadResponse> porDireccion(
            @RequestParam("query") @NotBlank String query) {
        return viajeService.ubicar(query)
                .map(punto -> ResponseEntity.ok(ConectividadResponse.desde(conectividadService.medir(punto))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /** {@code GET /api/conectividad/punto?lat=-34.9&lon=-56.16}, para cuando ya hay coordenada. */
    @GetMapping("/punto")
    public ConectividadResponse porPunto(
            @RequestParam("lat") @Min(-90) @Max(90) double lat,
            @RequestParam("lon") @Min(-180) @Max(180) double lon) {
        return ConectividadResponse.desde(conectividadService.medir(new Coordenada(lat, lon)));
    }
}
