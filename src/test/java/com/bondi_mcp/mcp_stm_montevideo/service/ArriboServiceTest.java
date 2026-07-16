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

    @InjectMocks
    private ArriboService service;

    @Test
    void encadena_las_lineas_de_la_parada_antes_de_pedir_arribos() {
        // La API exige `lines`, así que el service tiene que averiguarlas primero.
        given(client.obtenerLineasDeParada(1234L)).willReturn(List.of("116", "183"));
        given(client.obtenerProximosArribos(eq(1234L), anyList(), anyInt()))
                .willReturn(List.of(new Arribo("116", "POCITOS", Duration.ofMinutes(3), 800, "CUTCSA")));

        final List<Arribo> arribos = service.proximosArribos(1234L);

        assertThat(arribos).hasSize(1);
        assertThat(arribos.getFirst().linea()).isEqualTo("116");
        verify(client).obtenerProximosArribos(1234L, List.of("116", "183"), 2);
    }

    @Test
    void no_llama_a_arribos_si_la_parada_no_tiene_lineas() {
        // Sin líneas el request daría 400 seguro: mejor ni hacerlo.
        given(client.obtenerLineasDeParada(999L)).willReturn(List.of());

        assertThat(service.proximosArribos(999L)).isEmpty();
        verify(client, never()).obtenerProximosArribos(anyLong(), anyList(), anyInt());
    }

    @Test
    void propaga_la_falla_externa_como_excepcion_de_dominio() {
        given(client.obtenerLineasDeParada(1234L))
                .willThrow(new TransportePublicoException("la API se cayó"));

        assertThatThrownBy(() -> service.proximosArribos(1234L))
                .isInstanceOf(TransportePublicoException.class);
    }

    @Test
    void permite_filtrar_por_lineas_puntuales_sin_consultar_las_de_la_parada() {
        given(client.obtenerProximosArribos(1234L, List.of("116"), 3))
                .willReturn(List.of(new Arribo("116", "POCITOS", Duration.ofMinutes(5), null, null)));

        final List<Arribo> arribos = service.proximosArribos(1234L, List.of("116"), 3);

        assertThat(arribos).hasSize(1);
        verify(client, never()).obtenerLineasDeParada(anyLong());
    }
}
