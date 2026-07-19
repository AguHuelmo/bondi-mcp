package com.bondi_mcp.mcp_stm_montevideo.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** El webhook de WhatsApp: verificación de Meta, despacho de mensajes y dedupe de reintentos. */
@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    private static final String UN_MENSAJE = """
            {"entry":[{"changes":[{"value":{"messages":[
              {"id":"wamid.abc","from":"59891234567","type":"text","text":{"body":"3977"}}
            ]}}]}]}""";

    @Mock
    private RespondedorDirecto respondedorDirecto;

    @Mock
    private Mensajero mensajero;

    private WhatsAppWebhookController controller() {
        return controller("");
    }

    private WhatsAppWebhookController controller(String appSecret) {
        final BotProperties properties = new BotProperties(
                new BotProperties.Telegram("", "https://api.telegram.org"),
                new BotProperties.Whatsapp("un-token", "123456", "verificame", appSecret,
                        "https://graph.facebook.com/v21.0"),
                new BotProperties.Claude("", "claude-opus-4-8", 30));
        return new WhatsAppWebhookController(properties, respondedorDirecto, mensajero, new ObjectMapper());
    }

    @Test
    void la_verificacion_de_meta_devuelve_el_challenge_si_el_token_coincide() {
        final ResponseEntity<String> respuesta =
                controller().verificar("subscribe", "verificame", "12345");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        assertThat(respuesta.getBody()).isEqualTo("12345");
    }

    @Test
    void la_verificacion_con_token_equivocado_es_403() {
        assertThat(controller().verificar("subscribe", "otro", "12345").getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void un_mensaje_de_texto_se_responde_por_la_charla_de_whatsapp() {
        final Charla charla = Charla.whatsapp("59891234567");
        given(respondedorDirecto.responder(charla, "3977")).willReturn("los arribos");

        final ResponseEntity<Void> respuesta = controller().recibir(UN_MENSAJE, null);

        assertThat(respuesta.getStatusCode().value()).isEqualTo(200);
        verify(mensajero).enviar(charla, "los arribos");
    }

    @Test
    void los_reintentos_de_meta_no_se_responden_dos_veces() {
        given(respondedorDirecto.responder(Charla.whatsapp("59891234567"), "3977")).willReturn("ok");
        final WhatsAppWebhookController controller = controller();

        controller.recibir(UN_MENSAJE, null);
        controller.recibir(UN_MENSAJE, null);

        verify(mensajero, times(1)).enviar(Charla.whatsapp("59891234567"), "ok");
    }

    @Test
    void un_acuse_de_entrega_sin_mensajes_no_hace_nada() {
        final String soloEstados = """
                {"entry":[{"changes":[{"value":{"statuses":[{"id":"wamid.x","status":"delivered"}]}}]}]}""";

        assertThat(controller().recibir(soloEstados, null).getStatusCode().value()).isEqualTo(200);
        verify(respondedorDirecto, never()).responder(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void con_app_secret_configurado_una_firma_invalida_se_descarta() {
        final ResponseEntity<Void> respuesta =
                controller("mi-secreto").recibir(UN_MENSAJE, "sha256=firmatrucha");

        assertThat(respuesta.getStatusCode().value()).isEqualTo(403);
        verify(mensajero, never()).enviar(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void una_ubicacion_compartida_va_a_paradas_cercanas() {
        final String conUbicacion = """
                {"entry":[{"changes":[{"value":{"messages":[
                  {"id":"wamid.loc","from":"59891234567","type":"location",
                   "location":{"latitude":-34.9,"longitude":-56.16}}
                ]}}]}]}""";
        final Charla charla = Charla.whatsapp("59891234567");
        given(respondedorDirecto.responderUbicacion(charla, -34.9, -56.16)).willReturn("cercanas");

        controller().recibir(conUbicacion, null);

        verify(mensajero).enviar(charla, "cercanas");
    }
}
