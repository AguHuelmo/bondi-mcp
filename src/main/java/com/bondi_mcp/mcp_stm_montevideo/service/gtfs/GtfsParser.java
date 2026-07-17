package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;

import lombok.extern.slf4j.Slf4j;

/**
 * Extrae del GTFS estático las líneas por parada y los recorridos ordenados.
 *
 * <p>El camino es {@code stop_times → trips → routes}: cada parada aparece en horarios de viajes,
 * cada viaje pertenece a una ruta, y la ruta tiene el nombre de la línea. Los {@code stop_id} del
 * GTFS son los mismos {@code busstopId} de la API REST, así que enganchan directo.
 *
 * <p>{@code stop_times.txt} pesa ~88 MB descomprimido y son ~2,2 millones de filas: se recorre en
 * streaming y solo se retiene el resultado agregado, nunca el archivo entero.
 */
@Slf4j
public final class GtfsParser {

    private GtfsParser() {
    }

    /**
     * @param zip contenido de google_transit.zip
     */
    public static DatosGtfs parsear(byte[] zip) {
        final Map<String, String> lineaPorRuta = new HashMap<>();
        final Map<String, Viaje> viajes = new HashMap<>();
        final Map<String, Set<TipoDia>> diasPorServicio = new HashMap<>();
        // Horarios como BitSet indexado por minuto y no como set de enteros: dedupe gratis y unos
        // 240 bytes fijos por (parada, línea, día), contra ~2,2 millones de Integers en TreeSets.
        final Map<ClaveHorario, BitSet> minutosPorClave = new HashMap<>();

        // Dos pasadas, y no una: dentro del zip stop_times.txt viene ANTES que trips.txt, así que
        // en una sola pasada no sabríamos todavía a qué línea pertenece cada viaje. routes, trips
        // y calendar son chicos (321, 37 mil y decenas de filas) y entran en memoria; stop_times
        // no se retiene.
        try {
            recorrerZip(zip, (nombre, zis) -> {
                switch (nombre) {
                    case "routes.txt" -> leerRoutes(zis, lineaPorRuta);
                    case "trips.txt" -> leerTrips(zis, lineaPorRuta, viajes);
                    case "calendar.txt" -> leerCalendar(zis, diasPorServicio);
                    default -> { /* en esta pasada, nada más */ }
                }
            });
            recorrerZip(zip, (nombre, zis) -> {
                if ("stop_times.txt".equals(nombre)) {
                    leerStopTimes(zis, viajes, diasPorServicio, minutosPorClave);
                }
            });
        }
        catch (IOException ex) {
            throw new GtfsInvalidoException("No se pudo leer el zip del GTFS", ex);
        }

        final Map<Long, Set<String>> lineasPorParada = new HashMap<>();
        final Map<String, RecorridoGtfs> recorridos = new HashMap<>();
        for (final Viaje viaje : viajes.values()) {
            final List<Long> paradas = viaje.paradasEnOrden();
            if (paradas.isEmpty()) {
                continue;
            }
            paradas.forEach(parada ->
                    lineasPorParada.computeIfAbsent(parada, p -> new HashSet<>()).add(viaje.linea));
            // Los 37.490 viajes colapsan en ~1.080 recorridos: la mayoría comparte la secuencia
            // exacta de paradas y solo cambia el horario, que acá no interesa.
            recorridos.putIfAbsent(viaje.firma(paradas), new RecorridoGtfs(viaje.linea, viaje.direccion, paradas));
        }

        if (lineasPorParada.isEmpty() || recorridos.isEmpty()) {
            throw new GtfsInvalidoException(
                    "El GTFS no produjo ni relaciones parada-línea ni recorridos; ¿cambió el formato?");
        }

        final List<HorarioTeoricoGtfs> horarios = minutosPorClave.entrySet().stream()
                .map(e -> new HorarioTeoricoGtfs(e.getKey().parada(), e.getKey().linea(),
                        e.getKey().tipoDia(), e.getValue().stream().boxed().toList()))
                .toList();
        if (horarios.isEmpty()) {
            // Sin horarios la app sigue sirviendo (arribos, búsqueda y viajes no dependen de esto),
            // así que no volteamos el import entero; pero es una anomalía que hay que ver.
            log.warn("El GTFS no produjo ningún horario teórico; ¿falta calendar.txt o cambió el formato?");
        }

        log.info("GTFS parseado: {} rutas, {} viajes, {} paradas con líneas, {} recorridos distintos, "
                        + "{} grupos de horarios",
                lineaPorRuta.size(), viajes.size(), lineasPorParada.size(), recorridos.size(),
                horarios.size());
        return new DatosGtfs(lineasPorParada, List.copyOf(recorridos.values()), horarios);
    }

