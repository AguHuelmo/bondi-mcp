package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * La única puerta de salida del bot: manda un texto a una {@link Charla}, sea del canal que sea.
 *
 * <p>Acá vive también el corte de mensajes largos, porque es una regla de conversación y no de
 * un canal puntual: Telegram corta en 4096 y WhatsApp también, así que se parte una sola vez y
 * los clientes de cada canal mandan pedazos ya listos.
 */
@Component
@RequiredArgsConstructor
public class Mensajero {

    /** Ambos canales cortan en 4096; cortamos antes, en un límite prolijo. */
    private static final int MAXIMO_LARGO_MENSAJE = 4000;

    private final TelegramClient telegramClient;
    private final WhatsAppClient whatsAppClient;

    public void enviar(Charla charla, String texto) {
        for (final String parte : partir(texto)) {
            switch (charla.canal()) {
                case TELEGRAM -> telegramClient.enviarMensaje(Long.parseLong(charla.id()), parte);
                case WHATSAPP -> whatsAppClient.enviarMensaje(charla.id(), parte);
            }
        }
    }

    /** Parte un texto en pedazos que entren en un mensaje, cortando por renglón si puede. */
    static List<String> partir(String texto) {
        if (texto.length() <= MAXIMO_LARGO_MENSAJE) {
            return List.of(texto);
        }
        final List<String> partes = new ArrayList<>();
        String resto = texto;
        while (resto.length() > MAXIMO_LARGO_MENSAJE) {
            // Cortar en un salto de línea deja mensajes legibles; a mitad de palabra, solo si no hay.
            final int corte = ultimoCorte(resto);
            partes.add(resto.substring(0, corte).stripTrailing());
            resto = resto.substring(corte).stripLeading();
        }
        if (!resto.isBlank()) {
            partes.add(resto.stripTrailing());
        }
        return List.copyOf(partes);
    }

    private static int ultimoCorte(String texto) {
        final int salto = texto.lastIndexOf('\n', MAXIMO_LARGO_MENSAJE);
        if (salto > 0) {
            return salto;
        }
        final int espacio = texto.lastIndexOf(' ', MAXIMO_LARGO_MENSAJE);
        return espacio > 0 ? espacio : MAXIMO_LARGO_MENSAJE;
    }
}
