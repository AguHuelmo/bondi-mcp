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
public record BotProperties(@DefaultValue Telegram telegram, @DefaultValue Whatsapp whatsapp,
        @DefaultValue Claude claude) {

    /** Acceso a la API de bots de Telegram. El token lo da @BotFather. */
    public record Telegram(
            @DefaultValue("") String token,
            @DefaultValue("https://api.telegram.org") String baseUrl) {

        public boolean configurado() {
            return !token.isBlank();
        }
    }

    /**
     * Acceso a la Cloud API de WhatsApp (Meta). Todo sale de developers.facebook.com.
     *
     * @param accessToken   token de acceso de la app de Meta
     * @param phoneNumberId id del número emisor (no es el número de teléfono)
     * @param verifyToken   secreto que uno inventa y repite al registrar el webhook; Meta lo
     *                      manda en el GET de verificación para probar que el server es nuestro
     * @param appSecret     opcional; si está, se verifica la firma HMAC de cada webhook entrante
     */
    public record Whatsapp(
            @DefaultValue("") String accessToken,
            @DefaultValue("") String phoneNumberId,
            @DefaultValue("") String verifyToken,
            @DefaultValue("") String appSecret,
            @DefaultValue("https://graph.facebook.com/v21.0") String baseUrl) {

        public boolean configurado() {
            return !accessToken.isBlank() && !phoneNumberId.isBlank() && !verifyToken.isBlank();
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
