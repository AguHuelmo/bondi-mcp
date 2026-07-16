package com.bondi_mcp.mcp_stm_montevideo.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del caché local de paradas.
 *
 * <p>El endpoint {@code GET /buses/busstops} de la Intendencia no acepta ningún filtro:
 * devuelve la colección completa. Por eso la búsqueda por texto se resuelve localmente
 * sobre este caché y no contra la API externa.
 */
@ConfigurationProperties("montevideo.paradas")
public record ParadasProperties(
        @DefaultValue("24h") Duration cacheTtl,
        @DefaultValue("20") int maxResultadosBusqueda) {
}
