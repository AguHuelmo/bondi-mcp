package com.bondi_mcp.mcp_stm_montevideo.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bondi_mcp.mcp_stm_montevideo.service.BusEnVivoService;
import com.bondi_mcp.mcp_stm_montevideo.service.RecorridoService;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.BusResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ParadaResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.RecorridoLineaResponse;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * API REST de líneas: por dónde va una línea y dónde están sus coches ahora.
 *
 * <p>Fachada delgada sobre los servicios, como {@link ParadaController}.
 */
@RestController
@RequestMapping("/api/lineas")
@RequiredArgsConstructor
public class LineaController {

    private final RecorridoService recorridoService;
    private final BusEnVivoService busEnVivoService;

    /**
     * {@code GET /api/lineas/185/recorridos}
     *
     * <p>Una línea desconocida responde con sentidos vacíos, no 404: "no tenemos el recorrido"
     * es una respuesta, no una falla.
     */
    @GetMapping("/{linea}/recorridos")
    public RecorridoLineaResponse recorridos(@PathVariable @NotBlank String linea) {
        return RecorridoLineaResponse.desde(recorridoService.recorridoDe(linea));
    }

    /** {@code GET /api/lineas/185/buses}: posición actual de todos los coches de la línea. */
    @GetMapping("/{linea}/buses")
    public List<BusResponse> buses(@PathVariable @NotBlank String linea) {
        return busEnVivoService.deLinea(linea).stream()
                .map(BusResponse::desde)
                .toList();
    }

    /**
     * {@code GET /api/lineas/185/tramo?subida=1234&bajada=5678}
     *
     * <p>Las paradas entre dos puntos del recorrido, en orden: el camino real de un tramo del
     * planificador. Vacío si la línea no une esas paradas en ese orden.
     */
    @GetMapping("/{linea}/tramo")
    public List<ParadaResponse> tramo(
            @PathVariable @NotBlank String linea,
            @RequestParam("subida") long subida,
            @RequestParam("bajada") long bajada) {

        return recorridoService.tramoDe(linea, subida, bajada).stream()
                .map(ParadaResponse::desde)
                .toList();
    }
}
