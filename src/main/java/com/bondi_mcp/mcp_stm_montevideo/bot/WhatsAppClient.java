package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador HTTP contra la Cloud API de WhatsApp (Graph API de Meta).
 *
 * <p>Único lugar que conoce ese contrato. Solo manda: los mensajes entrantes no se piden, los
 * empuja Meta al webhook ({@link WhatsAppWebhookController}). Responder mensajes que inicia el
 * usuario no tiene costo en la Cloud API; Meta cobra solo las plantillas que inicia el negocio.
 */
@Component
public class WhatsAppClient {

    private final RestClient restClient;
    private final BotProperties.Whatsapp properties;

    public WhatsAppClient(BotProperties properties, RestClient.Builder builder) {
        this.properties = properties.whatsapp();
        this.restClient = builder.baseUrl(this.properties.baseUrl()).build();
    }

    /** Manda un texto plano a un número. {@code destinatario} es el wa_id que llegó en el webhook. */
    public void enviarMensaje(String destinatario, String texto) {
        restClient.post()
                .uri("/{phoneNumberId}/messages", properties.phoneNumberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "messaging_product", "whatsapp",
                        "to", destinatario,
                        "type", "text",
                        "text", Map.of("body", texto)))
                .retrieve()
                .toBodilessEntity();
    }
}
