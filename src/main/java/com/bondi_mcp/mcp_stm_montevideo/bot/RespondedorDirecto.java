package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.domain.RecorridoDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.BusEnVivoService;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.service.RecorridoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * El cerebro determinístico del bot: responde sin ningún LLM, solo con los servicios propios.
 *
 * <p>Es el modo por defecto y el único que no cuesta nada por mensaje, así que es el que se
 * puede dejar corriendo público sin miedo. Entiende una gramática chica y honesta en vez de
 * lenguaje natural: números de parada, "parada línea", cruces para buscar, "origen > destino",
 * "linea 185" y ubicaciones compartidas. Todo lo demás se trata como una búsqueda de paradas,
 * que ya es tolerante a errores por diseño.
 *
 * <p>Como el código del cartel muchas veces no se ve (o no hay cartel), cada lista de paradas
 * que devuelve queda numerada y recordada por charla: alcanza con contestar "1" o "2" para
 * elegir, y "avisame 2 185" también acepta el número de opción.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RespondedorDirecto {

    private static final int MAXIMAS_PARADAS_LISTADAS = 6;
    private static final int MAXIMAS_OPCIONES_DE_VIAJE = 3;
    private static final int PROXIMAS_SALIDAS = 3;

    /** "avisame 3977 185" o "avisame 3977 185 10": parada, línea y minutos opcionales. */
    private static final Pattern AVISAME =
            Pattern.compile("(?iu)^(?:av[ií]same?|alerta)\\s+(\\d{1,7})\\s+(\\S{1,6})(?:\\s+(\\d{1,2}))?$");

    private static final String AYUDA = """
            ¡Hola! Soy el bot de los bondis de Montevideo 🚌

            Mandame:
            · el número de una parada y te digo qué viene → 3977
            · parada y línea para las próximas salidas → 3977 185
            · un cruce, dirección o lugar para encontrar la parada → 18 de julio y ejido
            · origen > destino para saber cómo llegar → estadio centenario > pocitos
            · linea 185 para ver el recorrido y cuántos coches andan
            · avisame 3977 185 y te escribo cuando esté a 5 min o menos
              (avisame 3977 185 10 si querés 10 min; "alertas" las lista, "cancelar" las corta)
            · o compartime tu ubicación con el clip 📎 y te muestro las paradas cercanas

            Cuando te mande una lista de paradas, contestá el número de opción (1, 2...) y
            listo: no hace falta el código del cartel.""";

    private static final String PIE_ARRIBOS = "Los tiempos son estimados y cambian minuto a minuto.";

    private static final String PIE_OPCIONES = "Contestá con el número de opción (1, 2...) y te "
            + "digo qué viene; el código de parada también sirve. Para una alerta: avisame 1 185";

    private final ParadaService paradaService;
    private final ArriboService arriboService;
    private final ViajeService viajeService;
    private final RecorridoService recorridoService;
    private final BusEnVivoService busEnVivoService;
    private final HorarioTeoricoService horarioTeoricoService;
    private final GuardiaDeArribos guardiaDeArribos;

    /** La última lista de paradas que vio cada charla, para poder elegir por número de opción. */
    private final Map<Charla, List<Parada>> ultimasOpciones = new ConcurrentHashMap<>();

    /** Responde un mensaje de texto. Nunca tira: del otro lado hay una persona. */
    public String responder(Charla charla, String texto) {
        try {
            return despachar(charla, texto == null ? "" : texto.trim());
        }
        catch (TransportePublicoException ex) {
            log.warn("La API de la Intendencia falló atendiendo '{}': {}", texto, ex.getMessage());
            return "El servicio de la Intendencia no está respondiendo ahora 🙈 Probá en un rato.";
        }
        catch (RuntimeException ex) {
            log.warn("Falló la respuesta a '{}': {}", texto, ex.getMessage());
            return "Uy, algo falló de mi lado. Probá de nuevo en un ratito.";
        }
    }

    /** Responde una ubicación compartida: las paradas más cercanas y cómo seguir. */
    public String responderUbicacion(Charla charla, double latitud, double longitud) {
        try {
            final List<ParadaCercana> cercanas =
                    paradaService.cercanasA(new Coordenada(latitud, longitud), 5);
            if (cercanas.isEmpty()) {
                return "No encontré paradas cerca de esa ubicación. ¿Estás en Montevideo?";
            }
            return "Las paradas más cerca tuyo:\n\n" + listadoDeCercanas(charla, cercanas)
                    + "\n\n" + PIE_OPCIONES;
        }
        catch (TransportePublicoException ex) {
            log.warn("La API de la Intendencia falló con la ubicación ({}, {}): {}", latitud, longitud,
                    ex.getMessage());
            return "El servicio de la Intendencia no está respondiendo ahora 🙈 Probá en un rato.";
        }
    }

    private String despachar(Charla charla, String texto) {
        if (texto.isEmpty() || texto.startsWith("/start") || texto.startsWith("/ayuda")
                || texto.equalsIgnoreCase("ayuda") || texto.equalsIgnoreCase("hola")) {
            return AYUDA;
        }
        if (texto.equalsIgnoreCase("alertas")) {
            return guardiaDeArribos.listar(charla);
        }
        if (texto.equalsIgnoreCase("cancelar")) {
            return guardiaDeArribos.cancelar(charla);
        }
        final Matcher aviso = AVISAME.matcher(texto);
        if (aviso.matches()) {
            final Optional<Long> parada = codigoDeParada(charla, aviso.group(1));
            if (parada.isEmpty()) {
                return "Para \"avisame " + aviso.group(1) + " ...\" me falta la lista de la que "
                        + "elegir: mandame primero la esquina o dirección. El código de parada "
                        + "también sirve: avisame 3977 " + aviso.group(2);
            }
            return guardiaDeArribos.crear(charla,
                    parada.get(),
                    aviso.group(2),
                    aviso.group(3) == null ? null : Integer.valueOf(aviso.group(3)));
        }
        if (texto.contains(">")) {
            return viaje(texto);
        }
        if (texto.matches("\\d{1,2}")) {
            final Optional<Parada> elegida = opcionElegida(charla, Integer.parseInt(texto));
            if (elegida.isPresent()) {
                return arribos(elegida.get());
            }
            // No era una opción de la última lista: sigue el camino de parada o línea.
        }
        if (texto.matches("\\d{1,7}")) {
            return paradaOLinea(texto);
        }
        // "3977 185" (o "2 185", eligiendo de la última lista): próximas salidas teóricas.
        if (texto.matches("\\d{1,7}\\s+\\S{1,6}")) {
            final String[] partes = texto.split("\\s+");
            final Optional<Long> parada = codigoDeParada(charla, partes[0]);
            if (parada.isPresent()) {
                return proximasSalidas(parada.get(), partes[1]);
            }
            // Un número corto sin lista previa no es una parada: se sigue como búsqueda.
        }
        final String[] palabras = texto.split("\\s+", 2);
        if (palabras.length == 2 && esComandoDeLinea(palabras[0])) {
            return linea(palabras[1]);
        }
        return busqueda(charla, texto);
    }

    private static boolean esComandoDeLinea(String palabra) {
        final String normalizada = palabra.toLowerCase(Locale.ROOT);
        return normalizada.equals("linea") || normalizada.equals("línea")
                || normalizada.equals("recorrido") || normalizada.equals("buses");
    }

    /**
     * Un número suelto es casi siempre el código del cartel de la parada; si no existe esa
     * parada pero sí una línea con ese nombre ("185"), se responde la línea.
     */
    private String paradaOLinea(String numero) {
        final Optional<Parada> parada = paradaService.porCodigo(Long.parseLong(numero));
        if (parada.isPresent()) {
            return arribos(parada.get());
        }
        if (!recorridoService.recorridoDe(numero).sentidos().isEmpty()) {
            return linea(numero);
        }
        return "No encontré la parada " + numero + ". El código está en el cartel del refugio; "
                + "si buscás una esquina, mandámela como texto: 18 de julio y ejido";
    }

    private String arribos(Parada parada) {
        final StringBuilder sb = new StringBuilder("📍 ").append(parada.descripcion())
                .append(" (parada ").append(parada.codigo()).append(")\n\n");
        try {
            final ArribosDeParada resultado = arriboService.proximosArribos(parada.codigo());
            if (resultado.arribos().isEmpty()) {
                sb.append("Ahora no viene ningún bondi a esta parada.\n");
            }
            else {
                resultado.arribos().forEach(arribo -> sb.append(renglonArribo(arribo)).append('\n'));
            }
            if (!resultado.lineasQuePasan().isEmpty()) {
                sb.append("\nPasan por acá: ").append(String.join(", ", resultado.lineasQuePasan()));
                sb.append("\nHorarios de una línea: mandá \"").append(parada.codigo()).append(" 185\"");
            }
            if (!resultado.arribos().isEmpty()) {
                sb.append("\n\n").append(PIE_ARRIBOS);
            }
        }
        catch (TransportePublicoException ex) {
            // El tiempo real se cayó, pero las líneas de la parada salen de nuestra base.
            log.warn("Fallaron los arribos de la parada {}: {}", parada.codigo(), ex.getMessage());
            sb.append("El tiempo real de la Intendencia no responde ahora 🙈\n");
            final List<String> lineas = arriboService.lineasQuePasan(parada.codigo());
            if (!lineas.isEmpty()) {
                sb.append("Igual te digo: por acá pasan ").append(String.join(", ", lineas)).append('.');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static String renglonArribo(Arribo arribo) {
        final String espera = arribo.esperaEnMinutos() <= 0
                ? "llegando"
                : arribo.esperaEnMinutos() + " min";
        final String distancia = arribo.distanciaMetros() == null
                ? ""
                : " · a " + arribo.distanciaMetros() + " m";
        return "🚌 " + arribo.linea() + " → " + (arribo.destino() == null ? "" : arribo.destino())
                + " · " + espera + distancia;
    }

    private String proximasSalidas(long codigoParada, String linea) {
        final List<SalidaTeorica> salidas =
                horarioTeoricoService.proximasSalidas(codigoParada, linea, PROXIMAS_SALIDAS);
        final String nombreLinea = linea.toUpperCase(Locale.ROOT);
        if (salidas.isEmpty()) {
            return "No tengo horarios de la " + nombreLinea + " en la parada " + codigoParada
                    + ". Mandá solo el número de parada para ver qué líneas pasan.";
        }
        final String lista = salidas.stream()
                .map(RespondedorDirecto::renglonSalida)
                .collect(Collectors.joining("\n"));
        return "Próximas salidas de la " + nombreLinea + " por la parada " + codigoParada + ":\n\n"
                + lista + "\n\nSon horarios programados: puede pasar unos minutos antes o después. "
                + "Para verlo venir en vivo mandá solo " + codigoParada + ".";
    }

    private static String renglonSalida(SalidaTeorica salida) {
        final LocalDate hoy = LocalDate.now(HorarioTeoricoService.ZONA_MONTEVIDEO);
        final String dia;
        if (salida.momento().toLocalDate().equals(hoy)) {
            dia = "hoy";
        }
        else if (salida.momento().toLocalDate().equals(hoy.plusDays(1))) {
            dia = "mañana";
        }
        else {
            dia = salida.momento().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"));
        }
        return "🕐 " + dia + " " + salida.momento().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String linea(String nombre) {
        final RecorridoDeLinea recorrido = recorridoService.recorridoDe(nombre);
        if (recorrido.sentidos().isEmpty()) {
            return "No conozco la línea " + nombre.toUpperCase(Locale.ROOT)
                    + ". Probá como figura en el cartel del coche: 185, CE1, D5.";
        }
        final StringBuilder sb = new StringBuilder("Línea ").append(recorrido.linea()).append(":\n");
        recorrido.sentidos().forEach(sentido -> sb.append("→ hacia ").append(sentido.destino())
                .append(" (").append(sentido.paradas().size()).append(" paradas)\n"));
        sb.append('\n').append(cochesEnCalle(recorrido.linea()));
        return sb.toString().stripTrailing();
    }

    /** Cuántos coches andan ahora. Si el tiempo real está caído, el recorrido se informa igual. */
    private String cochesEnCalle(String linea) {
        try {
            final int coches = busEnVivoService.deLinea(linea).size();
            if (coches == 0) {
                return "Ahora no hay coches transmitiendo. De noche o los domingos es normal.";
            }
            return coches == 1
                    ? "Hay 1 coche en la calle ahora, contando ambos sentidos."
                    : "Hay " + coches + " coches en la calle ahora, contando ambos sentidos.";
        }
        catch (TransportePublicoException ex) {
            log.warn("Fallaron los buses en vivo de la línea {}: {}", linea, ex.getMessage());
            return "El tiempo real de la Intendencia no responde ahora, así que no sé cuántos coches andan.";
        }
    }

    private String viaje(String texto) {
        final String[] extremos = texto.split(">", 2);
        final String origen = extremos[0].trim();
        final String destino = extremos[1].trim();
        if (origen.isEmpty() || destino.isEmpty()) {
            return "Mandame el viaje como: origen > destino. Por ejemplo: 18 de julio y ejido > tres cruces";
        }

        final Optional<Coordenada> desde = viajeService.ubicar(origen);
        final Optional<Coordenada> hasta = viajeService.ubicar(destino);
        if (desde.isEmpty() || hasta.isEmpty()) {
            final String cual = desde.isEmpty() ? origen : destino;
            return "No pude ubicar \"" + cual + "\". Probá con un cruce de calles, como \"18 de julio y ejido\".";
        }

        final List<Viaje> viajes = viajeService.comoLlegar(desde.get(), hasta.get());
        if (viajes.isEmpty()) {
            return "No encontré ningún bondi que te lleve, ni con transbordo. "
                    + "Puede que el trayecto se haga caminando, o probá desde otra esquina.";
        }

        final StringBuilder sb = new StringBuilder();
        final List<Viaje> mejores = viajes.stream().limit(MAXIMAS_OPCIONES_DE_VIAJE).toList();
        for (int i = 0; i < mejores.size(); i++) {
            sb.append(tarjetaViaje(i + 1, mejores.get(i))).append("\n\n");
        }
        sb.append("Para saber cuándo pasa, mandá el número de la parada donde subís.");
        return sb.toString();
    }

    private static String tarjetaViaje(int numero, Viaje viaje) {
        final StringBuilder sb = new StringBuilder("Opción ").append(numero).append(": ")
                .append(String.join(" y después ", viaje.lineas()))
                .append(viaje.transbordos() == 0 ? " · directo" : " · 1 transbordo")
                .append(" · caminás ").append(metros(viaje.metrosCaminando()));
        viaje.tramos().forEach(tramo -> sb.append('\n')
                .append("🚌 ").append(tramo.linea())
                .append(": subís en ").append(tramo.subida().descripcion())
                .append(" (parada ").append(tramo.subida().codigo()).append(')')
                .append(", bajás en ").append(tramo.bajada().descripcion())
                .append(" (parada ").append(tramo.bajada().codigo()).append(')'));
        return sb.toString();
    }

    private static String metros(int total) {
        return total < 1000 ? total + " m" : String.format(Locale.ROOT, "%.1f km", total / 1000.0);
    }

    private String busqueda(Charla charla, String texto) {
        final ResultadoBusqueda resultado = paradaService.buscar(texto);

        if (resultado.hayCercanasAlPunto()) {
            final String titulo = resultado.punto().descripcion() != null
                    ? "📍 " + resultado.punto().descripcion() + ". Las paradas más cercanas:"
                    : "En ese cruce no hay parada, pero estimé dónde queda. Las más cercanas:";
            return titulo + "\n\n" + listadoDeCercanas(charla, resultado.cercanasAlPunto())
                    + "\n\n" + PIE_OPCIONES;
        }

        if (resultado.sinResultados()) {
            return "No encontré paradas con eso. Probá con una sola calle del cruce, o como "
                    + "figura en el cartel (a veces llevan inicial: GABRIEL A PEREIRA).";
        }

        // Una sola coincidencia exacta: ir directo a los arribos ahorra un mensaje.
        if (resultado.exactas() == 1) {
            return arribos(resultado.paradas().getFirst());
        }

        final String titulo = resultado.soloAproximadas()
                ? "Ninguna coincide del todo. ¿Será alguna de estas?"
                : "Encontré estas paradas:";
        final List<Parada> opciones = resultado.paradas().stream()
                .limit(MAXIMAS_PARADAS_LISTADAS)
                .toList();
        recordarOpciones(charla, opciones);
        final StringBuilder listado = new StringBuilder();
        for (int i = 0; i < opciones.size(); i++) {
            if (i > 0) {
                listado.append('\n');
            }
            listado.append(i + 1).append(") ").append(opciones.get(i).descripcion())
                    .append(" (parada ").append(opciones.get(i).codigo()).append(')');
        }
        return titulo + "\n\n" + listado + "\n\n" + PIE_OPCIONES;
    }

    /** Lista numerada de cercanas con distancia; queda recordada para elegir por número. */
    private String listadoDeCercanas(Charla charla, List<ParadaCercana> cercanas) {
        recordarOpciones(charla, cercanas.stream().map(ParadaCercana::parada).toList());
        final StringBuilder listado = new StringBuilder();
        for (int i = 0; i < cercanas.size(); i++) {
            final ParadaCercana cercana = cercanas.get(i);
            if (i > 0) {
                listado.append('\n');
            }
            listado.append(i + 1).append(") a ").append(cercana.distanciaLegible()).append(" · ")
                    .append(cercana.parada().descripcion())
                    .append(" (parada ").append(cercana.parada().codigo()).append(')');
        }
        return listado.toString();
    }

    private void recordarOpciones(Charla charla, List<Parada> paradas) {
        ultimasOpciones.put(charla, List.copyOf(paradas));
    }

    /** La parada elegida de la última lista, si el número corresponde a una opción. */
    private Optional<Parada> opcionElegida(Charla charla, int numero) {
        final List<Parada> opciones = ultimasOpciones.get(charla);
        if (opciones == null || numero < 1 || numero > opciones.size()) {
            return Optional.empty();
        }
        return Optional.of(opciones.get(numero - 1));
    }

    /**
     * Resuelve lo que el usuario llamó "parada": un código del cartel (3 dígitos o más) o el
     * número de una opción de la última lista (1 o 2 dígitos).
     */
    private Optional<Long> codigoDeParada(Charla charla, String numero) {
        if (numero.length() >= 3) {
            return Optional.of(Long.parseLong(numero));
        }
        return opcionElegida(charla, Integer.parseInt(numero)).map(Parada::codigo);
    }
}
