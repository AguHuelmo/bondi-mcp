package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * El loop del bot: escucha Telegram por long polling y responde cada mensaje.
 *
 * <p>Alcanza con el token de Telegram: el modo por defecto es el {@link RespondedorDirecto},
 * que no le paga a nadie por mensaje. Si además hay una API key de Anthropic configurada, las
 * respuestas pasan a {@link ConversacionBot} y el bot entiende lenguaje natural — útil para uso
 * propio o demos, no para dejarlo público gastando tokens ajenos.
 *
 * <p>Sin token de Telegram el componente queda dormido y la app sigue siendo MCP + REST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramPoller implements ApplicationRunner {

    private final BotProperties properties;
    private final TelegramClient telegramClient;
    private final ConversacionBot conversacionBot;
    private final RespondedorDirecto respondedorDirecto;
    private final Mensajero mensajero;

    /** Un candado por chat: dos mensajes seguidos del mismo usuario no deben pisarse el historial. */
    private final Map<Long, Object> candados = new ConcurrentHashMap<>();

    private volatile boolean activo = true;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.telegram().configurado()) {
            log.info("Bot de Telegram apagado: no hay bot.telegram.token configurado");
            return;
        }
        log.info("Bot de Telegram en modo {}", conversacionBot.habilitado()
                ? "lenguaje natural (Claude)"
                : "comandos (sin LLM, gratis por mensaje)");
        Thread.ofVirtual().name("telegram-poller").start(this::sondear);
    }

    @PreDestroy
    void detener() {
        activo = false;
    }

    private void sondear() {
        log.info("Bot de Telegram escuchando (long polling de {} s)", TelegramClient.POLL_SEGUNDOS);
        long offset = 0;
        while (activo) {
            try {
                for (final TelegramClient.Actualizacion actualizacion
                        : telegramClient.obtenerActualizaciones(offset)) {
                    offset = actualizacion.updateId() + 1;
                    despachar(actualizacion);
                }
            }
            catch (RuntimeException ex) {
                log.warn("Falló el polling de Telegram; se reintenta enseguida: {}", ex.getMessage());
                dormir();
            }
        }
    }

    /**
     * Atiende un mensaje en su propio hilo virtual.
     *
     * <p>Así un chat con una consulta lenta (Claude + la API de la Intendencia) no bloquea a los
     * demás; el candado por chat mantiene el orden dentro de cada conversación.
     */
    private void despachar(TelegramClient.Actualizacion actualizacion) {
        final TelegramClient.Mensaje mensaje = actualizacion.message();
        if (mensaje == null || mensaje.chat() == null || !tieneContenido(mensaje)) {
            return;
        }
        final long chatId = mensaje.chat().id();
        Thread.ofVirtual().name("telegram-chat-" + chatId).start(() -> {
            synchronized (candados.computeIfAbsent(chatId, id -> new Object())) {
                try {
                    telegramClient.mostrarEscribiendo(chatId);
                    mensajero.enviar(Charla.telegram(chatId), responderA(chatId, mensaje));
                }
                catch (RuntimeException ex) {
                    log.warn("No se pudo responder al chat {}: {}", chatId, ex.getMessage());
                }
            }
        });
    }

    private String responderA(long chatId, TelegramClient.Mensaje mensaje) {
        if (conversacionBot.habilitado()) {
            return conversacionBot.responder(chatId, textoParaElAgente(mensaje));
        }
        final Charla charla = Charla.telegram(chatId);
        if (mensaje.location() != null) {
            return respondedorDirecto.responderUbicacion(charla,
                    mensaje.location().latitude(), mensaje.location().longitude());
        }
        return respondedorDirecto.responder(charla, mensaje.text());
    }

    private static boolean tieneContenido(TelegramClient.Mensaje mensaje) {
        return mensaje.location() != null || (mensaje.text() != null && !mensaje.text().isBlank());
    }

    /**
     * El texto que ve el agente en modo Claude. Una ubicación compartida se traduce a texto con
     * las coordenadas: el agente sabe que eso significa llamar a paradas_cercanas.
     */
    private static String textoParaElAgente(TelegramClient.Mensaje mensaje) {
        if (mensaje.location() != null) {
            return String.format(Locale.ROOT,
                    "[El usuario compartió su ubicación de Telegram: latitud %.6f, longitud %.6f]",
                    mensaje.location().latitude(), mensaje.location().longitude());
        }
        return mensaje.text();
    }

    private static void dormir() {
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
