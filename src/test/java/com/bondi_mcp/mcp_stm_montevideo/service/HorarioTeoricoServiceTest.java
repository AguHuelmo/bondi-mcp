package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * La parte difícil de "cuándo sale la próxima": la trasnoche pertenece al día de servicio
 * anterior, y de madrugada conviven salidas de dos días de servicio distintos.
 *
 * <p>Fechas fijas de enero de 2026: el lunes es 5, el sábado 10 y el domingo 11.
 */
@ExtendWith(MockitoExtension.class)
class HorarioTeoricoServiceTest {

    private static final long PARADA = 3977L;

    @Mock
    private HorarioTeoricoDao dao;

    @InjectMocks
    private HorarioTeoricoService service;

    @Test
    void la_trasnoche_del_sabado_aparece_como_madrugada_del_domingo() {
        // "24:30" del sábado: el GTFS la escribe como minuto 1470 del día de servicio sábado.
        given(dao.minutosPorDia(PARADA, "185")).willReturn(Map.of(TipoDia.SABADO, List.of(1470)));

        final List<SalidaTeorica> salidas = service.proximasSalidas(
                PARADA, "185", 1, LocalDateTime.of(2026, 1, 11, 0, 0));

        assertThat(salidas).hasSize(1);
        assertThat(salidas.getFirst().momento()).isEqualTo(LocalDateTime.of(2026, 1, 11, 0, 30));
        assertThat(salidas.getFirst().tipoDia()).isEqualTo(TipoDia.SABADO);
    }

    @Test
    void de_madrugada_intercala_la_trasnoche_de_ayer_con_las_salidas_de_hoy() {
        given(dao.minutosPorDia(PARADA, "185")).willReturn(Map.of(
                TipoDia.SABADO, List.of(1470),          // 00:30 del domingo, servicio del sábado
                TipoDia.DOMINGO, List.of(20, 600)));    // 00:20 y 10:00 del domingo

        final List<SalidaTeorica> salidas = service.proximasSalidas(
                PARADA, "185", 3, LocalDateTime.of(2026, 1, 11, 0, 10));

        assertThat(salidas).extracting(SalidaTeorica::momento).containsExactly(
                LocalDateTime.of(2026, 1, 11, 0, 20),
                LocalDateTime.of(2026, 1, 11, 0, 30),
                LocalDateTime.of(2026, 1, 11, 10, 0));
        assertThat(salidas).extracting(SalidaTeorica::tipoDia)
                .containsExactly(TipoDia.DOMINGO, TipoDia.SABADO, TipoDia.DOMINGO);
    }

    @Test
    void si_hoy_no_hay_mas_salidas_salta_al_proximo_dia_con_servicio() {
        // Una línea que corre solo los días hábiles, a las 08:00.
        given(dao.minutosPorDia(PARADA, "185")).willReturn(Map.of(TipoDia.HABIL, List.of(480)));

        final List<SalidaTeorica> salidas = service.proximasSalidas(
                PARADA, "185", 1, LocalDateTime.of(2026, 1, 10, 9, 0)); // sábado a las 09:00

        assertThat(salidas).hasSize(1);
        assertThat(salidas.getFirst().momento()).isEqualTo(LocalDateTime.of(2026, 1, 12, 8, 0));
        assertThat(salidas.getFirst().tipoDia()).isEqualTo(TipoDia.HABIL);
    }

    @Test
    void normaliza_la_linea_como_esta_en_la_base() {
        given(dao.minutosPorDia(PARADA, "CE1")).willReturn(Map.of(TipoDia.HABIL, List.of(480)));

        final List<SalidaTeorica> salidas = service.proximasSalidas(
                PARADA, " ce1 ", 1, LocalDateTime.of(2026, 1, 5, 7, 0)); // lunes

        assertThat(salidas).hasSize(1);
    }

    @Test
    void sin_horarios_devuelve_vacio() {
        given(dao.minutosPorDia(PARADA, "999")).willReturn(Map.of());

        assertThat(service.proximasSalidas(PARADA, "999", 3,
                LocalDateTime.of(2026, 1, 5, 7, 0))).isEmpty();
    }
}
