package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador HTTP contra la API de bots de Telegram.
 *
 * <p>Único lugar que conoce ese contrato. Usa long polling ({@code getUpdates} con
 * {@code timeout}): no hace falta webhook ni exponer un puerto público, así que el bot corre
 * desde cualquier máquina con salida a internet.
 */
@Slf4j
@Component
public class TelegramClient {

    /** Cuánto mantiene Telegram abierta la conexión del long polling esperando novedades. */
    static final int POLL_SEGUNDOS = 30;

    /** Telegram corta los mensajes en 4096; cortamos nosotros antes, en un límite prolijo. */
    private static final int MAXIMO_LARGO_MENSAJE = 4000;

    private final RestClient restClient;
    private final String token;

    public TelegramClient(BotProperties properties) {
        this.token = properties.telegram().token();
        // Cliente propio y no el builder global de la app: el long polling necesita un read
        // timeout mayor que los 10s generales, si no cada poll moriría antes de responder.
        final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(POLL_SEGUNDOS + 15L));
        this.restClient = RestClient.builder()
                .baseUrl(properties.telegram().baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Espera y devuelve las novedades desde {@code offset}.
     *
     * <p>Bloquea hasta {@link #POLL_SEGUNDOS} si no hay nada: es el long polling normal de
     * Telegram, no un error.
     */
    public List<Actualizacion> obtenerActualizaciones(long offset) {
        final RespuestaTelegram<List<Actualizacion>> respuesta = restClient.get()
                .uri(uri -> uri.path("/bot{token}/getUpdates")
                        .queryParam("timeout", POLL_SEGUNDOS)
                        .queryParam("offset", offset)
                        .build(token))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return (respuesta == null || respuesta.result() == null) ? List.of() : respuesta.result();
    }

    /** Manda un mensaje de texto plano, partido en pedazos si supera el límite de Telegram. */
    public void enviarMensaje(long chatId, String texto) {
        for (final String parte : partir(texto)) {
            restClient.post()
                    .uri("/bot{token}/sendMessage", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", parte))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    /**
     * Muestra "escribiendo…" en el chat mientras el agente piensa.
     *
     * <p>Es puro feedback: si falla no pasa nada, así que nunca voltea la respuesta de verdad.
     */
    public void mostrarEscribiendo(long chatId) {
        try {
            restClient.post()
                    .uri("/bot{token}/sendChatAction", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "action", "typing"))
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (RestClientException ex) {
            log.debug("No se pudo mostrar 'escribiendo' en el chat {}: {}", chatId, ex.getMessage());
        }
    }

    /** Parte un texto en pedazos que entren en un mensaje de Telegram, cortando por renglón si puede. */
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

    /** Sobre estándar de la API de Telegram: {@code ok} más el resultado pedido. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RespuestaTelegram<T>(boolean ok, T result) {
    }

    /** Una novedad del long polling. Solo nos importan las que traen mensaje. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Actualizacion(@JsonProperty("update_id") long updateId, Mensaje message) {
    }

    /** Un mensaje entrante: texto, o una ubicación compartida. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mensaje(Chat chat, String text, Ubicacion location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id) {
    }

    /** La ubicación que el usuario comparte con el clip de Telegram. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ubicacion(double latitude, double longitude) {
    }
}
