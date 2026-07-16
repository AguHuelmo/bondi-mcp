package com.bondi_mcp.mcp_stm_montevideo.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ArriboResponse;
import com.bondi_mcp.mcp_stm_montevideo.web.dto.ParadaResponse;

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

    private final ParadaService paradaService;
    private final ArriboService arriboService;

    /** {@code GET /api/paradas?query=18 de julio y ejido} */
    @GetMapping
    public List<ParadaResponse> buscar(@RequestParam("query") @NotBlank String query) {
        return paradaService.buscar(query).stream().map(ParadaResponse::desde).toList();
    }

    /** {@code GET /api/paradas/{codigo}/arribos} */
    @GetMapping("/{codigo}/arribos")
    public List<ArriboResponse> arribos(@PathVariable long codigo) {
        return arriboService.proximosArribos(codigo).stream().map(ArriboResponse::desde).toList();
    }
}
