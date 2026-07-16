package com.bondi_mcp.mcp_stm_montevideo.client;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.bondi_mcp.mcp_stm_montevideo.client.dto.BusStopItem;
import com.bondi_mcp.mcp_stm_montevideo.client.dto.EtaItem;
import com.bondi_mcp.mcp_stm_montevideo.client.dto.LineByBusStopItem;
import com.bondi_mcp.mcp_stm_montevideo.config.MontevideoApiProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;

import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador HTTP contra api.montevideo.gub.uy.
 *
 * <p>Único lugar del proyecto que conoce el formato de la Intendencia. Traduce todo a tipos
 * del dominio y encapsula cualquier falla en {@link TransportePublicoException}.
 */
@Slf4j
@Component
public class MontevideoTransportePublicoClient implements TransportePublicoClient {

    private final RestClient restClient;
    private final OAuth2TokenManager tokenManager;

    public MontevideoTransportePublicoClient(MontevideoApiProperties properties,
            OAuth2TokenManager tokenManager, RestClient.Builder builder) {
        this.tokenManager = tokenManager;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public List<Parada> obtenerTodasLasParadas() {
        final List<BusStopItem> items = get(
                uri -> uri.path("/buses/busstops").build(),
                BusStopItem[].class,
                "listado de paradas");

        return items.stream()
                .filter(item -> item.busstopId() != null)
                .map(BusStopItem::aParada)
                .toList();
    }

    @Override
    public List<String> obtenerLineasDeParada(long codigoParada) {
        final List<LineByBusStopItem> items = get(
                uri -> uri.path("/buses/busstops/{id}/lines").build(codigoParada),
                LineByBusStopItem[].class,
                "líneas de la parada " + codigoParada);

        return items.stream()
                .map(LineByBusStopItem::line)
                .filter(Objects::nonNull)
                .filter(linea -> !linea.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public List<Arribo> obtenerProximosArribos(long codigoParada, List<String> lineas, int cantidadPorLinea) {
        if (lineas.isEmpty()) {
            // La API rechaza el request sin `lines`; evitamos el 400 seguro.
            return List.of();
        }

        // `lines` es obligatorio y se manda separado por comas, según la doc del endpoint.
        final String lineasCsv = String.join(",", lineas);
        final List<EtaItem> items = get(
                uri -> uri.path("/buses/busstops/{id}/upcomingbuses")
                        .queryParam("lines", lineasCsv)
                        .queryParam("amountperline", cantidadPorLinea)
                        .build(codigoParada),
                EtaItem[].class,
                "arribos de la parada " + codigoParada);

        final List<EtaItem> utilizables = items.stream().filter(EtaItem::esUtilizable).toList();
        if (utilizables.size() < items.size()) {
            log.warn("La API devolvió {} arribos sin datos usables para la parada {}; se ignoran",
                    items.size() - utilizables.size(), codigoParada);
        }

        return utilizables.stream()
                .map(EtaItem::aArribo)
                .sorted((a, b) -> a.espera().compareTo(b.espera()))
                .toList();
    }

    /**
     * Ejecuta un GET autenticado y deserializa un array JSON.
     *
     * <p>Si el token fue revocado o venció antes de tiempo, reintenta una vez con uno nuevo.
     */
    private <T> List<T> get(Function<UriBuilder, URI> uriFn, Class<T[]> arrayType, String queEstabaHaciendo) {
        try {
            return ejecutar(uriFn, arrayType);
        }
        catch (TokenRechazadoException ex) {
            log.info("La API rechazó el token; renovando y reintentando: {}", queEstabaHaciendo);
            tokenManager.invalidar();
            try {
                return ejecutar(uriFn, arrayType);
            }
            catch (TokenRechazadoException reintento) {
                throw new TransportePublicoException(
                        "La API de Montevideo rechazó las credenciales al pedir " + queEstabaHaciendo, reintento);
            }
        }
    }

    private <T> List<T> ejecutar(Function<UriBuilder, URI> uriFn, Class<T[]> arrayType) {
        try {
            final T[] cuerpo = restClient.get()
                    .uri(uriFn::apply)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenManager.obtenerToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status.value() == 401 || status.value() == 403,
                            (request, response) -> {
                                throw new TokenRechazadoException(response.getStatusCode().value());
                            })
                    .body(arrayType);

            return cuerpo == null ? List.of() : List.of(cuerpo);
        }
        catch (RestClientException ex) {
            // Incluye timeouts, 5xx y respuestas que no matchean el formato esperado.
            throw new TransportePublicoException("Falló la llamada a la API de Montevideo", ex);
        }
    }

    /** Marcador interno para disparar el reintento con token renovado. */
    private static class TokenRechazadoException extends RuntimeException {
        TokenRechazadoException(int status) {
            super("La API respondió " + status);
        }
    }
}
