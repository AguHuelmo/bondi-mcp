package com.bondi_mcp.mcp_stm_montevideo.mcp;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.domain.PuntoDeReferencia;
import com.bondi_mcp.mcp_stm_montevideo.domain.RecorridoDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;
import com.bondi_mcp.mcp_stm_montevideo.domain.Conectividad;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.BusEnVivoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ConectividadService;
import com.bondi_mcp.mcp_stm_montevideo.service.EstimadorDeLlegada;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.service.RecorridoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Las tools MCP nunca dejan escapar una falla de la API externa como excepción: la devuelven
 * como dato con contexto, para que el agente la explique en vez de cortar la conversación.
 */
@ExtendWith(MockitoExtension.class)
class TransporteMcpToolsTest {

    private static final Parada UNA_PARADA =
            new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO", new Coordenada(-34.91, -56.15));

    @Mock
    private ParadaService paradaService;

    @Mock
    private ArriboService arriboService;

    @Mock
    private ViajeService viajeService;

    @Mock
    private HorarioTeoricoService horarioTeoricoService;

    @Mock
    private RecorridoService recorridoService;

    @Mock
    private BusEnVivoService busEnVivoService;

    @Mock
    private ConectividadService conectividadService;

    @Mock
    private EstimadorDeLlegada estimadorDeLlegada;

    @InjectMocks
    private TransporteMcpTools tools;

    @Test
    void buscar_paradas_devuelve_contexto_y_no_explota_si_la_api_esta_caida() {
        given(paradaService.buscar(anyString()))
                .willThrow(new TransportePublicoException("API caída"));

        final var resultado = tools.buscarParadas("18 de julio y ejido");

        assertThat(resultado.paradas()).isEmpty();
        assertThat(resultado.contexto()).contains("falla temporal");
    }

    @Test
    void paradas_cercanas_devuelve_contexto_y_no_explota_si_la_api_esta_caida() {
        given(paradaService.cercanasA(new Coordenada(-34.9, -56.16), 5))
                .willThrow(new TransportePublicoException("API caída"));

        final var resultado = tools.paradasCercanas(-34.9, -56.16, null);

        assertThat(resultado.paradas()).isEmpty();
        assertThat(resultado.contexto()).contains("falla temporal");
    }

    @Test
    void como_llego_devuelve_contexto_y_no_explota_si_la_api_esta_caida() {
        given(viajeService.ubicar(anyString()))
                .willThrow(new TransportePublicoException("API caída"));

        final var resultado = tools.comoLlego("gabriel pereira y berro", "18 de julio y ejido");

        assertThat(resultado.opciones()).isEmpty();
        assertThat(resultado.contexto()).contains("falla temporal");
    }

    @Test
    void consultar_arribos_informa_las_lineas_aunque_el_cache_de_paradas_no_cargue() {
        // El peor caso: primer arranque con la API caída. Ni la descripción de la parada ni los
        // arribos se pueden resolver, pero las líneas que pasan salen de nuestra base.
        given(paradaService.porCodigo(3977L))
                .willThrow(new TransportePublicoException("API caída"));
        given(arriboService.proximosArribos(3977L))
                .willThrow(new TransportePublicoException("API caída"));
        given(arriboService.lineasQuePasan(3977L)).willReturn(List.of("142", "62"));

        final var respuesta = tools.consultarArribos(3977L);

        assertThat(respuesta.descripcion()).isEqualTo("Parada 3977");
        assertThat(respuesta.arribos()).isEmpty();
        assertThat(respuesta.lineasQuePasan()).containsExactly("142", "62");
        assertThat(respuesta.error()).isNotNull();
    }

    @Test
    void buscar_paradas_expone_el_punto_ubicado_de_un_cruce_estimado() {
        final var punto = PuntoDeReferencia.deCruceEstimado(new Coordenada(-34.905, -56.155));
        final var resultado = new ResultadoBusqueda(List.of(UNA_PARADA), List.of("GABRIEL", "PEREIRA"), 0)
                .withPunto(punto, List.of(new ParadaCercana(UNA_PARADA, 55)));
        given(paradaService.buscar("gabriel pereira y chucarro")).willReturn(resultado);

        final var respuesta = tools.buscarParadas("gabriel pereira y chucarro");

        assertThat(respuesta.puntoUbicado()).isNotNull();
        assertThat(respuesta.puntoUbicado().latitud()).isEqualTo(-34.905);
        assertThat(respuesta.puntoUbicado().tipo()).isEqualTo("CRUCE");
        assertThat(respuesta.cercanasAlPunto()).hasSize(1);
    }

    @Test
    void paradas_cercanas_acota_la_cantidad_pedida() {
        given(paradaService.cercanasA(new Coordenada(-34.9, -56.16), 20)).willReturn(List.of());

        tools.paradasCercanas(-34.9, -56.16, 999);

        verify(paradaService).cercanasA(new Coordenada(-34.9, -56.16), 20);
    }

