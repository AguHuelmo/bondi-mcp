package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.bondi_mcp.mcp_stm_montevideo.mcp.TransporteMcpTools;

import lombok.extern.slf4j.Slf4j;

/**
 * El agente conversacional: Claude con las mismas herramientas que el servidor MCP.
 *
 * <p>No hay lógica de transporte acá: cada tool delega en {@link TransporteMcpTools}, que ya
 * devuelve resultados con su campo {@code contexto} pensado para que un LLM no invente. El bot
 * es un tercer consumidor de la misma capa de servicio, junto al MCP y la API REST.
 *
 * <p>El historial vive en memoria por chat: suficiente para conversar con continuidad, y se
 * pierde al reiniciar, que para un bot de bondis está bien.
 */
@Slf4j
@Component
public class ConversacionBot {

    /** Tope de idas y vueltas con herramientas por mensaje: corta cualquier loop descontrolado. */
    private static final int MAXIMAS_VUELTAS_DE_HERRAMIENTAS = 8;

    private static final long MAX_TOKENS = 2048;

    /**
     * El personaje y las reglas. Estable a propósito: se cachea como prefijo en la API de
     * Anthropic, así que meterle nada variable (fecha, nombre del usuario) lo invalidaría.
     */
    private static final String INSTRUCCIONES = """
            Sos "Bondi", el bot de Telegram del transporte público de Montevideo (STM). Ayudás a \
            saber cuándo viene el ómnibus, qué línea lleva a dónde, y por dónde anda cada coche.

            Reglas:
            - Contestá en español rioplatense, breve y al grano: esto es un chat, no un informe. \
            Un emoji cada tanto está bien 🚌, muchos es ruido.
            - Texto plano solamente: nada de Markdown (ni asteriscos, ni numerales, ni tablas), \
            Telegram lo muestra tal cual.
            - Para CUALQUIER dato de bondis (paradas, arribos, horarios, recorridos, viajes) usá \
            las herramientas. NUNCA inventes líneas, horarios ni paradas: si una herramienta no \
            lo tiene, decilo con honestidad.
            - Cada herramienta devuelve un campo "contexto" que explica qué tan confiable es el \
            resultado y qué conviene repreguntar: hacele caso siempre.
            - Los tiempos de arribo son estimados y cambian minuto a minuto: aclaralo al darlos.
            - Si el usuario comparte su ubicación de Telegram te llega como un mensaje con \
            latitud y longitud: usá paradas_cercanas con esos números, sin pedirle nada más.
            - Si te saludan o mandan /start, presentate en dos líneas con ejemplos de lo que \
            sabés hacer: "¿cuándo pasa la 405 por 18 y Ejido?", "¿cómo llego del Estadio \
            Centenario a Pocitos?", o compartir la ubicación para ver las paradas cercanas.
            - Si la charla se va de tema, seguila con simpatía una línea y traela de vuelta a \
            los bondis.""";

    private final BotProperties properties;
    private final TransporteMcpTools transporte;
    private final AnthropicClient client;
    private final List<Tool> herramientas;
    private final Map<Long, Deque<Turno>> historiales = new ConcurrentHashMap<>();

    public ConversacionBot(BotProperties properties, TransporteMcpTools transporte) {
        this.properties = properties;
        this.transporte = transporte;
        this.herramientas = definirHerramientas();
        this.client = properties.claude().configurado()
                ? AnthropicOkHttpClient.builder().apiKey(properties.claude().apiKey()).build()
                : null;
    }

    /** {@code true} si hay API key de Anthropic; sin ella el bot no puede conversar. */
    public boolean habilitado() {
        return client != null;
    }

    /**
     * Responde un mensaje de un chat, con memoria de la conversación.
     *
     * <p>Nunca tira excepción: un fallo (de Anthropic, de la Intendencia, nuestro) se convierte
     * en una disculpa corta, porque del otro lado hay una persona esperando un mensaje.
     */
    public String responder(long chatId, String textoUsuario) {
        if (client == null) {
            return "El bot no está configurado todavía.";
        }
        try {
            return conversar(chatId, textoUsuario);
        }
        catch (RuntimeException ex) {
            log.warn("Falló la conversación del chat {}: {}", chatId, ex.getMessage());
            return "Uy, algo falló de mi lado 🙈 Probá de nuevo en un ratito.";
        }
    }

