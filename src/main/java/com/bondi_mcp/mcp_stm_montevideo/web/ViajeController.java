package com.bondi_mcp.mcp_stm_montevideo.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ViajeResponse;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * Cómo ir de un lugar a otro.
 *
 * <p>Misma lógica que la tool MCP {@code como_llego}.
 */
@RestController
@RequestMapping("/api/viajes")
@RequiredArgsConstructor
public class ViajeController {

    private final ViajeService viajeService;

    /**
     * {@code GET /api/viajes?origen=gabriel pereira y berro&destino=18 de julio y ejido}
     *
     * <p>Los dos extremos van como texto y se resuelven con la búsqueda de paradas. Si alguno no
     * se puede ubicar, responde 404: no es un error del servicio, es que esa dirección no existe.
     */
    @GetMapping
    public ResponseEntity<List<ViajeResponse>> comoLlego(
            @RequestParam("origen") @NotBlank String origen,
            @RequestParam("destino") @NotBlank String destino) {

        final var desde = viajeService.ubicar(origen);
        final var hasta = viajeService.ubicar(destino);
        if (desde.isEmpty() || hasta.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        final List<ViajeResponse> viajes = viajeService
                .comoLlegar(desde.get(), hasta.get()).stream()
                .map(ViajeResponse::desde)
                .toList();
        return ResponseEntity.ok(viajes);
    }

    /** Variante por coordenadas, para cuando el origen sale del GPS. */
    @GetMapping("/desde-punto")
    public ResponseEntity<List<ViajeResponse>> desdePunto(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam("destino") @NotBlank String destino) {

        final var hasta = viajeService.ubicar(destino);
        return hasta.map(coordenada -> ResponseEntity.ok(viajeService.comoLlegar(new Coordenada(lat, lon), coordenada).stream()
                .map(ViajeResponse::desde)
                .toList())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