    @Test
    void proxima_salida_resuelve_hora_fecha_y_espera() {
        final var momento = java.time.LocalDateTime.now(HorarioTeoricoService.ZONA_MONTEVIDEO)
                .plusMinutes(30);
        given(horarioTeoricoService.proximasSalidas(3977L, "185", 3))
                .willReturn(List.of(new SalidaTeorica(momento, TipoDia.HABIL)));

        final var respuesta = tools.proximaSalida(3977L, "185", null);

        assertThat(respuesta.salidas()).hasSize(1);
        final var salida = respuesta.salidas().getFirst();
        assertThat(salida.hora()).matches("\\d{2}:\\d{2}");
        assertThat(salida.fecha()).isEqualTo(momento.toLocalDate().toString());
        // Entre 29 y 30: el "ahora" de la tool corre unos milisegundos después del del test.
        assertThat(salida.enMinutos()).isBetween(29L, 30L);
        assertThat(salida.tipoDia()).isEqualTo("HABIL");
    }

    @Test
    void proxima_salida_sin_horarios_lo_dice_sin_inventar() {
        given(horarioTeoricoService.proximasSalidas(3977L, "999", 3)).willReturn(List.of());

        final var respuesta = tools.proximaSalida(3977L, "999", null);

        assertThat(respuesta.salidas()).isEmpty();
        assertThat(respuesta.contexto()).contains("No inventes horarios");
    }

    @Test
    void recorrido_desconocido_devuelve_contexto_y_no_error() {
        given(recorridoService.recorridoDe("999")).willReturn(new RecorridoDeLinea("999", List.of()));

        final var respuesta = tools.recorridoDeLinea("999");

        assertThat(respuesta.sentidos()).isEmpty();
        assertThat(respuesta.contexto()).contains("No conocemos el recorrido");
    }

    @Test
    void recorrido_conocido_lista_las_paradas_en_orden() {
        final var otra = new Parada(3179L, "AV 18 DE JULIO", "EJIDO", new Coordenada(-34.905, -56.187));
        given(recorridoService.recorridoDe("62")).willReturn(new RecorridoDeLinea("62",
                List.of(new RecorridoDeLinea.Sentido(otra.descripcion(), List.of(UNA_PARADA, otra)))));

        final var respuesta = tools.recorridoDeLinea("62");

        assertThat(respuesta.sentidos()).hasSize(1);
        assertThat(respuesta.sentidos().getFirst().cantidadParadas()).isEqualTo(2);
        assertThat(respuesta.sentidos().getFirst().paradas())
                .extracting(TransporteMcpTools.ParadaDeRecorrido::codigo)
                .containsExactly(3977L, 3179L);
    }

    @Test
    void conectividad_de_un_lugar_no_ubicable_lo_dice_en_el_contexto() {
        given(viajeService.ubicar("narnia 123")).willReturn(java.util.Optional.empty());

        final var respuesta = tools.conectividad("narnia 123");

        assertThat(respuesta.indice()).isNull();
        assertThat(respuesta.contexto()).contains("No se pudo ubicar");
    }

    @Test
    void conectividad_devuelve_el_indice_con_sus_componentes() {
        final var punto = new Coordenada(-34.91, -56.15);
        given(viajeService.ubicar("gabriel pereira 2470")).willReturn(java.util.Optional.of(punto));
        given(conectividadService.medir(punto)).willReturn(new Conectividad(
                75, "muy buena", 3, 80, List.of("185", "405"), 900, 8, 48, 2000L, 4900L));

        final var respuesta = tools.conectividad("gabriel pereira 2470");

        assertThat(respuesta.indice().puntaje()).isEqualTo(75);
        assertThat(respuesta.indice().nivel()).isEqualTo("muy buena");
        assertThat(respuesta.indice().porcentajeDeLaCiudadAlcanzable()).isEqualTo(41);
        assertThat(respuesta.contexto()).contains("cuatro componentes");
    }

    @Test
    void llego_a_tiempo_rechaza_una_hora_mal_formateada() {
        final var respuesta = tools.llegoATiempo("a", "b", "seis y media");

        assertThat(respuesta.veredicto()).isNull();
        assertThat(respuesta.contexto()).contains("HH:MM");
    }

    @Test
    void llego_a_tiempo_avisa_si_la_hora_ya_paso() {
        final var respuesta = tools.llegoATiempo("a", "b", "00:00");

        assertThat(respuesta.veredicto()).isNull();
        assertThat(respuesta.contexto()).contains("ya pasó");
    }

    @Test
    void buses_en_vivo_devuelve_contexto_y_no_explota_si_la_api_esta_caida() {
        given(busEnVivoService.deLinea("185"))
                .willThrow(new TransportePublicoException("API caída"));

        final var respuesta = tools.busesEnVivo("185");

        assertThat(respuesta.buses()).isEmpty();
        assertThat(respuesta.error()).isNotNull();
        assertThat(respuesta.contexto()).contains("falla temporal");
    }
}
