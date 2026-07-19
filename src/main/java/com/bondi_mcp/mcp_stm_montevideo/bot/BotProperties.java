package com.bondi_mcp.mcp_stm_montevideo.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del bot conversacional.
 *
 * <p>Las dos credenciales son opcionales a propósito: sin ellas la app arranca igual y el bot
 * queda apagado. El MCP y la API REST no dependen del bot para nada.
 */
@ConfigurationProperties("bot")
public record BotProperties(@DefaultValue Telegram telegram, @DefaultValue Claude claude) {

    /** Acceso a la API de bots de Telegram. El token lo da @BotFather. */
    public record Telegram(
            @DefaultValue("") String token,
            @DefaultValue("https://api.telegram.org") String baseUrl) {

        public boolean configurado() {
            return !token.isBlank();
        }
    }

    /**
     * Acceso a la API de Anthropic.
     *
     * @param maxTurnosDeHistorial cuántos mensajes (de usuario y del bot sumados) se recuerdan
     *                             por chat; más allá de eso los viejos se van olvidando
     */
    public record Claude(
            @DefaultValue("") String apiKey,
            @DefaultValue("claude-opus-4-8") String model,
            @DefaultValue("30") int maxTurnosDeHistorial) {

        public boolean configurado() {
            return !apiKey.isBlank();
        }
    }
}
