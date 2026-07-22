package com.bondi_mcp.mcp_stm_montevideo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Quién puede consumir esta instancia y a qué ritmo.
 *
 * <p>Hay un costo real detrás de cada request: la cuota de la API de la Intendencia es por
 * cuenta, así que un tercero que descubra la URL gasta tu cupo, no el suyo. Estas dos perillas
 * son la defensa mínima antes de dejar el server público.
 *
 * @param mcpToken        si no está vacío, {@code /mcp} exige {@code Authorization: Bearer <token>}.
 *                        Vacío deja el endpoint abierto (cómodo en local) y avisa por log al arrancar.
 * @param limitePorMinuto requests por IP por minuto sobre {@code /api} y {@code /mcp}; 0 lo apaga
 */
@ConfigurationProperties("acceso")
public record AccesoProperties(
        @DefaultValue("") String mcpToken,
        @DefaultValue("120") int limitePorMinuto) {

    public AccesoProperties {
        if (limitePorMinuto < 0) {
            throw new IllegalArgumentException("acceso.limite-por-minuto no puede ser negativo");
        }
    }

    public boolean mcpProtegido() {
        return !mcpToken.isBlank();
    }

    public boolean limiteActivo() {
        return limitePorMinuto > 0;
    }
}
