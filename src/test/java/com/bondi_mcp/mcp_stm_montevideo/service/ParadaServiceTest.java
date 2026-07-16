package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import com.bondi_mcp.mcp_stm_montevideo.config.ParadasProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParadaServiceTest {

    private static final Parada PARADA =
            new Parada(546L, "CORUÑA", "PURIFICACION", new Coordenada(-34.903778, -56.196167));

    @Mock
    private ParadaRepository repository;

    @Mock
    private ParadaCacheService cacheService;

    private ParadaService service() {
        return new ParadaService(repository, cacheService, new ParadasProperties(Duration.ofHours(24), 20));
    }

    private static ParadaEntity entidadDe(Parada parada) {
        return ParadaEntity.desde(parada, "546 CORUNA PURIFICACION", java.time.Instant.now());
    }

    @Test
    void busca_con_un_patron_por_palabra_normalizada() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of(entidadDe(PARADA)));

        final List<Parada> resultado = service().buscar("Coruña y Purificación");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().codigo()).isEqualTo(546L);

        final ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(repository).buscarPorPatrones(captor.capture(), any(Limit.class));
        // Tildes fuera, una palabra por patrón y sin el conector "y": matchean todas, en
        // cualquier orden.
        assertThat(captor.getValue()).containsExactly("%CORUNA%", "%PURIFICACION%");
    }

    @Test
    void el_conector_del_cruce_no_se_exige_como_termino() {
        // "18 DE JULIO Y EJIDO": ninguna de las dos calles contiene una Y, así que exigir "%Y%"
        // dejaba la búsqueda más natural del usuario sin un solo resultado.
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());

        service().buscar("18 de julio y ejido");

        final ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(repository).buscarPorPatrones(captor.capture(), any(Limit.class));
        assertThat(captor.getValue()).containsExactly("%18%", "%DE%", "%JULIO%", "%EJIDO%");
    }

    @Test
    void una_consulta_de_puros_conectores_no_devuelve_todas_las_paradas() {
        assertThat(service().buscar("y esquina con")).isEmpty();

        verify(repository, never()).buscarPorPatrones(any(), any());
    }

    @Test
    void refresca_el_cache_antes_de_buscar() {
        given(repository.buscarPorPatrones(any(), any())).willReturn(List.of());

        service().buscar("ejido");

        verify(cacheService).asegurarCacheFresco();
    }

    @Test
    void una_consulta_sin_palabras_no_toca_la_base() {
        assertThat(service().buscar("   ")).isEmpty();

        verify(cacheService, never()).asegurarCacheFresco();
        verify(repository, never()).buscarPorPatrones(any(), any());
    }

    @Test
    void busca_por_codigo_de_parada() {
        given(repository.findById(546L)).willReturn(Optional.of(entidadDe(PARADA)));

        assertThat(service().porCodigo(546L)).hasValueSatisfying(
                parada -> assertThat(parada.descripcion()).isEqualTo("CORUÑA y PURIFICACION"));
    }

    @Test
    void un_codigo_que_no_existe_da_vacio() {
        given(repository.findById(1L)).willReturn(Optional.empty());

        assertThat(service().porCodigo(1L)).isEmpty();
    }
}
