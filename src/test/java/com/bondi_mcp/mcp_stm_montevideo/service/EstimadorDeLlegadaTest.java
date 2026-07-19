package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
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
import com.bondi_mcp.mcp_stm_montevideo.domain.PronosticoDeLlegada;
import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * El pronóstico de "¿llego?": el detalle que importa es que solo cuenta el bondi ALCANZABLE
 * (espera ≥ caminata), y que sin tiempo real cae a los horarios programados avisándolo.
 */
@ExtendWith(MockitoExtension.class)
class EstimadorDeLlegadaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 7, 20, 17, 0);
    private static final Coordenada ORIGEN = new Coordenada(-34.9100, -56.1500);
    private static final Coordenada DESTINO = new Coordenada(-34.9000, -56.1700);

    // A ~300 m del origen: la caminata inicial da 4-5 minutos.
    private static final Parada SUBIDA =
            new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO", new Coordenada(-34.9127, -56.1500));
    private static final Parada BAJADA =
            new Parada(1234L, "AV BRASIL", "BENITO BLANCO", new Coordenada(-34.9005, -56.1705));

    @Mock
    private ViajeService viajeService;

    @Mock
    private ArriboService arriboService;

    @Mock
    private HorarioTeoricoService horarioTeoricoService;

    @Mock
    private RecorridoService recorridoService;

    @InjectMocks
    private EstimadorDeLlegada estimador;

    private static Viaje directo() {
        return new Viaje(List.of(new Viaje.Tramo("185", SUBIDA, BAJADA)), 400);
    }

    private static Arribo arriboEn(long minutos) {
        return new Arribo("185", "PORTONES", Duration.ofMinutes(minutos), 500, "CUTCSA", null);
    }

    /** Un tramo de 11 paradas: diez saltos de ~1,4 min → 14 minutos arriba del bondi. */
    private void tramoDeOnceParadas() {
        given(recorridoService.tramoDe("185", 3977L, 1234L))
                .willReturn(Collections.nCopies(11, SUBIDA));
    }

    @Test
    void descarta_el_bondi_que_pasa_antes_de_llegar_caminando_a_la_parada() {
        given(viajeService.comoLlegar(ORIGEN, DESTINO)).willReturn(List.of(directo()));
        // El de 2 min pasa mientras todavía se camina; el alcanzable es el de 12.
        given(arriboService.proximosArribos(3977L)).willReturn(new ArribosDeParada(3977L,
                List.of(arriboEn(2), arriboEn(12)), List.of("185")));
        tramoDeOnceParadas();

        final PronosticoDeLlegada pronostico = estimador
                .pronosticar(ORIGEN, DESTINO, LocalTime.of(18, 0), AHORA).orElseThrow();

        assertThat(pronostico.esperaMinutos()).isEqualTo(12);
        assertThat(pronostico.esperaEnTiempoReal()).isTrue();
        assertThat(pronostico.viajeMinutos()).isEqualTo(14);
        // La llegada y el margen tienen que cerrar entre sí y contra la hora objetivo.
        assertThat(pronostico.llegadaEstimada()).isEqualTo(
                AHORA.plusMinutes(12L + 14 + pronostico.caminataFinalMinutos()));
        assertThat(pronostico.margenMinutos()).isEqualTo((int) Duration
                .between(pronostico.llegadaEstimada(), AHORA.toLocalDate().atTime(18, 0)).toMinutes());
        assertThat(pronostico.llega()).isTrue();
    }

    @Test
    void si_no_da_el_tiempo_el_veredicto_es_no_sin_vueltas() {
        given(viajeService.comoLlegar(ORIGEN, DESTINO)).willReturn(List.of(directo()));
        given(arriboService.proximosArribos(3977L)).willReturn(new ArribosDeParada(3977L,
                List.of(arriboEn(12)), List.of("185")));
        tramoDeOnceParadas();

        final PronosticoDeLlegada pronostico = estimador
                .pronosticar(ORIGEN, DESTINO, LocalTime.of(17, 10), AHORA).orElseThrow();

        assertThat(pronostico.llega()).isFalse();
        assertThat(pronostico.margenMinutos()).isNegative();
    }

    @Test
    void sin_tiempo_real_cae_a_los_horarios_programados_y_lo_dice() {
        given(viajeService.comoLlegar(ORIGEN, DESTINO)).willReturn(List.of(directo()));
        given(arriboService.proximosArribos(3977L))
                .willThrow(new TransportePublicoException("API caída"));
        // La salida de 17:02 pasa mientras se camina; la alcanzable es la de 17:25.
        given(horarioTeoricoService.proximasSalidas(3977L, "185", 5)).willReturn(List.of(
                new SalidaTeorica(AHORA.plusMinutes(2), TipoDia.HABIL),
                new SalidaTeorica(AHORA.plusMinutes(25), TipoDia.HABIL)));
        tramoDeOnceParadas();

        final PronosticoDeLlegada pronostico = estimador
                .pronosticar(ORIGEN, DESTINO, LocalTime.of(18, 30), AHORA).orElseThrow();

        assertThat(pronostico.esperaMinutos()).isEqualTo(25);
        assertThat(pronostico.esperaEnTiempoReal()).isFalse();
    }

    @Test
    void sin_salida_en_lo_que_queda_del_dia_no_promete_nada() {
        given(viajeService.comoLlegar(ORIGEN, DESTINO)).willReturn(List.of(directo()));
        given(arriboService.proximosArribos(3977L)).willReturn(new ArribosDeParada(3977L,
                List.of(), List.of("185")));
        // La única salida que queda es de mañana: prometer con eso no le sirve a nadie.
        given(horarioTeoricoService.proximasSalidas(anyLong(), anyString(), anyInt()))
                .willReturn(List.of(new SalidaTeorica(AHORA.plusDays(1).withHour(6), TipoDia.HABIL)));

        assertThat(estimador.pronosticar(ORIGEN, DESTINO, LocalTime.of(23, 0), AHORA)).isEmpty();
    }

    @Test
    void con_transbordo_no_se_anima_a_prometer_hora() {
        final Viaje conTransbordo = new Viaje(List.of(
                new Viaje.Tramo("185", SUBIDA, BAJADA),
                new Viaje.Tramo("405", BAJADA, SUBIDA)), 600);
        given(viajeService.comoLlegar(any(), any())).willReturn(List.of(conTransbordo));

        assertThat(estimador.pronosticar(ORIGEN, DESTINO, LocalTime.of(18, 0), AHORA)).isEmpty();
    }
}
