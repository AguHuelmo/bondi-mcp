package com.bondi_mcp.mcp_stm_montevideo.client;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bondi_mcp.mcp_stm_montevideo.config.MontevideoApiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Obtiene y renueva el access token OAuth2 (client_credentials) del portal de la Intendencia.
 *
 * <p>El token se cachea en memoria y se renueva solo cuando está por vencer; no se pide uno
 * nuevo en cada request.
 */
@Slf4j
@Component
public class OAuth2TokenManager {

    private static final Duration MARGEN_RENOVACION = Duration.ofSeconds(30);

    private final MontevideoApiProperties properties;
    private final RestClient authClient;
    private final AtomicReference<TokenVigente> cache = new AtomicReference<>();

    public OAuth2TokenManager(MontevideoApiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.authClient = builder.build();
    }

    public String obtenerToken() {
        final TokenVigente actual = cache.get();
        if (actual != null && actual.sigueValido()) {
            return actual.token();
        }
        final TokenVigente nuevo = solicitarToken();
        cache.set(nuevo);
        return nuevo.token();
    }

    /** Invalida el token cacheado para forzar una renovación en el próximo uso. */
    public void invalidar() {
        cache.set(null);
    }

    private TokenVigente solicitarToken() {
        final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());

        try {
            final TokenResponse response = authClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new TransportePublicoException(
                        "El servidor de auth de Montevideo no devolvió un access_token");
            }
            log.debug("Token OAuth2 renovado, vence en {}s", response.expiresIn());
            return TokenVigente.desde(response);
        }
        catch (RestClientException ex) {
            throw new TransportePublicoException(
                    "No se pudo obtener el token OAuth2 de Montevideo. "
                            + "Revisá MONTEVIDEO_CLIENT_ID / MONTEVIDEO_CLIENT_SECRET.",
                    ex);
        }
    }

    /** Respuesta del endpoint de token. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    /** Token cacheado junto con su vencimiento. */
    private record TokenVigente(String token, Instant vence) {

        /** Si el server no informa expires_in, asumimos una vida corta y renovamos seguido. */
        private static final Duration VIDA_POR_DEFECTO = Duration.ofMinutes(5);

        static TokenVigente desde(TokenResponse response) {
            final Duration vida = response.expiresIn() == null
                    ? VIDA_POR_DEFECTO
                    : Duration.ofSeconds(response.expiresIn());
            return new TokenVigente(response.accessToken(), Instant.now().plus(vida));
        }

        boolean sigueValido() {
            return Instant.now().isBefore(vence.minus(MARGEN_RENOVACION));
        }
    }
}
