package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** La guardia de arribos: avisa una sola vez, sobrevive caídas de la API y vence a los 45 min. */
@ExtendWith(MockitoExtension.class)
class GuardiaDeArribosTest {

    private static final long CHAT = 7L;
    private static final Parada UNA_PARADA =
            new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO", new Coordenada(-34.91, -56.15));

    @Mock
    private ParadaService paradaService;

    @Mock
    private ArriboService arriboService;

    @Mock
    private TelegramClient telegramClient;

    @InjectMocks
    private GuardiaDeArribos guardia;

    private static ArribosDeParada arribosCon(long minutos) {
        return new ArribosDeParada(3977L,
                List.of(new Arribo("185", "PORTONES", Duration.ofMinutes(minutos), 900, "CUTCSA", null)),
                List.of("185", "405"));
    }

    private static ArribosDeParada sinArribos() {
        return new ArribosDeParada(3977L, List.of(), List.of("185", "405"));
    }

    private void alertaCreada() {
        given(paradaService.porCodigo(3977L)).willReturn(Optional.of(UNA_PARADA));
        given(arriboService.lineasQuePasan(3977L)).willReturn(List.of("185", "405"));
        given(arriboService.proximosArribos(3977L)).willReturn(sinArribos());
        guardia.crear(CHAT, 3977L, "185", 5);
    }

    @Test
    void valida_que_la_parada_exista() {
        given(paradaService.porCodigo(99999L)).willReturn(Optional.empty());

        assertThat(guardia.crear(CHAT, 99999L, "185", null)).contains("No encontré la parada");
    }

    @Test
    void rechaza_una_linea_que_no_pasa_por_la_parada() {
        given(paradaService.porCodigo(3977L)).willReturn(Optional.of(UNA_PARADA));
        given(arriboService.lineasQuePasan(3977L)).willReturn(List.of("142", "405"));

        assertThat(guardia.crear(CHAT, 3977L, "185", null))
                .contains("no pasa")
                .contains("142, 405");
    }

    @Test
    void si_el_bondi_ya_esta_cerca_avisa_al_toque_y_no_crea_alerta() {
        given(paradaService.porCodigo(3977L)).willReturn(Optional.of(UNA_PARADA));
        given(arriboService.lineasQuePasan(3977L)).willReturn(List.of("185"));
        given(arriboService.proximosArribos(3977L)).willReturn(arribosCon(3));

        final String respuesta = guardia.crear(CHAT, 3977L, "185", 5);

        assertThat(respuesta).contains("Ahí viene");
        assertThat(guardia.listar(CHAT)).contains("No tenés alertas");
    }

    @Test
    void avisa_una_sola_vez_cuando_el_bondi_entra_en_el_umbral() {
        alertaCreada();
        given(arriboService.proximosArribos(3977L)).willReturn(arribosCon(4));

        guardia.revisar(Instant.now());
        guardia.revisar(Instant.now());

        verify(telegramClient, times(1)).enviarMensaje(eq(CHAT), contains("¡Ahí viene!"));
        assertThat(guardia.listar(CHAT)).contains("No tenés alertas");
    }

    @Test
    void no_avisa_mientras_el_bondi_este_lejos() {
        alertaCreada();
        given(arriboService.proximosArribos(3977L)).willReturn(arribosCon(12));

        guardia.revisar(Instant.now());

        verify(telegramClient, never()).enviarMensaje(anyLong(), contains("Ahí viene"));
        assertThat(guardia.listar(CHAT)).contains("185");
    }

    @Test
    void una_caida_de_la_api_no_mata_la_alerta() {
        alertaCreada();
        given(arriboService.proximosArribos(3977L))
                .willThrow(new TransportePublicoException("API caída"))
                .willReturn(arribosCon(2));

        guardia.revisar(Instant.now()); // falla la consulta: la alerta sigue viva
        guardia.revisar(Instant.now()); // ahora sí

        verify(telegramClient, times(1)).enviarMensaje(eq(CHAT), contains("¡Ahí viene!"));
    }

    @Test
    void a_los_45_minutos_avisa_que_corto_la_guardia() {
        alertaCreada();

        guardia.revisar(Instant.now().plus(Duration.ofMinutes(46)));

        verify(telegramClient).enviarMensaje(eq(CHAT), contains("Corté la guardia"));
        assertThat(guardia.listar(CHAT)).contains("No tenés alertas");
    }

    @Test
    void pedir_la_misma_alerta_actualiza_en_vez_de_duplicar() {
        alertaCreada();
        guardia.crear(CHAT, 3977L, "185", 10);

        assertThat(guardia.listar(CHAT)).containsOnlyOnce("185");
    }

    @Test
    void cancelar_borra_todas_las_del_chat() {
        alertaCreada();

        assertThat(guardia.cancelar(CHAT)).contains("corté");
        assertThat(guardia.cancelar(CHAT)).contains("No tenías");
    }
}
