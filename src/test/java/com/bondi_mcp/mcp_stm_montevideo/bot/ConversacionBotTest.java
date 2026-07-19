package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthropic.core.JsonValue;
import com.bondi_mcp.mcp_stm_montevideo.mcp.TransporteMcpTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * El despacho de herramientas del bot: mismos nombres y parámetros que las tools MCP.
 *
 * <p>Sin API key el bot queda deshabilitado pero el despacho sigue siendo testeable: no
 * necesita el cliente de Anthropic para nada.
 */
@ExtendWith(MockitoExtension.class)
class ConversacionBotTest {

    @Mock
    private TransporteMcpTools transporte;

    private ConversacionBot bot() {
        return new ConversacionBot(new BotProperties(
                new BotProperties.Telegram("", "https://api.telegram.org"),
                new BotProperties.Whatsapp("", "", "", "", "https://graph.facebook.com/v21.0"),
                new BotProperties.Claude("", "claude-opus-4-8", 30)), transporte);
    }

    @Test
    void sin_api_key_el_bot_queda_deshabilitado_pero_responde_con_gracia() {
        final ConversacionBot bot = bot();

        assertThat(bot.habilitado()).isFalse();
        assertThat(bot.responder(1L, "hola")).isNotBlank();
    }

    @Test
    void despacha_buscar_paradas_con_la_consulta_tal_cual() {
        bot().ejecutarHerramienta("buscar_paradas",
                JsonValue.from(Map.of("consulta", "18 de julio y ejido")));

        verify(transporte).buscarParadas("18 de julio y ejido");
    }

    @Test
    void despacha_paradas_cercanas_sin_cantidad_cuando_no_viene() {
        bot().ejecutarHerramienta("paradas_cercanas",
                JsonValue.from(Map.of("latitud", -34.9, "longitud", -56.16)));

        verify(transporte).paradasCercanas(-34.9, -56.16, null);
    }

    @Test
    void despacha_proxima_salida_con_todos_los_parametros() {
        bot().ejecutarHerramienta("proxima_salida",
                JsonValue.from(Map.of("codigoParada", 3977, "linea", "185", "cantidad", 5)));

        verify(transporte).proximaSalida(3977L, "185", 5);
    }

    @Test
    void una_herramienta_desconocida_es_un_error_claro() {
        assertThatThrownBy(() -> bot().ejecutarHerramienta("hackear_nasa", JsonValue.from(Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hackear_nasa");
    }
}
