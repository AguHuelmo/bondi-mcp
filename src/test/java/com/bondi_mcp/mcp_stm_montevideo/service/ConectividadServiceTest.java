package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.domain.Conectividad;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaLineaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * El índice de conectividad: los componentes del puntaje y las decisiones que lo hacen honesto
 * (dedupe de veredas, "no sé" cuando faltan horarios, "sin servicio" cuando no hay paradas).
 */
@ExtendWith(MockitoExtension.class)
class ConectividadServiceTest {

    private static final Coordenada PUNTO = new Coordenada(-34.91, -56.15);
    private static final Parada PARADA_A =
            new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO", new Coordenada(-34.910, -56.151));
    private static final Parada PARADA_B =
            new Parada(3979L, "GABRIEL A PEREIRA", "ELLAURI", new Coordenada(-34.911, -56.152));

    @Mock
    private ParadaService paradaService;

    @Mock
    private ParadaLineaRepository paradaLineaRepository;

    @Mock
    private HorarioTeoricoDao horarioTeoricoDao;

    @Mock
    private RecorridoRepository recorridoRepository;

    @Mock
    private ParadaRepository paradaRepository;

    @InjectMocks
    private ConectividadService service;

    @Test
    void un_punto_sin_paradas_caminables_es_sin_servicio() {
        given(paradaService.cercanasA(any(), anyInt()))
                .willReturn(List.of(new ParadaCercana(PARADA_A, 900)));

        final Conectividad conectividad = service.medir(PUNTO);

        assertThat(conectividad.puntaje()).isZero();
        assertThat(conectividad.nivel()).isEqualTo("sin servicio");
        assertThat(conectividad.sinParadasCerca()).isTrue();
    }

    @Test
    void una_esquina_bien_servida_suma_los_cuatro_componentes() {
        given(paradaService.cercanasA(any(), anyInt())).willReturn(List.of(
                new ParadaCercana(PARADA_A, 80), new ParadaCercana(PARADA_B, 150)));
        given(horarioTeoricoDao.frecuencias(List.of(3977L, 3979L))).willReturn(List.of(
                new HorarioTeoricoDao.Frecuencia(3977L, "185", 500, 60, 20),
                new HorarioTeoricoDao.Frecuencia(3977L, "405", 400, 50, 10),
                new HorarioTeoricoDao.Frecuencia(3979L, "185", 480, 55, 18)));
        given(recorridoRepository.paradasAlcanzablesDesde(List.of(3977L, 3979L))).willReturn(2000L);
        given(paradaRepository.count()).willReturn(4900L);

        final Conectividad conectividad = service.medir(PUNTO);

        // Cercanía 25 (80 m) + variedad 5 (2 líneas) + frecuencia 25 (espera ~8 min) + alcance 20.
        assertThat(conectividad.puntaje()).isEqualTo(75);
        assertThat(conectividad.nivel()).isEqualTo("muy buena");
        assertThat(conectividad.lineas()).containsExactly("185", "405");
        // La 185 para en las dos veredas: cuenta su mejor parada (500), no la suma (980).
        assertThat(conectividad.salidasSemanales()).isEqualTo(900);
        assertThat(conectividad.esperaMediaDiurnaMinutos()).isEqualTo(8);
        assertThat(conectividad.porcentajeAlcanzable()).isEqualTo(41);
    }

    @Test
    void sin_horarios_importados_responde_igual_y_no_inventa_frecuencia() {
        given(paradaService.cercanasA(any(), anyInt()))
                .willReturn(List.of(new ParadaCercana(PARADA_A, 120)));
        given(horarioTeoricoDao.frecuencias(anyList())).willReturn(List.of());
        given(paradaLineaRepository.lineasDeParada(3977L)).willReturn(List.of("185", "405"));
        given(recorridoRepository.paradasAlcanzablesDesde(anyList())).willReturn(1000L);
        given(paradaRepository.count()).willReturn(4900L);

        final Conectividad conectividad = service.medir(PUNTO);

        assertThat(conectividad.lineas()).containsExactly("185", "405");
        assertThat(conectividad.esperaMediaDiurnaMinutos()).isNull();
        assertThat(conectividad.salidasSemanales()).isZero();
        assertThat(conectividad.puntaje()).isGreaterThan(0);
    }
}
