package com.bondi_mcp.mcp_stm_montevideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuración de acceso a la API de Transporte Público de la Intendencia de Montevideo.
 *
 * <p>La API usa OAuth2 client_credentials: no hay API key. Las credenciales se obtienen
 * registrando una cuenta gratuita en https://api.montevideo.gub.uy y nunca se hardcodean;
 * llegan por entorno vía {@code MONTEVIDEO_CLIENT_ID} / {@code MONTEVIDEO_CLIENT_SECRET}.
 *
 * <p>Los timeouts no están acá: van en {@code spring.http.clients.*}, que Boot aplica a los
 * {@code RestClient} de la app (que son justamente los de Montevideo).
 */
@ConfigurationProperties("montevideo.api")
public record MontevideoApiProperties(
        @DefaultValue("https://api.montevideo.gub.uy/api/transportepublico") String baseUrl,
        @DefaultValue("https://mvdapi-auth.montevideo.gub.uy/token") String tokenUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret) {
}