    private String conversar(long chatId, String textoUsuario) {
        final Deque<Turno> historial = historiales.computeIfAbsent(chatId, id -> new ArrayDeque<>());

        final List<MessageParam> mensajes = new ArrayList<>();
        historial.forEach(turno -> mensajes.add(MessageParam.builder()
                .role(turno.delUsuario() ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                .content(turno.texto())
                .build()));
        mensajes.add(MessageParam.builder().role(MessageParam.Role.USER).content(textoUsuario).build());

        Message respuesta = pedirAClaude(mensajes);
        int vueltas = 0;
        while (esPedidoDeHerramientas(respuesta) && vueltas++ < MAXIMAS_VUELTAS_DE_HERRAMIENTAS) {
            mensajes.add(respuesta.toParam());
            mensajes.add(resultadosDeHerramientas(respuesta));
            respuesta = pedirAClaude(mensajes);
        }

        final String texto = textoDe(respuesta);
        recordar(historial, textoUsuario, texto);
        return texto;
    }

    private Message pedirAClaude(List<MessageParam> mensajes) {
        final MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(properties.claude().model())
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(INSTRUCCIONES)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .messages(mensajes);
        herramientas.forEach(params::addTool);
        return client.messages().create(params.build());
    }

    private static boolean esPedidoDeHerramientas(Message respuesta) {
        return respuesta.stopReason().filter(StopReason.TOOL_USE::equals).isPresent();
    }

    /** Ejecuta todas las herramientas que pidió la respuesta y arma el mensaje con los resultados. */
    private MessageParam resultadosDeHerramientas(Message respuesta) {
        final List<ContentBlockParam> resultados = new ArrayList<>();
        for (final ContentBlock bloque : respuesta.content()) {
            bloque.toolUse().ifPresent(uso -> resultados.add(ContentBlockParam.ofToolResult(resultadoDe(uso))));
        }
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(resultados)
                .build();
    }

    private ToolResultBlockParam resultadoDe(ToolUseBlock uso) {
        try {
            final Object resultado = ejecutarHerramienta(uso.name(), uso._input());
            return ToolResultBlockParam.builder()
                    .toolUseId(uso.id())
                    .contentAsJson(resultado)
                    .build();
        }
        catch (RuntimeException ex) {
            // El error vuelve como dato para que el agente lo explique, igual que en el MCP.
            log.warn("Falló la herramienta {} del bot: {}", uso.name(), ex.getMessage());
            return ToolResultBlockParam.builder()
                    .toolUseId(uso.id())
                    .content("La herramienta falló: " + ex.getMessage())
                    .isError(true)
                    .build();
        }
    }

    /**
     * Despacha una tool del agente a {@link TransporteMcpTools}.
     *
     * <p>Mismos nombres y mismos parámetros que las tools MCP: lo que aprende un agente sobre
     * una superficie vale para la otra.
     */
    Object ejecutarHerramienta(String nombre, JsonValue input) {
        return switch (nombre) {
            case "buscar_paradas" -> transporte.buscarParadas(input.convert(ArgsConsulta.class).consulta());
            case "paradas_cercanas" -> {
                final ArgsCercanas args = input.convert(ArgsCercanas.class);
                yield transporte.paradasCercanas(args.latitud(), args.longitud(), args.cantidad());
            }
            case "consultar_arribos" -> transporte.consultarArribos(input.convert(ArgsParada.class).codigoParada());
            case "como_llego" -> {
                final ArgsViaje args = input.convert(ArgsViaje.class);
                yield transporte.comoLlego(args.origen(), args.destino());
            }
            case "horarios_teoricos" -> {
                final ArgsParadaLinea args = input.convert(ArgsParadaLinea.class);
                yield transporte.horariosTeoricos(args.codigoParada(), args.linea());
            }
            case "proxima_salida" -> {
                final ArgsParadaLinea args = input.convert(ArgsParadaLinea.class);
                yield transporte.proximaSalida(args.codigoParada(), args.linea(), args.cantidad());
            }
            case "recorrido_de_linea" -> transporte.recorridoDeLinea(input.convert(ArgsLinea.class).linea());
            case "buses_en_vivo" -> transporte.busesEnVivo(input.convert(ArgsLinea.class).linea());
            default -> throw new IllegalArgumentException("Herramienta desconocida: " + nombre);
        };
    }

    private static String textoDe(Message respuesta) {
        final String texto = respuesta.content().stream()
                .flatMap(bloque -> bloque.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining("\n"))
                .trim();
        if (!texto.isBlank()) {
            return texto;
        }
        if (respuesta.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            return "Con eso no te puedo ayudar. Preguntame por los bondis 🚌";
        }
        return "Me quedé sin respuesta. Probá preguntándome de nuevo.";
    }

    /** Guarda el intercambio y olvida lo más viejo cuando el historial se pasa del tope. */
    private void recordar(Deque<Turno> historial, String delUsuario, String delBot) {
        historial.addLast(new Turno(true, delUsuario));
        historial.addLast(new Turno(false, delBot));
        while (historial.size() > properties.claude().maxTurnosDeHistorial()) {
            historial.pollFirst();
        }
        // El primer turno tiene que ser del usuario: si el recorte dejó uno del bot al frente,
        // se descarta para que la conversación siga alternando bien.
        while (!historial.isEmpty() && !historial.peekFirst().delUsuario()) {
            historial.pollFirst();
        }
    }

    /**
     * Los esquemas de las 8 herramientas, espejo de las tools MCP.
     *
     * <p>Descripciones cortas a propósito: el trabajo fino de guiar al agente lo hace el campo
     * "contexto" de cada resultado, no el esquema.
     */
    private static List<Tool> definirHerramientas() {
        return List.of(
                herramienta("buscar_paradas",
                        "Busca paradas de ómnibus de Montevideo por dirección con número "
                                + "(\"gabriel pereira 2470\"), cruce de calles (\"18 de julio y ejido\"), "
                                + "lugar conocido (\"estadio centenario\") o código de parada. Devuelve "
                                + "candidatas con su código, que sirve para consultar_arribos. Pasá la "
                                + "consulta del usuario tal cual, sin convertirla vos.",
                        Map.of("consulta", texto("Dirección, cruce de calles, lugar o código de parada")),
                        List.of("consulta")),
                herramienta("paradas_cercanas",
                        "Las paradas más cercanas a una coordenada, ordenadas por distancia en línea "
                                + "recta. Usala cuando el usuario comparte su ubicación.",
                        Map.of("latitud", numero("Latitud en grados decimales (Montevideo ronda -34.9)"),
                                "longitud", numero("Longitud en grados decimales (Montevideo ronda -56.2)"),
                                "cantidad", entero("Cuántas devolver; por defecto 5")),
                        List.of("latitud", "longitud")),
                herramienta("consultar_arribos",
                        "Los próximos ómnibus que llegan a una parada, con línea, destino y espera "
                                + "en minutos, más todas las líneas que paran ahí.",
                        Map.of("codigoParada", entero("Código de la parada, sale de buscar_paradas")),
                        List.of("codigoParada")),
                herramienta("como_llego",
                        "Qué líneas llevan de un lugar a otro de Montevideo, con o sin transbordo: "
                                + "dónde subir, dónde bajar y cuánto se camina. Usá esta tool y no "
                                + "deduzcas viajes cruzando líneas de dos paradas.",
                        Map.of("origen", texto("Dirección o cruce de calles de origen"),
                                "destino", texto("Dirección o cruce de calles de destino")),
                        List.of("origen", "destino")),
                herramienta("horarios_teoricos",
                        "Todos los horarios programados de una línea en una parada, por tipo de día "
                                + "(hábiles, sábados, domingos). Para \"cuándo sale el próximo\" es "
                                + "más fácil proxima_salida.",
                        Map.of("codigoParada", entero("Código de la parada"),
                                "linea", texto("Línea, como \"185\" o \"CE1\"")),
                        List.of("codigoParada", "linea")),
                herramienta("proxima_salida",
                        "Cuándo sale la próxima vez una línea de una parada según los horarios "
                                + "programados, con fecha, hora y espera en minutos ya resueltas "
                                + "(incluida la trasnoche). Usala en vez de calcularlo vos con "
                                + "horarios_teoricos.",
                        Map.of("codigoParada", entero("Código de la parada"),
                                "linea", texto("Línea, como \"185\" o \"CE1\""),
                                "cantidad", entero("Cuántas salidas devolver; por defecto 3")),
                        List.of("codigoParada", "linea")),
                herramienta("recorrido_de_linea",
                        "El recorrido completo de una línea: todas sus paradas en orden, un sentido "
                                + "por dirección. Para \"¿por dónde va la 185?\".",
                        Map.of("linea", texto("Línea, como \"185\" o \"CE1\"")),
                        List.of("linea")),
                herramienta("buses_en_vivo",
                        "Dónde está ahora cada coche de una línea (GPS de hace unos segundos), con "
                                + "ambos sentidos mezclados. Para \"¿dónde anda la 185?\".",
                        Map.of("linea", texto("Línea, como \"185\" o \"CE1\"")),
                        List.of("linea")));
    }

    private static Tool herramienta(String nombre, String descripcion,
            Map<String, Map<String, String>> propiedades, List<String> requeridas) {
        final Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        propiedades.forEach((clave, esquema) -> props.putAdditionalProperty(clave, JsonValue.from(esquema)));
        return Tool.builder()
                .name(nombre)
                .description(descripcion)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(props.build())
                        .required(requeridas)
                        .build())
                .build();
    }

    private static Map<String, String> texto(String descripcion) {
        return Map.of("type", "string", "description", descripcion);
    }

    private static Map<String, String> numero(String descripcion) {
        return Map.of("type", "number", "description", descripcion);
    }

    private static Map<String, String> entero(String descripcion) {
        return Map.of("type", "integer", "description", descripcion);
    }

    /** Un mensaje del historial: quién lo dijo y qué. */
    private record Turno(boolean delUsuario, String texto) {
    }

    /* Argumentos de las herramientas, tal como los manda el agente. */

    record ArgsConsulta(String consulta) {
    }

    record ArgsCercanas(Double latitud, Double longitud, Integer cantidad) {
    }

    record ArgsParada(Long codigoParada) {
    }

    record ArgsViaje(String origen, String destino) {
    }

    record ArgsParadaLinea(Long codigoParada, String linea, Integer cantidad) {
    }

    record ArgsLinea(String linea) {
    }
}
