package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** La puerta de salida única: rutea al canal correcto y corta los mensajes largos. */
@ExtendWith(MockitoExtension.class)
class MensajeroTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private WhatsAppClient whatsAppClient;

    @InjectMocks
    private Mensajero mensajero;

    @Test
    void una_charla_de_telegram_sale_por_telegram() {
        mensajero.enviar(Charla.telegram(7), "hola");

        verify(telegramClient).enviarMensaje(7L, "hola");
        verify(whatsAppClient, never()).enviarMensaje(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void una_charla_de_whatsapp_sale_por_whatsapp() {
        mensajero.enviar(Charla.whatsapp("59891234567"), "hola");

        verify(whatsAppClient).enviarMensaje("59891234567", "hola");
        verify(telegramClient, never()).enviarMensaje(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void un_mensaje_corto_va_entero() {
        assertThat(Mensajero.partir("hola")).containsExactly("hola");
    }

    @Test
    void un_mensaje_largo_se_parte_por_renglon_y_no_pierde_contenido() {
        final String renglon = "La 185 sale 07:30 desde Gabriel Pereira y Berro\n";
        final String largo = renglon.repeat(200); // ~9.600 caracteres

        final List<String> partes = Mensajero.partir(largo);

        assertThat(partes.size()).isGreaterThan(1);
        assertThat(partes).allSatisfy(parte -> assertThat(parte.length()).isLessThanOrEqualTo(4000));
        assertThat(partes).allSatisfy(parte -> assertThat(parte).endsWith("Berro"));
    }

    @Test
    void un_texto_sin_saltos_se_parte_igual() {
        final String sinSaltos = "a".repeat(9000);

        final List<String> partes = Mensajero.partir(sinSaltos);

        assertThat(String.join("", partes)).isEqualTo(sinSaltos);
        assertThat(partes).allSatisfy(parte -> assertThat(parte.length()).isLessThanOrEqualTo(4000));
    }
}
