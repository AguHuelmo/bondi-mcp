package com.bondi_mcp.mcp_stm_montevideo.client;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.bondi_mcp.mcp_stm_montevideo.client.dto.DireccionItem;
import com.bondi_mcp.mcp_stm_montevideo.client.dto.PuntoNotableItem;
import com.bondi_mcp.mcp_stm_montevideo.config.GeocoderProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.DireccionUbicada;
import com.bondi_mcp.mcp_stm_montevideo.domain.LugarUbicado;

import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador HTTP contra direcciones.ide.uy, el servicio de direcciones del IDE.
 *
 * <p>Único lugar del proyecto que conoce ese contrato. Sin autenticación: el servicio es abierto.
 */
@Slf4j
@Component
public class IdeUyGeocoder implements Geocoder {

    /** Cuántos lugares traer: hay que poder descartar los que no matchean sin quedarse sin opciones. */
    private static final int CANDIDATOS_POR_LUGAR = 5;

    private final RestClient restClient;
    private final GeocoderProperties properties;

    public IdeUyGeocoder(GeocoderProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>El parámetro se llama {@code calle} pero recibe la dirección entera, número incluido:
     * así lo espera el servicio.
     *
     * <p>Devuelve varios candidatos ordenados de mejor a peor, y los malos no vienen marcados como
     * tales salvo por {@code error} (ver {@link DireccionItem#esExacta()}). Nos quedamos con el
     * primero que sea exacto: pedir "18 de julio 1360" contesta "AVENIDA 18 DE JULIO 1360" y
     * después "14 DE JULIO 1360", las dos exactas, y la buena es la primera.
     */
    @Override
    public Optional<DireccionUbicada> ubicar(String direccion) {
        final List<DireccionItem> candidatos = buscar(direccion);
        final Optional<DireccionUbicada> ubicada = candidatos.stream()
                .filter(DireccionItem::esExacta)
                .findFirst()
                .map(DireccionItem::aDireccionUbicada);

        if (ubicada.isEmpty()) {
            log.debug("El geocoder no ubicó '{}' ({} candidatos, ninguno exacto)",
                    direccion, candidatos.size());
        }
        return ubicada;
    }

    private List<DireccionItem> buscar(String direccion) {
        return get(uri -> uri.path("/v0/geocode/BusquedaDireccion")
                        .queryParam("calle", direccion)
                        .queryParam("departamento", properties.departamento())
                        .build(),
                DireccionItem[].class,
                "la geocodificación de '" + direccion + "'");
    }

    /**
     * {@inheritDoc}
     *
     * <p>No filtra nada más allá de lo que no se puede usar: acá no hay forma de saber si el
     * servicio acertó (ver {@link PuntoNotableItem}). Los candidatos salen en el orden en que
     * vienen, que es de mejor a peor.
     */
    @Override
    public List<LugarUbicado> buscarLugares(String nombre) {
        return get(uri -> uri.path("/v1/geocode/direcPuntoNotable")
                        .queryParam("nombre", nombre)
                        .queryParam("departamento", properties.departamento())
                        .queryParam("limit", CANDIDATOS_POR_LUGAR)
                        .build(),
                PuntoNotableItem[].class,
                "la búsqueda del lugar '" + nombre + "'")
                .stream()
                .filter(PuntoNotableItem::esUtilizable)
                .map(PuntoNotableItem::aLugarUbicado)
                .toList();
    }

    /** GET que deserializa un array JSON. Sin auth: el servicio es abierto. */
    private <T> List<T> get(Function<UriBuilder, URI> uriFn, Class<T[]> arrayType, String queEstabaHaciendo) {
        try {
            final T[] cuerpo = restClient.get()
                    .uri(uriFn::apply)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(arrayType);

            return cuerpo == null ? List.of() : List.of(cuerpo);
        }
        catch (RestClientException ex) {
            // Incluye timeouts, 5xx y respuestas que no matchean el formato esperado.
            throw new GeocoderException("Falló " + queEstabaHaciendo, ex);
        }
    }
}
