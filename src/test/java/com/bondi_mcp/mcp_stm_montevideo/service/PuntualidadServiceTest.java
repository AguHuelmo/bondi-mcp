package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.HistorialDeEsperas;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ObservacionDeArriboDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El historial de esperas: registra solo lo creíble y resume sin inventar. */
@ExtendWith(MockitoExtension.class)
class PuntualidadServiceTest {

    @Mock
    private ObservacionDeArriboDao observacionDao;

    @Mock
    private HorarioTeoricoDao horarioTeoricoDao;

    @InjectMocks
    private PuntualidadService service;

    private static Arribo arribo(String linea, long minutos) {
        return new Arribo(linea, "DESTINO", Duration.ofMinutes(minutos), 500, "CUTCSA", null);
    }

    @Test
    void registra_las_observaciones_de_una_consulta() {
        service.registrar(3977L, List.of(arribo("185", 4), arribo("405", 12)));

        verify(observacionDao).insertar(any(Instant.class), eq(3977L), anyList());
    }

    @Test
    void descarta_la_basura_del_feed_y_no_inserta_si_no_queda_nada() {
        // Una espera negativa o de tres horas no es un dato: es un feed roto.
        service.registrar(3977L, List.of(arribo("185", -1), arribo("185", 400)));

        verify(observacionDao, never()).insertar(any(), anyLong(), anyList());
    }

    @Test
    void sin_observaciones_responde_vacio_sin_inventar() {
        given(observacionDao.resumir("185", null)).willReturn(Optional.empty());

        final HistorialDeEsperas historial = service.historialDe("185", null);

        assertThat(historial.sinDatos()).isTrue();
        assertThat(historial.esperaMedianaMinutos()).isNull();
    }

    @Test
    void resume_el_historial_y_lo_compara_contra_el_horario_programado() {
        given(observacionDao.resumir("185", 3977L)).willReturn(Optional.of(
                new ObservacionDeArriboDao.Resumen(250, 7.4, 6.0, 14.6,
                        Instant.parse("2026-07-01T12:00:00Z"), Instant.parse("2026-07-19T12:00:00Z"))));
        given(observacionDao.porFranja("185", 3977L)).willReturn(List.of(
                new ObservacionDeArriboDao.FranjaFila(1, 120, 5.2),
                new ObservacionDeArriboDao.FranjaFila(3, 80, 9.8)));
        // 90 salidas diurnas → una cada 10 min → espera teórica media de 5.
        given(horarioTeoricoDao.frecuencias(List.of(3977L))).willReturn(List.of(
                new HorarioTeoricoDao.Frecuencia(3977L, "185", 600, 90, 20)));

        final HistorialDeEsperas historial = service.historialDe(" 185 ", 3977L);

        assertThat(historial.observaciones()).isEqualTo(250);
        assertThat(historial.esperaMedianaMinutos()).isEqualTo(6);
        assertThat(historial.esperaP90Minutos()).isEqualTo(15);
        assertThat(historial.esperaTeoricaMinutos()).isEqualTo(5);
        assertThat(historial.porFranja()).extracting(HistorialDeEsperas.Franja::nombre)
                .containsExactly("mañana", "noche");
    }

    @Test
    void la_linea_se_normaliza_como_en_la_base() {
        given(observacionDao.resumir("CE1", null)).willReturn(Optional.empty());

        assertThat(service.historialDe("ce1", null).linea()).isEqualTo("CE1");
    }
}