    private static void leerRoutes(ZipInputStream zis, Map<String, String> lineaPorRuta) throws IOException {
        recorrer(zis, (cabecera, campos) -> {
            final String rutaId = campo(cabecera, campos, "route_id");
            final String linea = campo(cabecera, campos, "route_short_name");
            if (rutaId != null && linea != null && !linea.isBlank()) {
                // La API en vivo es case-sensitive: exige "CE1" y responde 400 ante el "Ce1"
                // que trae el GTFS. Normalizamos acá, una sola vez.
                lineaPorRuta.put(rutaId, linea.toUpperCase(Locale.ROOT));
            }
        });
    }

    private static void leerTrips(ZipInputStream zis, Map<String, String> lineaPorRuta,
            Map<String, Viaje> viajes) throws IOException {
        recorrer(zis, (cabecera, campos) -> {
            final String linea = lineaPorRuta.get(campo(cabecera, campos, "route_id"));
            final String viajeId = campo(cabecera, campos, "trip_id");
            if (linea != null && viajeId != null) {
                viajes.put(viajeId, new Viaje(linea, campo(cabecera, campos, "direction_id"),
                        campo(cabecera, campos, "service_id")));
            }
        });
    }

    /**
     * Qué tipos de día cubre cada servicio, según {@code calendar.txt}.
     *
     * <p>Los cinco días hábiles colapsan en {@link TipoDia#HABIL}: en el STM un martes y un jueves
     * tienen los mismos horarios. Las excepciones de {@code calendar_dates.txt} (feriados) no se
     * contemplan: un horario "de feriado" es en la práctica el de domingo, y modelarlo fecha a
     * fecha no paga lo que cuesta.
     */
    private static void leerCalendar(ZipInputStream zis, Map<String, Set<TipoDia>> diasPorServicio)
            throws IOException {
        recorrer(zis, (cabecera, campos) -> {
            final String servicio = campo(cabecera, campos, "service_id");
            if (servicio == null) {
                return;
            }
            final Set<TipoDia> dias = EnumSet.noneOf(TipoDia.class);
            for (final String habil : List.of("monday", "tuesday", "wednesday", "thursday", "friday")) {
                if (activo(cabecera, campos, habil)) {
                    dias.add(TipoDia.HABIL);
                }
            }
            if (activo(cabecera, campos, "saturday")) {
                dias.add(TipoDia.SABADO);
            }
            if (activo(cabecera, campos, "sunday")) {
                dias.add(TipoDia.DOMINGO);
            }
            if (!dias.isEmpty()) {
                diasPorServicio.put(servicio, dias);
            }
        });
    }

    private static boolean activo(Map<String, Integer> cabecera, List<String> campos, String columna) {
        return "1".equals(campo(cabecera, campos, columna));
    }

    private static void leerStopTimes(ZipInputStream zis, Map<String, Viaje> viajes,
            Map<String, Set<TipoDia>> diasPorServicio, Map<ClaveHorario, BitSet> minutosPorClave)
            throws IOException {
        recorrer(zis, (cabecera, campos) -> {
            final Viaje viaje = viajes.get(campo(cabecera, campos, "trip_id"));
            if (viaje == null) {
                return;
            }
            final Long parada = aLong(campo(cabecera, campos, "stop_id"));
            if (parada == null) {
                return;
            }
            final Integer orden = aInt(campo(cabecera, campos, "stop_sequence"));
            if (orden != null) {
                viaje.agregar(orden, parada);
            }

            final Integer minuto = aMinuto(campo(cabecera, campos, "departure_time"),
                    campo(cabecera, campos, "arrival_time"));
            final Set<TipoDia> dias = diasPorServicio.get(viaje.servicio);
            if (minuto == null || dias == null) {
                return;
            }
            for (final TipoDia dia : dias) {
                minutosPorClave.computeIfAbsent(new ClaveHorario(parada, viaje.linea, dia),
                        clave -> new BitSet()).set(minuto);
            }
        });
    }

