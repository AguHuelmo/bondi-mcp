package com.bondi_mcp.mcp_stm_montevideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del servicio de direcciones del IDE (direcciones.ide.uy).
 *
 * <p>Es el Sistema Único de Direcciones Geográficas del Uruguay, y es de otro organismo que la API
 * de transporte: no comparten host, ni credenciales, ni ciclo de vida. Por eso tiene su propia
 * config y no cuelga de {@link MontevideoApiProperties}.
 *
 * <p>No lleva credenciales: es abierto y sin autenticación, a diferencia del de transporte.
 *
 * <p>Los timeouts no están acá: van en {@code spring.http.clients.*}, igual que los del resto.
 */
@ConfigurationProperties("montevideo.geocoder")
public record GeocoderProperties(
        @DefaultValue("https://direcciones.ide.uy/api") String baseUrl,
        /* El servicio cubre todo el país y hay calles con el mismo nombre en varios departamentos;
         * sin esto, "Gabriel Pereira 2470" puede resolverse en Pando. */
        @DefaultValue("MONTEVIDEO") String departamento) {
}
