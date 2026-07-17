package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;

class GtfsParserTest {

    /**
     * Un GTFS mínimo pero completo: dos líneas, servicios de hábil y de sábado, y horarios con
     * los casos raros reales (trasnoche "24:10", salida vacía, viajes duplicados).
     */
    private static byte[] gtfsDePrueba() {
        return zip(Map.of(
                "routes.txt", """
                        route_id,route_short_name,route_long_name
                        R185,185,Manga - Portones
                        RCE1,Ce1,Centro Express
                        """,
                "calendar.txt", """
                        service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
                        HABIL,1,1,1,1,1,0,0,20250101,20251231
                        SABADO,0,0,0,0,0,1,0,20250101,20251231
                        """,
                "trips.txt", """
                        route_id,service_id,trip_id,direction_id
                        R185,HABIL,V1,0
                        R185,HABIL,V2,0
                        R185,SABADO,V3,0
                        RCE1,HABIL,V4,0
                        """,
                // V1 y V2 pisan el mismo 05:30 en la parada 1000: tiene que quedar UNA vez.
                // V1 sigue a la 2000 pasada la medianoche del día de servicio (24:10 = 00:10).
                // V4 no trae departure_time en la 1000: vale la arrival_time.
                "stop_times.txt", """
                        trip_id,arrival_time,departure_time,stop_id,stop_sequence
                        V1,05:30:00,05:30:00,1000,1
                        V1,24:10:00,24:10:00,2000,2
                        V2,05:30:00,05:30:00,1000,1
                        V2,06:15:00,06:15:00,2000,2
                        V3,09:00:00,09:00:00,1000,1
                        V3,09:40:00,09:40:00,2000,2
                        V4,07:45:00,,1000,1
                        """));
    }

    @Test
    void agrupaLosHorariosPorParadaLineaYTipoDeDia() {
        final DatosGtfs datos = GtfsParser.parsear(gtfsDePrueba());

        final Map<TipoDia, List<Integer>> parada1000linea185 = minutosDe(datos, 1000, "185");
        // 05:30 una sola vez aunque V1 y V2 lo repitan.
        assertThat(parada1000linea185.get(TipoDia.HABIL)).containsExactly(5 * 60 + 30);
        assertThat(parada1000linea185.get(TipoDia.SABADO)).containsExactly(9 * 60);
        assertThat(parada1000linea185).doesNotContainKey(TipoDia.DOMINGO);
    }

    @Test
    void laTrasnocheQuedaComoMinutosMayoresAUnDia() {
        final DatosGtfs datos = GtfsParser.parsear(gtfsDePrueba());

        // El 24:10 NO es 00:10 del mismo día: ordena después del 06:15.
        assertThat(minutosDe(datos, 2000, "185").get(TipoDia.HABIL))
                .containsExactly(6 * 60 + 15, 24 * 60 + 10);
    }

    @Test
    void usaLaLlegadaCuandoFaltaLaSalidaYNormalizaLaLineaAMayusculas() {
        final DatosGtfs datos = GtfsParser.parsear(gtfsDePrueba());

        assertThat(minutosDe(datos, 1000, "CE1").get(TipoDia.HABIL)).containsExactly(7 * 60 + 45);
    }

    @Test
    void losRecorridosYLasLineasPorParadaSiguenSaliendo() {
        final DatosGtfs datos = GtfsParser.parsear(gtfsDePrueba());

        assertThat(datos.lineasPorParada().get(1000L)).containsExactlyInAnyOrder("185", "CE1");
        // V1, V2 y V3 comparten la secuencia 1000→2000: un solo recorrido para la 185.
        assertThat(datos.recorridos()).filteredOn(r -> r.linea().equals("185")).hasSize(1);
    }

    private static Map<TipoDia, List<Integer>> minutosDe(DatosGtfs datos, long parada, String linea) {
        return datos.horarios().stream()
                .filter(h -> h.parada() == parada && h.linea().equals(linea))
                .collect(java.util.stream.Collectors.toMap(HorarioTeoricoGtfs::tipoDia,
                        HorarioTeoricoGtfs::minutos));
    }

    private static byte[] zip(Map<String, String> archivos) {
        final ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(salida)) {
            // routes antes que trips, como en el zip real: el parser cuenta con ese orden.
            for (final String nombre : List.of("routes.txt", "calendar.txt", "trips.txt", "stop_times.txt")) {
                zos.putNextEntry(new ZipEntry(nombre));
                zos.write(archivos.get(nombre).getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return salida.toByteArray();
    }
}
