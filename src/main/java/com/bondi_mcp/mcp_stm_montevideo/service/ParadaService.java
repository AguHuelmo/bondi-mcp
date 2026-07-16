package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.config.ParadasProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;

/**
 * Búsqueda de paradas. Lógica compartida por las tools MCP y los controllers REST.
 *
 * <p>Busca sobre el caché local porque la API de la Intendencia no ofrece búsqueda por texto.
 *
 * <p>Limitación conocida de v0: no se puede buscar por número de línea. {@code BusStopItem} solo
 * trae calle, esquina y ubicación — la relación línea↔parada no está en ese endpoint y traerla
 * exigiría parsear el GTFS estático o pegarle a /lines por cada parada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParadaService {

    private final ParadaRepository repository;
    private final ParadaCacheService cacheService;
    private final ParadasProperties properties;

    /**
     * Busca paradas por dirección o cruce (ej. "18 de julio y ejido"), o por código exacto.
     *
     * <p>Las palabras se matchean todas y en cualquier orden.
     *
     * @return paradas candidatas, o vacío si no hay match
     */
    @Transactional
    public List<Parada> buscar(String consulta) {
        final List<String> palabras = TextoNormalizador.enPalabrasDeBusqueda(consulta);
        if (palabras.isEmpty()) {
            return List.of();
        }

        cacheService.asegurarCacheFresco();

        final String[] patrones = palabras.stream().map(palabra -> "%" + palabra + "%").toArray(String[]::new);
        final List<Parada> resultados = repository
                .buscarPorPatrones(patrones, Limit.of(properties.maxResultadosBusqueda()))
                .stream()
                .map(ParadaEntity::aParada)
                .toList();

        log.debug("Búsqueda '{}' -> {} paradas", consulta, resultados.size());
        return resultados;
    }

    /** Una parada por su código, si está en el caché. */
    @Transactional
    public Optional<Parada> porCodigo(long codigo) {
        cacheService.asegurarCacheFresco();
        return repository.findById(codigo).map(ParadaEntity::aParada);
    }
}
