package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.config.ParadasProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParadaCacheServiceTest {

    private static final Parada UNA_PARADA =
            new Parada(546L, "CORUÑA", "PURIFICACION", new Coordenada(-34.903778, -56.196167));

    @Mock
    private TransportePublicoClient client;

    @Mock
    private ParadaRepository repository;

    private ParadaCacheService service(Duration ttl) {
        return new ParadaCacheService(client, repository, new ParadasProperties(ttl, 20));
    }

    @Test
    void no_le_pega_a_la_api_si_el_cache_esta_fresco() {
        given(repository.ultimaActualizacion()).willReturn(Optional.of(Instant.now().minusSeconds(60)));

        service(Duration.ofHours(24)).asegurarCacheFresco();

        verify(client, never()).obtenerTodasLasParadas();
    }

    @Test
    void refresca_cuando_el_cache_vencio() {
        given(repository.ultimaActualizacion()).willReturn(Optional.of(Instant.now().minus(Duration.ofDays(2))));
        given(client.obtenerTodasLasParadas()).willReturn(List.of(UNA_PARADA));

        service(Duration.ofHours(24)).asegurarCacheFresco();

        verify(client).obtenerTodasLasParadas();
        verify(repository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void si_falla_el_refresco_pero_hay_datos_viejos_los_sigue_usando() {
        // Una parada no se muda: caché vencido es mejor que un error.
        given(repository.ultimaActualizacion()).willReturn(Optional.of(Instant.now().minus(Duration.ofDays(2))));
        given(client.obtenerTodasLasParadas()).willThrow(new TransportePublicoException("timeout"));
        given(repository.count()).willReturn(1500L);

        assertThatCode(() -> service(Duration.ofHours(24)).asegurarCacheFresco()).doesNotThrowAnyException();
    }

    @Test
    void si_falla_el_refresco_y_el_cache_esta_vacio_falla() {
        // Sin datos no hay nada que servir: mentir con una lista vacía sería peor.
        given(repository.ultimaActualizacion()).willReturn(Optional.empty());
        given(client.obtenerTodasLasParadas()).willThrow(new TransportePublicoException("timeout"));
        given(repository.count()).willReturn(0L);

        assertThatThrownBy(() -> service(Duration.ofHours(24)).asegurarCacheFresco())
                .isInstanceOf(TransportePublicoException.class);
    }

    @Test
    void una_respuesta_vacia_de_la_api_no_borra_el_cache() {
        // Montevideo no se quedó sin paradas; es un problema de la API.
        given(client.obtenerTodasLasParadas()).willReturn(List.of());

        service(Duration.ofHours(24)).refrescar();

        verify(repository, never()).deleteAllInBatch();
        verify(repository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
