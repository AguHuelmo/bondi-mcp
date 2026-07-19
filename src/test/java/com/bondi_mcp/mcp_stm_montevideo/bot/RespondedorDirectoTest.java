package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.time.Duration;
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
import com.bondi_mcp.mcp_stm_montevideo.domain.RecorridoDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.BusEnVivoService;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.service.RecorridoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * El modo comandos del bot: sin LLM, cada forma de mensaje va al servicio correcto y la
 * respuesta es texto plano listo para Telegram.
 */
@ExtendWith(MockitoExtension.class)
class RespondedorDirectoTest {

    private static final Charla CHARLA = Charla.telegram(7);

    private static final Parada UNA_PARADA =
            new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO", new Coordenada(-34.91, -56.15));

    @Mock
    private ParadaService paradaService;

    @Mock
    private ArriboService arriboService;

    @Mock
    private ViajeService viajeService;

    @Mock
    private RecorridoService recorridoService;

    @Mock
    private BusEnVivoService busEnVivoService;

    @Mock
    private HorarioTeoricoService horarioTeoricoService;

    @Mock
    private GuardiaDeArribos guardiaDeArribos;

    @InjectMocks
    private RespondedorDirecto respondedor;

    @Test
    void el_saludo_y_los_comandos_de_ayuda_explican_como_usarlo() {
        assertThat(respondedor.responder(CHARLA, "/start")).contains("3977", ">", "ubicación");
        assertThat(respondedor.responder(CHARLA, "hola")).contains("3977");
    }

    @Test
    void un_numero_de_parada_devuelve_los_arribos() {
        given(paradaService.porCodigo(3977L)).willReturn(Optional.of(UNA_PARADA));
        given(arriboService.proximosArribos(3977L)).willReturn(new ArribosDeParada(3977L,
                List.of(new Arribo("405", "PLAZA ESPAÑA", Duration.ofMinutes(4), 800, "CUTCSA", null)),
                List.of("142", "405")));

        final String respuesta = respondedor.responder(CHARLA, "3977");

        assertThat(respuesta)
                .contains("GABRIEL A PEREIRA y PEDRO F BERRO")
                .contains("405")
                .contains("4 min")
                .contains("142, 405");
    }

    @Test
    void si_el_tiempo_real_se_cae_igual_informa_las_lineas_de_la_parada() {
        given(paradaService.porCodigo(3977L)).willReturn(Optional.of(UNA_PARADA));
        given(arriboService.proximosArribos(3977L))
                .willThrow(new TransportePublicoException("API caída"));
        given(arriboService.lineasQuePasan(3977L)).willReturn(List.of("142", "405"));

        final String respuesta = respondedor.responder(CHARLA, "3977");

        assertThat(respuesta).contains("no responde").contains("142, 405");
    }

    @Test
    void un_numero_que_no_es_parada_pero_si_linea_devuelve_la_linea() {
        given(paradaService.porCodigo(185L)).willReturn(Optional.empty());
        given(recorridoService.recorridoDe("185")).willReturn(new RecorridoDeLinea("185",
                List.of(new RecorridoDeLinea.Sentido("PORTONES", List.of(UNA_PARADA)))));
        given(busEnVivoService.deLinea("185")).willReturn(List.of());

        final String respuesta = respondedor.responder(CHARLA, "185");

        assertThat(respuesta).contains("Línea 185").contains("PORTONES");
    }

    @Test
    void parada_y_linea_devuelven_las_proximas_salidas_teoricas() {
        given(horarioTeoricoService.proximasSalidas(3977L, "185", 3)).willReturn(List.of());

        final String respuesta = respondedor.responder(CHARLA, "3977 185");

        assertThat(respuesta).contains("No tengo horarios de la 185");
    }

    @Test
    void origen_mayor_destino_planifica_el_viaje() {
        given(viajeService.ubicar("estadio centenario")).willReturn(Optional.of(new Coordenada(-34.89, -56.15)));
        given(viajeService.ubicar("pocitos")).willReturn(Optional.empty());

        final String respuesta = respondedor.responder(CHARLA, "estadio centenario > pocitos");

        assertThat(respuesta).contains("No pude ubicar \"pocitos\"");
    }

    @Test
    void avisame_crea_una_alerta_con_el_umbral_pedido() {
        given(guardiaDeArribos.crear(CHARLA, 3977L, "185", 10)).willReturn("creada");

        assertThat(respondedor.responder(CHARLA, "avisame 3977 185 10")).isEqualTo("creada");
    }

    @Test
    void avisame_sin_minutos_usa_el_umbral_por_defecto() {
        given(guardiaDeArribos.crear(CHARLA, 3977L, "ce1", null)).willReturn("creada");

        assertThat(respondedor.responder(CHARLA, "Avísame 3977 ce1")).isEqualTo("creada");
    }

    @Test
    void alertas_y_cancelar_van_a_la_guardia_del_chat_correcto() {
        given(guardiaDeArribos.listar(CHARLA)).willReturn("tus alertas");
        given(guardiaDeArribos.cancelar(CHARLA)).willReturn("cortadas");

        assertThat(respondedor.responder(CHARLA, "alertas")).isEqualTo("tus alertas");
        assertThat(respondedor.responder(CHARLA, "cancelar")).isEqualTo("cortadas");
    }

    @Test
    void una_caida_de_la_intendencia_no_tira_excepcion_al_chat() {
        given(paradaService.buscar("18 de julio y ejido"))
                .willThrow(new TransportePublicoException("API caída"));

        final String respuesta = respondedor.responder(CHARLA, "18 de julio y ejido");

        assertThat(respuesta).contains("Intendencia");
    }
}
