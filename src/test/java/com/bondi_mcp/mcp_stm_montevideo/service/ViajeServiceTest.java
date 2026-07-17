package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ViajeServiceTest {

    private static final Coordenada ORIGEN = new Coordenada(-34.9110, -56.1520);
    private static final Coordenada DESTINO = new Coordenada(-34.9057, -56.1874);

    private static final Parada SUBIDA = new Parada(3977L, "GABRIEL A PEREIRA", "PEDRO F BERRO", ORIGEN);
    private static final Parada BAJADA = new Parada(4016L, "AV 18 DE JULIO", "YAGUARON", DESTINO);

    @Mock
    private RecorridoRepository recorridoRepository;

    @Mock
    private ParadaRepository paradaRepository;

    @Mock
    private ParadaService paradaService;

    @InjectMocks
    private ViajeService service;

    private void dadasParadasEnAmbasPuntas() {
        given(paradaService.cercanasA(eq(ORIGEN), anyInt()))
                .willReturn(List.of(new ParadaCercana(SUBIDA, 20)));
        given(paradaService.cercanasA(eq(DESTINO), anyInt()))
                .willReturn(List.of(new ParadaCercana(BAJADA, 33)));
        given(paradaRepository.findAllById(any()))
                .willReturn(List.of(entidad(SUBIDA), entidad(BAJADA)));
    }

    private static ParadaEntity entidad(Parada parada) {
        return ParadaEntity.desde(parada, "x", java.time.Instant.now());
    }

    private static Coordenada eq(Coordenada c) {
        return org.mockito.ArgumentMatchers.eq(c);
    }

    @Test
    void devuelve_el_viaje_directo_con_la_caminata_de_las_dos_puntas() {
        dadasParadasEnAmbasPuntas();
        given(recorridoRepository.viajesDirectos(anyList(), anyList()))
                .willReturn(List.of(tramoDirecto("62", 3977L, 4016L)));

        final List<Viaje> viajes = service.comoLlegar(ORIGEN, DESTINO);

        assertThat(viajes).hasSize(1);
        assertThat(viajes.getFirst().esDirecto()).isTrue();
        assertThat(viajes.getFirst().lineas()).containsExactly("62");
        // 20 m hasta la parada de subida + 33 m desde la de bajada.
        assertThat(viajes.getFirst().metrosCaminando()).isEqualTo(53);
    }

    @Test
    void no_busca_transbordos_si_ya_hay_un_directo() {
        // Un directo siempre le gana a uno con cambio: no tiene sentido pagar la consulta cara.
        dadasParadasEnAmbasPuntas();
        given(recorridoRepository.viajesDirectos(anyList(), anyList()))
                .willReturn(List.of(tramoDirecto("62", 3977L, 4016L)));

        service.comoLlegar(ORIGEN, DESTINO);

        verify(recorridoRepository, never())
                .viajesConTransbordo(anyList(), anyList(), anyDouble(), anyDouble(), any());
    }

    @Test
    void recurre_al_transbordo_solo_cuando_no_hay_directo() {
        dadasParadasEnAmbasPuntas();
        given(recorridoRepository.viajesDirectos(anyList(), anyList())).willReturn(List.of());
        given(recorridoRepository.viajesConTransbordo(anyList(), anyList(), anyDouble(), anyDouble(), any()))
                .willReturn(List.of());

        assertThat(service.comoLlegar(ORIGEN, DESTINO)).isEmpty();

        verify(recorridoRepository).viajesConTransbordo(anyList(), anyList(), anyDouble(), anyDouble(), any());
    }

    @Test
    void sin_paradas_cerca_de_alguna_punta_no_hay_viaje() {
        // Sin dónde subir no hay nada que buscar; no se consulta la base de recorridos.
        given(paradaService.cercanasA(eq(ORIGEN), anyInt())).willReturn(List.of());
        given(paradaService.cercanasA(eq(DESTINO), anyInt()))
                .willReturn(List.of(new ParadaCercana(BAJADA, 33)));

        assertThat(service.comoLlegar(ORIGEN, DESTINO)).isEmpty();

        verify(recorridoRepository, never()).viajesDirectos(anyList(), anyList());
    }

    @Test
    void descarta_las_paradas_demasiado_lejos_de_la_punta() {
        // A 900 m no es "estar ahí": si se aceptaran, cualquier viaje empezaría con una caminata
        // que el usuario no pidió.
        given(paradaService.cercanasA(eq(ORIGEN), anyInt()))
                .willReturn(List.of(new ParadaCercana(SUBIDA, 900)));
        given(paradaService.cercanasA(eq(DESTINO), anyInt()))
                .willReturn(List.of(new ParadaCercana(BAJADA, 33)));

        assertThat(service.comoLlegar(ORIGEN, DESTINO)).isEmpty();

        verify(recorridoRepository, never()).viajesDirectos(anyList(), anyList());
    }

    private static RecorridoRepository.TramoDirecto tramoDirecto(String linea, long subida, long bajada) {
        return new RecorridoRepository.TramoDirecto() {

            @Override
            public String getLinea() {
                return linea;
            }

            @Override
            public long getParadaSubida() {
                return subida;
            }

            @Override
            public long getParadaBajada() {
                return bajada;
            }
        };
    }
}
