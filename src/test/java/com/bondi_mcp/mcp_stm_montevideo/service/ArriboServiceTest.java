package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaLineaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArriboServiceTest {

    @Mock
    private TransportePublicoClient client;

    @Mock
    private ParadaLineaRepository paradaLineaRepository;

    @InjectMocks
    private ArriboService service;

    @Test
    void encadena_las_lineas_en_circulacion_antes_de_pedir_arribos() {
        // La API exige `lines`, así que el service tiene que averiguarlas primero.
        given(client.obtenerLineasDeParada(1234L)).willReturn(List.of("116", "183"));
        given(client.obtenerProximosArribos(eq(1234L), anyList(), anyInt()))
                .willReturn(List.of(new Arribo("116", "POCITOS", Duration.ofMinutes(3), 800, "CUTCSA", null)));

        final ArribosDeParada resultado = service.proximosArribos(1234L);

        assertThat(resultado.arribos()).hasSize(1);
        assertThat(resultado.arribos().getFirst().linea()).isEqualTo("116");
        verify(client).obtenerProximosArribos(1234L, List.of("116", "183"), 2);
    }

    @Test
    void no_llama_a_arribos_si_no_hay_buses_circulando() {
        // Sin líneas el request daría 400 seguro: mejor ni hacerlo.
        given(client.obtenerLineasDeParada(999L)).willReturn(List.of());

        assertThat(service.proximosArribos(999L).arribos()).isEmpty();
        verify(client, never()).obtenerProximosArribos(anyLong(), anyList(), anyInt());
    }

    @Test
    void informa_las_lineas_del_gtfs_aunque_no_venga_ningun_bus() {
        // El caso de la madrugada: no hay arribos, pero saber qué líneas paran ahí es
        // justamente lo que uno necesita para decidir si esperar o caminar.
        given(client.obtenerLineasDeParada(3977L)).willReturn(List.of());
        given(paradaLineaRepository.lineasDeParada(3977L)).willReturn(List.of("149", "163", "316", "62"));

        final ArribosDeParada resultado = service.proximosArribos(3977L);

        assertThat(resultado.hayArribos()).isFalse();
        assertThat(resultado.lineasQuePasan()).containsExactly("149", "163", "316", "62");
    }

    @Test
    void las_lineas_que_pasan_no_dependen_de_los_buses_en_circulacion() {
        // El GTFS conoce las 4 líneas de la parada aunque ahora solo circule una.
        given(client.obtenerLineasDeParada(3977L)).willReturn(List.of("62"));
        given(client.obtenerProximosArribos(eq(3977L), anyList(), anyInt()))
                .willReturn(List.of(new Arribo("62", "CENTRO", Duration.ofMinutes(4), 900, "CUTCSA", null)));
        given(paradaLineaRepository.lineasDeParada(3977L)).willReturn(List.of("149", "163", "316", "62"));

        final ArribosDeParada resultado = service.proximosArribos(3977L);

        assertThat(resultado.arribos()).hasSize(1);
        assertThat(resultado.lineasQuePasan()).hasSize(4);
    }

    @Test
    void propaga_la_falla_externa_como_excepcion_de_dominio() {
        given(client.obtenerLineasDeParada(1234L))
                .willThrow(new TransportePublicoException("la API se cayó"));

        assertThatThrownBy(() -> service.proximosArribos(1234L))
                .isInstanceOf(TransportePublicoException.class);
    }
}
