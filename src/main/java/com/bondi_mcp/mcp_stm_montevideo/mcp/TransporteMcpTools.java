package com.bondi_mcp.mcp_stm_montevideo.mcp;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.HorariosDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.service.ViajeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Herramientas MCP sobre el STM de Montevideo.
 *
 * <p>Fachada delgada: toda la lógica vive en los servicios, que comparte con los controllers REST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransporteMcpTools {

    private final ParadaService paradaService;
    private final ArriboService arriboService;
    private final ViajeService viajeService;
    private final HorarioTeoricoService horarioTeoricoService;

    @McpTool(name = "buscar_paradas",
            description = """
                    Busca paradas de ómnibus de Montevideo por dirección con número de puerta \
                    (por ejemplo "gabriel pereira 2470"), por cruce de calles (por ejemplo \
                    "18 de julio y ejido"), por lugar conocido (por ejemplo "estadio centenario" \
                    o "terminal tres cruces") o por código de parada. Devuelve candidatas con su \
                    código, que sirve para consultar_arribos.

                    Si el usuario te da una dirección con número o el nombre de un lugar, pasáselo \
                    tal cual: se ubica contra el padrón oficial y se devuelven las paradas más \
                    cercanas a ese punto. No lo conviertas vos a un cruce ni le saques el número.

                    La búsqueda es aproximada: ordena por cuántas palabras coinciden, así que \
                    también responde cuando el usuario le erra a una calle. Mirá SIEMPRE el campo \
                    "contexto": te dice si lo que devolvió es lo que se pidió o una aproximación, \
                    y qué conviene preguntarle al usuario. No afirmes que encontraste la parada \
                    pedida cuando el contexto dice que son aproximaciones.""")
    public ResultadoDeBusqueda buscarParadas(
            @McpToolParam(description = "Dirección con número, cruce de calles o código de parada",
                    required = true) String consulta) {

        final ResultadoBusqueda resultado = paradaService.buscar(consulta);
        final List<ParadaEncontrada> paradas = resultado.paradas().stream()
                .map(p -> new ParadaEncontrada(p.codigo(), p.descripcion(),
                        p.ubicacion() == null ? null : p.ubicacion().latitud(),
                        p.ubicacion() == null ? null : p.ubicacion().longitud()))
                .toList();

        final List<ParadaProxima> cercanas = resultado.cercanasAlPunto().stream()
                .map(c -> new ParadaProxima(c.parada().codigo(), c.parada().descripcion(),
                        c.distanciaMetros(), c.distanciaLegible()))
                .toList();

        return new ResultadoDeBusqueda(consulta, paradas, cercanas, contextoDe(resultado));
    }

    @McpTool(name = "paradas_cercanas",
            description = """
                    Devuelve las paradas más cercanas a una coordenada, ordenadas por distancia. \
                    Usala cuando sepas dónde está el usuario (por ejemplo si te comparte su \
                    ubicación) o para explorar alrededor de una parada que ya encontraste con \
                    buscar_paradas, que devuelve latitud y longitud de cada una.

                    La distancia es en línea recta, no caminando: la real siempre es algo mayor. \
                    No sirve para direcciones en texto, para eso está buscar_paradas.""")
    public ResultadoCercanas paradasCercanas(
            @McpToolParam(description = "Latitud, en grados decimales (Montevideo ronda -34.9)",
                    required = true) double latitud,
            @McpToolParam(description = "Longitud, en grados decimales (Montevideo ronda -56.2)",
                    required = true) double longitud,
            @McpToolParam(description = "Cuántas paradas devolver; por defecto 5",
                    required = false) Integer cantidad) {

        final int limite = (cantidad == null || cantidad < 1) ? 5 : Math.min(cantidad, 20);
        final List<ParadaProxima> cercanas = paradaService
                .cercanasA(new Coordenada(latitud, longitud), limite).stream()
                .map(c -> new ParadaProxima(c.parada().codigo(), c.parada().descripcion(),
                        c.distanciaMetros(), c.distanciaLegible()))
                .toList();

        return new ResultadoCercanas(cercanas, contextoDeCercanas(cercanas));
    }

    private static String contextoDeCercanas(List<ParadaProxima> cercanas) {
        if (cercanas.isEmpty()) {
            return "No hay ninguna parada cerca de ese punto. Verificá que las coordenadas sean de "
                    + "Montevideo (latitud cerca de -34.9, longitud cerca de -56.2): este servicio "
                    + "solo cubre el STM de Montevideo.";
        }
        final ParadaProxima primera = cercanas.getFirst();
        if (primera.distanciaMetros() > 1000) {
            return "La parada más cercana está a " + primera.distanciaLegible() + ", que es bastante "
                    + "lejos. Puede que el punto esté fuera de la zona con servicio, o que las "
                    + "coordenadas estén equivocadas. Avisale al usuario antes de mandarlo a caminar.";
        }
        return "Ordenadas de más cerca a más lejos, en línea recta (caminando siempre es un poco "
                + "más). Ofrecele las primeras y usá consultar_arribos con el código de la que elija.";
    }

    /**
     * Explica al LLM qué representa el resultado y qué preguntar.
     *
     * <p>Sin esto un resultado aproximado es indistinguible de uno exacto, y el modelo termina
     * afirmando que encontró una parada que el usuario nunca pidió.
     */
    private static String contextoDe(ResultadoBusqueda resultado) {
        if (resultado.palabrasBuscadas().isEmpty()) {
            return "La consulta no tiene ninguna palabra buscable. Pedile al usuario una dirección, "
                    + "un cruce de calles o un código de parada.";
        }
        if (resultado.sinResultados()) {
            return """
                    Ninguna parada coincide, ni siquiera parcialmente. Posibles causas, en orden \
                    de probabilidad: la calle se escribe distinto en el padrón (suelen llevar la \
                    inicial del medio, como "GABRIEL A PEREIRA", o abreviaturas como "AV", "CNO", \
                    "BV", "GRAL"), o es un barrio o punto de interés en vez de una calle, o está \
                    fuera de Montevideo. Preguntale al usuario por UNA sola calle en vez del \
                    cruce completo: con una alcanza y es mucho más probable que matchee.""";
        }
        if (resultado.hayCercanasAlPunto()) {
            return switch (resultado.punto().origen()) {
                case DIRECCION_OFICIAL -> """
                        Ubicamos la dirección pedida en el padrón oficial (es "%s") y \
                        "cercanasAlPunto" trae las paradas más próximas a esa puerta, ordenadas \
                        por distancia real. El dato es confiable, no una estimación: decile a qué \
                        dirección la resolvimos y ofrecele la más cercana con la distancia ("la \
                        más cerca está a 150 m, en tal cruce"). "paradas" viene vacío a propósito: \
                        sabiendo dónde queda la puerta, las paradas que se llaman parecido no \
                        aportan nada.""".formatted(resultado.punto().descripcion());
                case LUGAR_CONOCIDO -> """
                        Lo que se buscó es un lugar conocido y lo ubicamos en el padrón: es "%s". \
                        "cercanasAlPunto" trae las paradas más próximas a ese lugar, ordenadas por \
                        distancia real, y "paradas" viene vacío a propósito. Decile al usuario a \
                        qué lugar lo resolvimos ANTES de darle las paradas: el nombre lo sacamos \
                        del padrón por parecido, así que si resolvimos cualquier cosa, es la única \
                        forma de que se dé cuenta y te corrija.""".formatted(
                                resultado.punto().descripcion());
                case CRUCE_ESTIMADO -> """
                        No hay ninguna parada en ese cruce, pero SÍ pudimos ubicar dónde queda, y \
                        "cercanasAlPunto" trae las más próximas con su distancia real. Este es el \
                        caso más útil: decile al usuario que en esa esquina no hay parada y \
                        ofrecele la más cercana con la distancia ("la más cerca está a 150 m, en \
                        tal cruce"). Ojo: el cruce está estimado a partir de las paradas de cada \
                        calle, así que las distancias son aproximadas. Las de "paradas" son solo \
                        coincidencias parciales de texto, mucho menos relevantes: guiate por \
                        cercanasAlPunto.""";
            };
        }
        if (resultado.soloAproximadas()) {
            return """
                    ATENCIÓN: ninguna parada contiene todas las palabras buscadas, y tampoco se \
                    pudo ubicar el lugar. Si era un cruce, las calles no parecen cruzarse o alguna \
                    no tiene paradas; si era una dirección, ese número de puerta no figura en el \
                    padrón oficial. Estas son aproximaciones ordenadas por cuántas palabras \
                    coinciden. Lo más probable es que el usuario le haya errado a la calle o al \
                    número. NO afirmes que encontraste lo que pidió: mostrale estas opciones como \
                    candidatas, decile con qué coinciden, y preguntale cuál es, o si quiso decir \
                    otra calle u otro número.""";
        }
        return """
                Hay coincidencias exactas: las primeras contienen todas las palabras buscadas. \
                Si hay varias, suelen ser las dos veredas del mismo cruce o paradas consecutivas \
                de la misma calle; preguntale al usuario hacia qué lado va o cuál le queda más \
                cerca antes de consultar arribos.""";
    }

    @McpTool(name = "consultar_arribos",
            description = """
                    Devuelve los próximos ómnibus que llegan a una parada, con la línea, el \
                    destino y el tiempo estimado de espera en minutos. Incluye además todas las \
                    líneas que pasan por esa parada, sirva o no para un arribo ahora: si no viene \
                    ninguno, esas son las líneas que igual paran ahí. El código de parada se \
                    obtiene con buscar_paradas.""")
    public RespuestaArribos consultarArribos(
            @McpToolParam(description = "Código de la parada", required = true) long codigoParada) {

        final String descripcion = paradaService.porCodigo(codigoParada)
                .map(Parada::descripcion)
                .orElse("Parada " + codigoParada);

        try {
            final ArribosDeParada resultado = arriboService.proximosArribos(codigoParada);
            final List<ProximoArribo> proximos = resultado.arribos().stream()
                    .map(a -> new ProximoArribo(a.linea(), a.destino(), a.esperaEnMinutos(),
                            a.distanciaMetros(), a.empresa()))
                    .toList();
            return new RespuestaArribos(codigoParada, descripcion, proximos,
                    resultado.lineasQuePasan(), contextoDe(resultado, descripcion), null);
        }
        catch (TransportePublicoException ex) {
            // Un fallo de la API externa se le devuelve al agente como dato, no como excepción:
            // así puede explicarlo en vez de cortar la conversación. Las líneas que pasan salen
            // de nuestra base, así que se informan igual.
            log.warn("Falló la consulta de arribos de la parada {}: {}", codigoParada, ex.getMessage());
            final List<String> lineas = arriboService.lineasQuePasan(codigoParada);
            return new RespuestaArribos(codigoParada, descripcion, List.of(), lineas,
                    "El servicio de tiempo real de la Intendencia no respondió. Esto es una falla "
                            + "temporal de ellos, no del usuario: decíselo y ofrecele reintentar en "
                            + "un rato. Las líneas que pasan por la parada, en cambio, son un dato "
                            + "fijo y siguen siendo válidas.",
                    "No se pudieron obtener los arribos: la API de la Intendencia no respondió correctamente.");
        }
    }

    /** Explica al LLM qué hacer con unos arribos, sobre todo cuando no hay ninguno. */
    private static String contextoDe(ArribosDeParada resultado, String descripcion) {
        if (resultado.hayArribos()) {
            return "Los tiempos son estimados y cambian minuto a minuto. Si el usuario pregunta por "
                    + "una línea puntual que no aparece en los arribos pero sí en lineasQuePasan, "
                    + "significa que esa línea para acá pero ahora mismo no tiene ningún bus en "
                    + "camino a esta parada.";
        }
        if (resultado.lineasQuePasan().isEmpty()) {
            return "No hay arribos y tampoco sabemos qué líneas pasan por esta parada (puede que el "
                    + "código no exista, o que el listado de líneas todavía no se haya importado). "
                    + "Verificá el código con buscar_paradas antes de sacar conclusiones.";
        }
        return """
                No viene ningún ómnibus en este momento, pero la parada existe y las líneas de \
                lineasQuePasan sí paran acá. Suele ser horario nocturno, un domingo, o que las \
                unidades todavía no salieron. Decíselo así al usuario: no le digas que la parada \
                no sirve. Ofrecele que le avise cuál de esas líneas le interesa, o buscar una \
                parada cercana con más frecuencia.""";
    }

    @McpTool(name = "como_llego",
            description = """
                    Dice qué líneas de ómnibus llevan de un lugar a otro de Montevideo, con o sin \
                    transbordo. Los dos lugares se escriben como direcciones o cruces de calles \
                    ("gabriel pereira y berro"). Devuelve, para cada opción, qué línea tomar, en \
                    qué parada subir, en qué parada bajar y cuánto hay que caminar.

                    Usá esta tool y NO deduzcas vos el viaje cruzando las líneas de dos paradas: \
                    que una línea aparezca en las dos NO significa que lleve de una a la otra. La \
                    62 pasa por Gabriel Pereira y por 18 de Julio, pero en sentidos opuestos, y \
                    ningún viaje une esas dos paradas. Esta tool mira el orden real de las \
                    paradas dentro de cada recorrido.

                    Responde recorridos, no horarios: para saber cuándo pasa el próximo, usá \
                    consultar_arribos con la parada de subida.""")
    public ResultadoViaje comoLlego(
            @McpToolParam(description = "Dirección o cruce de calles de origen", required = true)
            String origen,
            @McpToolParam(description = "Dirección o cruce de calles de destino", required = true)
            String destino) {

        final Optional<Coordenada> desde = viajeService.ubicar(origen);
        final Optional<Coordenada> hasta = viajeService.ubicar(destino);

        if (desde.isEmpty() || hasta.isEmpty()) {
            final String cual = desde.isEmpty() ? origen : destino;
            return new ResultadoViaje(origen, destino, List.of(),
                    "No se pudo ubicar \"" + cual + "\" en Montevideo. Verificalo con buscar_paradas "
                            + "y volvé a intentar con una dirección que exista, o pedile al usuario "
                            + "que la aclare.");
        }

        final List<Viaje> viajes = viajeService.comoLlegar(desde.get(), hasta.get());
        final List<OpcionDeViaje> opciones = viajes.stream()
                .map(v -> new OpcionDeViaje(
                        v.lineas(),
                        v.transbordos(),
                        v.tramos().stream()
                                .map(t -> new TramoDeViaje(t.linea(), t.subida().codigo(),
                                        t.subida().descripcion(), t.bajada().codigo(),
                                        t.bajada().descripcion()))
                                .toList(),
                        v.metrosCaminando()))
                .toList();

        return new ResultadoViaje(origen, destino, opciones, contextoDeViaje(opciones));
    }

    private static String contextoDeViaje(List<OpcionDeViaje> opciones) {
        if (opciones.isEmpty()) {
            return """
                    No se encontró ninguna forma de hacer ese viaje, ni directa ni con un \
                    transbordo. Puede que haga falta más de un cambio, o que alguna de las dos \
                    puntas no tenga paradas cerca. Decíselo con honestidad al usuario en vez de \
                    inventar un recorrido, y ofrecele probar con una esquina cercana.""";
        }
        if (opciones.getFirst().transbordos() == 0) {
            return """
                    Hay viaje directo. Cada opción dice en qué parada subir y en cuál bajar: \
                    dáselas al usuario, porque la parada de bajada muchas veces NO es la esquina \
                    exacta que pidió sino una cercana. "metrosCaminando" es todo lo que camina \
                    sumado. Después podés usar consultar_arribos con la parada de subida para \
                    decirle cuándo pasa el próximo.""";
        }
        return """
                No hay viaje directo: estas opciones tienen transbordo. Explicale bien las dos \
                partes (en qué parada baja de la primera línea y dónde toma la segunda), porque \
                el transbordo suele implicar caminar hasta otra parada. Si le parece mucho, \
                ofrecele buscar desde otra esquina.""";
    }

    @McpTool(name = "horarios_teoricos",
            description = """
                    Devuelve los horarios teóricos (programados) a los que una línea pasa por una \
                    parada, agrupados por tipo de día: hábiles, sábados y domingos. Salen del GTFS \
                    oficial, no del tiempo real: usalos cuando consultar_arribos no muestre la \
                    línea (de noche, domingos, o unidades que aún no salieron) o cuando el usuario \
                    planifique para más adelante ("¿a qué hora pasa la primera 185 el sábado?").

                    Los horarios después de medianoche pertenecen al día de servicio anterior: la \
                    "00:30" que figura entre los sábados es la trasnoche del sábado, o sea la \
                    madrugada del domingo. La parada y la línea salen de buscar_paradas y \
                    consultar_arribos.""")
    public RespuestaHorarios horariosTeoricos(
            @McpToolParam(description = "Código de la parada", required = true) long codigoParada,
            @McpToolParam(description = "Línea de ómnibus, como \"185\" o \"CE1\"", required = true)
            String linea) {

        final HorariosDeLinea horarios = horarioTeoricoService.horariosDe(codigoParada, linea);
        final List<HorariosDeDia> porDia = java.util.Arrays.stream(TipoDia.values())
                .filter(dia -> horarios.minutosPorDia().containsKey(dia))
                .map(dia -> new HorariosDeDia(dia.name(),
                        horarios.minutosPorDia().get(dia).stream().map(TransporteMcpTools::aHora).toList()))
                .toList();

        return new RespuestaHorarios(codigoParada, horarios.linea(), porDia, contextoDeHorarios(porDia));
    }

    /** "HH:mm". Los minutos de trasnoche (>= 1440) vuelven a empezar: 1470 es "00:30". */
    private static String aHora(int minuto) {
        return String.format("%02d:%02d", (minuto / 60) % 24, minuto % 60);
    }

    private static String contextoDeHorarios(List<HorariosDeDia> porDia) {
        if (porDia.isEmpty()) {
            return "No hay horarios para esa línea en esa parada. O la línea no pasa por ahí "
                    + "(verificalo con consultar_arribos, que lista las líneas de la parada), o los "
                    + "horarios todavía no se importaron. No inventes horarios: decile al usuario "
                    + "que no los tenés.";
        }
        return """
                Horarios programados, no tiempo real: el bondi puede pasar unos minutos antes o \
                después. Están ordenados dentro de cada tipo de día, y los que aparecen al final \
                con horas chicas (00:xx) son la trasnoche de ese día de servicio. Los feriados no \
                están modelados: suelen funcionar con horario de domingo. Para saber dónde viene \
                el próximo ahora mismo, usá consultar_arribos.""";
    }

    /** Parada devuelta por la búsqueda. */
    public record ParadaEncontrada(long codigo, String descripcion, Double latitud, Double longitud) {
    }

    /** Los horarios de un tipo de día, ya formateados como "HH:mm". */
    public record HorariosDeDia(String tipoDia, List<String> horas) {

        public HorariosDeDia {
            horas = List.copyOf(horas);
        }
    }

    /** Resultado de horarios_teoricos. */
    public record RespuestaHorarios(long codigoParada, String linea, List<HorariosDeDia> porDia,
            String contexto) {

        public RespuestaHorarios {
            porDia = List.copyOf(porDia);
        }
    }

    /** Un tramo arriba de una línea. */
    public record TramoDeViaje(String linea, long codigoSubida, String paradaSubida,
            long codigoBajada, String paradaBajada) {
    }

    /** Una forma de hacer el viaje. */
    public record OpcionDeViaje(List<String> lineas, int transbordos, List<TramoDeViaje> tramos,
            int metrosCaminando) {

        public OpcionDeViaje {
            lineas = List.copyOf(lineas);
            tramos = List.copyOf(tramos);
        }
    }

    /** Resultado de como_llego. */
    public record ResultadoViaje(String origen, String destino, List<OpcionDeViaje> opciones,
            String contexto) {

        public ResultadoViaje {
            opciones = List.copyOf(opciones);
        }
    }

    /**
     * Resultado de buscar_paradas.
     *
     * <p>{@code contexto} no es decorativo: es lo que le permite al LLM distinguir una coincidencia
     * exacta de una aproximación y repreguntar en vez de inventar.
     */
    public record ResultadoDeBusqueda(String consulta, List<ParadaEncontrada> paradas,
            List<ParadaProxima> cercanasAlPunto, String contexto) {

        public ResultadoDeBusqueda {
            paradas = List.copyOf(paradas);
            cercanasAlPunto = List.copyOf(cercanasAlPunto);
        }
    }

    /** Parada con su distancia a un punto de referencia. */
    public record ParadaProxima(long codigo, String descripcion, int distanciaMetros,
            String distanciaLegible) {
    }

    /** Resultado de paradas_cercanas. */
    public record ResultadoCercanas(List<ParadaProxima> paradas, String contexto) {

        public ResultadoCercanas {
            paradas = List.copyOf(paradas);
        }
    }

    /** Próximo ómnibus en llegar. */
    public record ProximoArribo(String linea, String destino, long esperaEnMinutos,
            Integer distanciaMetros, String empresa) {
    }

    /**
     * Resultado de consultar_arribos. {@code error} viene null cuando salió todo bien.
     *
     * <p>{@code lineasQuePasan} está siempre, aunque {@code arribos} venga vacío.
     */
    public record RespuestaArribos(long codigoParada, String descripcion,
            List<ProximoArribo> arribos, List<String> lineasQuePasan,
            String contexto, String error) {

        public RespuestaArribos {
            arribos = List.copyOf(arribos);
            lineasQuePasan = List.copyOf(lineasQuePasan);
        }
    }
}
