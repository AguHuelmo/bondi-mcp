package com.bondi_mcp.mcp_stm_montevideo.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.service.EstimadorDeLlegada;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.LlegoResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ViajeResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    private final EstimadorDeLlegada estimadorDeLlegada;

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

    /**
     * {@code GET /api/viajes/llego?origen=...&destino=...&hora=18:30}
     *
     * <p>El veredicto de "¿llego a tiempo?" para una hora objetivo de hoy. 404 si alguna punta
     * no se pudo ubicar; 400 si la hora no es HH:MM; 200 con {@code motivo} cuando no hay
     * pronóstico posible (hora pasada, sin viaje directo con salida hoy).
     */
    @GetMapping("/llego")
    public ResponseEntity<LlegoResponse> llego(
            @RequestParam("origen") @NotBlank String origen,
            @RequestParam("destino") @NotBlank String destino,
            @RequestParam("hora") @NotBlank String hora) {

        final LocalTime horaObjetivo;
        try {
            horaObjetivo = LocalTime.parse(hora.trim(), DateTimeFormatter.ofPattern("H:mm"));
        }
        catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().build();
        }

        final LocalDateTime ahora = LocalDateTime.now(HorarioTeoricoService.ZONA_MONTEVIDEO);
        if (!horaObjetivo.isAfter(ahora.toLocalTime())) {
            return ResponseEntity.ok(LlegoResponse.sin("Esa hora ya pasó hoy."));
        }

        final var desde = viajeService.ubicar(origen);
        final var hasta = viajeService.ubicar(destino);
        if (desde.isEmpty() || hasta.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        return ResponseEntity.ok(estimadorDeLlegada.pronosticar(desde.get(), hasta.get(), horaObjetivo)
                .map(LlegoResponse::de)
                .orElseGet(() -> LlegoResponse.sin(
                        "No hay viaje directo con salida en lo que queda del día.")));
    }

    /** Variante por coordenadas, para cuando el origen sale del GPS. */
    @GetMapping("/desde-punto")
    public ResponseEntity<List<ViajeResponse>> desdePunto(
            @RequestParam("lat") @Min(-90) @Max(90) double lat,
            @RequestParam("lon") @Min(-180) @Max(180) double lon,
            @RequestParam("destino") @NotBlank String destino) {

        final var hasta = viajeService.ubicar(destino);
        return hasta.map(coordenada -> ResponseEntity.ok(viajeService.comoLlegar(new Coordenada(lat, lon), coordenada).stream()
                .map(ViajeResponse::desde)
                .toList())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
