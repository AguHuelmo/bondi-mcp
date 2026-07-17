package com.bondi_mcp.mcp_stm_montevideo.client.dto;

import java.time.Duration;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Item de {@code GET /buses/busstops/{busstopId}/upcomingbuses}.
 *
 * <p>OJO — la spec de la Intendencia es inconsistente acá. Declara que ese endpoint devuelve
 * {@code BusLineVariantItem[]}, que no tiene ningún campo de tiempo de arribo, y a la vez
 * define un {@code ETAItem} (con {@code eta}, {@code distance}, {@code position}) que ningún
 * endpoint referencia. La respuesta real es casi con seguridad {@code ETAItem[]} y el $ref
 * quedó mal, así que modelamos ETAItem.
 *
 * <p>Por las dudas el mapeo es tolerante: campos de más se ignoran y un item sin {@code eta}
 * se descarta en vez de romper la respuesta entera. Verificar contra la API viva cuando haya
 * credenciales.
 *
 * @param eta      tiempo estimado de arribo en SEGUNDOS
 * @param distance distancia a la parada en metros
 * @param position posición en la lista de próximos a llegar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EtaItem(
        Integer busId,
        String companyName,
        String line,
        String lineVariantId,
        String origin,
        String destination,
        String subline,
        Boolean special,
        String access,
        String thermalConfort,
        String emissions,
        Integer eta,
        Integer distance,
        Integer position,
        GeoPoint location) {

    /** {@code true} si el item trae lo mínimo para ser un arribo utilizable. */
    public boolean esUtilizable() {
        return eta != null && line != null && !line.isBlank();
    }

    public Arribo aArribo() {
        return new Arribo(line, destination, Duration.ofSeconds(eta), distance, companyName,
                location == null ? null : location.aCoordenada());
    }
}
