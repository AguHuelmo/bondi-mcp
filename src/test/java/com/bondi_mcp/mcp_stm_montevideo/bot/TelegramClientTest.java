package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El corte de mensajes largos: Telegram rechaza los de más de 4096 caracteres. */
class TelegramClientTest {

    @Test
    void un_mensaje_corto_va_entero() {
        assertThat(TelegramClient.partir("hola")).containsExactly("hola");
    }

    @Test
    void un_mensaje_largo_se_parte_y_no_pierde_contenido() {
        final String renglon = "La 185 sale 07:30 desde Gabriel Pereira y Berro\n";
        final String largo = renglon.repeat(200); // ~9.600 caracteres

        final List<String> partes = TelegramClient.partir(largo);

        assertThat(partes.size()).isGreaterThan(1);
        assertThat(partes).allSatisfy(parte -> assertThat(parte.length()).isLessThanOrEqualTo(4000));
        // Se parte por renglón: ningún pedazo corta una frase al medio.
        assertThat(partes).allSatisfy(parte -> assertThat(parte).endsWith("Berro"));
    }

    @Test
    void un_texto_sin_saltos_se_parte_igual() {
        final String sinSaltos = "a".repeat(9000);

        final List<String> partes = TelegramClient.partir(sinSaltos);

        assertThat(String.join("", partes)).isEqualTo(sinSaltos);
        assertThat(partes).allSatisfy(parte -> assertThat(parte.length()).isLessThanOrEqualTo(4000));
    }
}
