package com.bondi_mcp.mcp_stm_montevideo.bot;

/**
 * Una conversación con alguien, sin importar por dónde llegó.
 *
 * <p>Es lo que permite que el mismo cerebro del bot atienda Telegram y WhatsApp: el respondedor
 * y la guardia de alertas trabajan sobre una charla, y recién a la salida el {@link Mensajero}
 * decide por qué canal contestar. Sin esto, una alerta pedida por WhatsApp terminaría avisándole
 * a un chat de Telegram con el mismo número.
 *
 * @param canal por dónde habla
 * @param id    el identificador dentro de ese canal: el chat id numérico en Telegram, el
 *              teléfono (wa_id) en WhatsApp
 */
public record Charla(Canal canal, String id) {

    public enum Canal {
        TELEGRAM, WHATSAPP
    }

    public static Charla telegram(long chatId) {
        return new Charla(Canal.TELEGRAM, Long.toString(chatId));
    }

    public static Charla whatsapp(String waId) {
        return new Charla(Canal.WHATSAPP, waId);
    }
}
