package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * El webhook de WhatsApp: Meta empuja acá cada mensaje que le escriben al número.
 *
 * <p>A diferencia de Telegram (long polling), la Cloud API exige una URL HTTPS pública. En
 * desarrollo se resuelve con un túnel (cloudflared, ngrok) apuntando a este endpoint; en
 * producción, cualquier server con TLS. El flujo entrante es: Meta manda un GET de verificación
 * al registrar la URL, y después POSTs con los mensajes. Las respuestas salen por
 * {@link WhatsAppClient} vía el {@link Mensajero}.
 *
 * <p>WhatsApp atiende siempre en modo comandos ({@link RespondedorDirecto}): es el canal pensado
 * para quedar público, así que nunca gasta tokens de nadie.
 */
@Slf4j
@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    /** Cuántos ids de mensajes recordar para no responder dos veces los reintentos de Meta. */
    private static final int IDS_RECORDADOS = 500;

    private final BotProperties properties;
    private final RespondedorDirecto respondedorDirecto;
    private final Mensajero mensajero;
    /** Boot 4 autoconfigura el {@code JsonMapper} de Jackson 3; no hay bean de {@code ObjectMapper} 2. */
    private final JsonMapper jsonMapper;

    /** Meta reintenta los webhooks sin respuesta 200; este set evita contestar dos veces. */
    private final Set<String> idsAtendidos = Collections.newSetFromMap(
            Collections.synchronizedMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > IDS_RECORDADOS;
                }
            }));

    /**
     * La verificación de Meta al registrar el webhook: si el token coincide con el nuestro, hay
     * que devolver el challenge tal cual.
     */
    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam(value = "hub.mode", required = false) String modo,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        if (properties.whatsapp().configurado()
                && "subscribe".equals(modo)
                && properties.whatsapp().verifyToken().equals(token)) {
            log.info("Webhook de WhatsApp verificado por Meta");
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * Un lote de novedades de Meta. Se responde 200 siempre que la firma sea válida: devolver
     * error por un mensaje que no entendemos haría que Meta lo reintente para siempre.
     *
     * <p>El cuerpo llega como texto crudo a propósito: la firma HMAC se calcula sobre los bytes
     * exactos, y cualquier re-serialización la rompería.
     */
    @PostMapping
    public ResponseEntity<Void> recibir(
            @RequestBody String cuerpo,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String firma) {

        if (!properties.whatsapp().configurado()) {
            return ResponseEntity.ok().build();
        }
        if (!firmaValida(cuerpo, firma)) {
            log.warn("Webhook de WhatsApp con firma inválida; se descarta");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            final Notificacion notificacion = jsonMapper.readValue(cuerpo, Notificacion.class);
            mensajesDe(notificacion).forEach(this::atender);
        }
        catch (Exception ex) {
            // Un payload raro no puede tirar el webhook: se loguea y Meta recibe su 200.
            log.warn("No se pudo procesar un webhook de WhatsApp: {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private void atender(MensajeWa mensaje) {
        if (mensaje.from() == null || (mensaje.id() != null && !idsAtendidos.add(mensaje.id()))) {
            return;
        }
        final Charla charla = Charla.whatsapp(mensaje.from());
        final String respuesta;
        if (mensaje.location() != null) {
            respuesta = respondedorDirecto.responderUbicacion(charla,
                    mensaje.location().latitude(), mensaje.location().longitude());
        }
        else if (mensaje.text() != null && mensaje.text().body() != null) {
            respuesta = respondedorDirecto.responder(charla, mensaje.text().body());
        }
        else {
            // Audio, sticker, imagen: se contesta qué sí sabemos hacer, no silencio.
            respuesta = respondedorDirecto.responder(charla, "/ayuda");
        }
        mensajero.enviar(charla, respuesta);
    }

    /**
     * Valida la firma HMAC-SHA256 del cuerpo contra el app secret de Meta.
     *
     * <p>Nunca hay un camino que acepte sin firma: el app secret es obligatorio para que la pata
     * de WhatsApp se considere configurada ({@link BotProperties.Whatsapp#configurado()}), así
     * que si falta el webhook queda apagado en vez de aceptar cualquier POST.
     */
    private boolean firmaValida(String cuerpo, String firma) {
        final String secreto = properties.whatsapp().appSecret();
        if (firma == null || !firma.startsWith("sha256=")) {
            return false;
        }
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final String esperada = HexFormat.of()
                    .formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    esperada.getBytes(StandardCharsets.UTF_8),
                    firma.substring("sha256=".length()).getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception ex) {
            log.warn("No se pudo verificar la firma del webhook: {}", ex.getMessage());
            return false;
        }
    }

    private static List<MensajeWa> mensajesDe(Notificacion notificacion) {
        if (notificacion.entry() == null) {
            return List.of();
        }
        return notificacion.entry().stream()
                .filter(entrada -> entrada.changes() != null)
                .flatMap(entrada -> entrada.changes().stream())
                .filter(cambio -> cambio.value() != null && cambio.value().messages() != null)
                .flatMap(cambio -> cambio.value().messages().stream())
                .toList();
    }

    /* El pedazo del contrato de Meta que usamos. Todo lo demás se ignora. */

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Notificacion(List<Entrada> entry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entrada(List<Cambio> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cambio(Valor value) {
    }

    /** {@code messages} trae los mensajes de la gente; los acuses de entrega vienen aparte y se ignoran. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Valor(List<MensajeWa> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MensajeWa(String id, String from, String type, Texto text, UbicacionWa location) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Texto(String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UbicacionWa(double latitude, double longitude) {
    }
}