    /**
     * Minutos desde la medianoche del día de servicio; no se acota a 1440 porque el GTFS escribe
     * "24:30" para el bondi de la 00:30 del servicio del día anterior. La salida manda; la llegada
     * es el respaldo para las paradas donde el GTFS deja la salida vacía.
     */
    private static Integer aMinuto(String salida, String llegada) {
        final String valor = (salida == null || salida.isBlank()) ? llegada : salida;
        if (valor == null || valor.isBlank()) {
            return null;
        }
        final String[] partes = valor.trim().split(":");
        if (partes.length < 2) {
            return null;
        }
        try {
            final int minuto = Integer.parseInt(partes[0]) * 60 + Integer.parseInt(partes[1]);
            return minuto < 0 ? null : minuto;
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Un grupo de horarios: una línea en una parada, un tipo de día. */
    private record ClaveHorario(long parada, String linea, TipoDia tipoDia) {
    }

    /** Un viaje del GTFS mientras se va armando. */
    private static final class Viaje {

        private final String linea;
        private final String direccion;
        private final String servicio;
        // stop_sequence no arranca siempre en 1 ni es necesariamente contiguo: ordenamos por él.
        private final TreeMap<Integer, Long> paradas = new TreeMap<>(Comparator.naturalOrder());

        Viaje(String linea, String direccion, String servicio) {
            this.linea = linea;
            this.direccion = direccion == null ? "" : direccion;
            this.servicio = servicio;
        }

        void agregar(int orden, long parada) {
            paradas.put(orden, parada);
        }

        List<Long> paradasEnOrden() {
            return List.copyOf(paradas.values());
        }

        /** Identifica un recorrido: misma línea, mismo sentido y misma secuencia de paradas. */
        String firma(List<Long> enOrden) {
            return linea + "|" + direccion + "|" + enOrden;
        }
    }

    /** Recorre las entradas del zip entregando cada una a {@code consumer}. */
    private static void recorrerZip(byte[] zip, EntradaConsumer consumer) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                consumer.aceptar(entrada.getName(), zis);
            }
        }
    }

    /** Recorre un CSV con cabecera, sin cargar el archivo entero. */
    private static void recorrer(ZipInputStream zis, FilaConsumer consumer) throws IOException {
        // No cerramos el reader: cerraría el ZipInputStream y con él el resto de las entradas.
        final BufferedReader reader = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
        final String lineaCabecera = reader.readLine();
        if (lineaCabecera == null) {
            return;
        }
        final Map<String, Integer> cabecera = indexar(partirCsv(quitarBom(lineaCabecera)));

        String fila;
        while ((fila = reader.readLine()) != null) {
            if (!fila.isBlank()) {
                consumer.aceptar(cabecera, partirCsv(fila));
            }
        }
    }

    private static Map<String, Integer> indexar(List<String> columnas) {
        final Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < columnas.size(); i++) {
            indice.put(columnas.get(i).trim(), i);
        }
        return indice;
    }

    private static String campo(Map<String, Integer> cabecera, List<String> campos, String nombre) {
        final Integer i = cabecera.get(nombre);
        return (i == null || i >= campos.size()) ? null : campos.get(i);
    }

    /** Parser CSV mínimo: solo comas y comillas dobles, que es todo lo que usa el GTFS. */
    private static List<String> partirCsv(String linea) {
        final List<String> campos = new ArrayList<>();
        final StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            final char c = linea.charAt(i);
            if (c == '"') {
                // "" dentro de un campo entrecomillado es una comilla literal.
                if (entreComillas && i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                    actual.append('"');
                    i++;
                }
                else {
                    entreComillas = !entreComillas;
                }
            }
            else if (c == ',' && !entreComillas) {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            }
            else {
                actual.append(c);
            }
        }
        campos.add(actual.toString().trim());
        return campos;
    }

    private static String quitarBom(String texto) {
        return texto.startsWith("﻿") ? texto.substring(1) : texto;
    }

    private static Long aLong(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer aInt(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    @FunctionalInterface
    private interface FilaConsumer {
        void aceptar(Map<String, Integer> cabecera, List<String> campos);
    }

    @FunctionalInterface
    private interface EntradaConsumer {
        void aceptar(String nombre, ZipInputStream zis) throws IOException;
    }
}
