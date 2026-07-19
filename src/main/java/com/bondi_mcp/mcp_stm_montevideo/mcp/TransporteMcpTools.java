package com.bondi_mcp.mcp_stm_montevideo.mcp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.HorariosDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.PuntoDeReferencia;
import com.bondi_mcp.mcp_stm_montevideo.domain.RecorridoDeLinea;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.TipoDia;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;
import com.bondi_mcp.mcp_stm_montevideo.domain.Conectividad;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.BusEnVivoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ConectividadService;
import com.bondi_mcp.mcp_stm_montevideo.service.HorarioTeoricoService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;
import com.bondi_mcp.mcp_stm_montevideo.service.RecorridoService;
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

    /**
     * Contexto cuando ni siquiera hay caché local de paradas para responder.
     *
     * <p>Solo puede pasar en el primer arranque con la API de la Intendencia caída: después de la
     * primera carga siempre se responde con el caché, aunque esté vencido. Igual que en los
     * arribos, la falla externa se devuelve como dato y no como excepción, para que el agente la
     * explique en vez de cortar la conversación.
     */
    private static final String SIN_DATOS_DE_PARADAS =
            "No se pudo responder: la API de la Intendencia no contestó y todavía no hay un caché "
                    + "local de paradas con el que salir del paso. Es una falla temporal del "
                    + "servicio, no un error del usuario: decíselo y ofrecele reintentar en unos "
                    + "minutos.";

    private final ParadaService paradaService;
    private final ArriboService arriboService;
    private final ViajeService viajeService;
    private final HorarioTeoricoService horarioTeoricoService;
    private final RecorridoService recorridoService;
    private final BusEnVivoService busEnVivoService;
    private final ConectividadService conectividadService;

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

        final ResultadoBusqueda resultado;
        try {
            resultado = paradaService.buscar(consulta);
        }
        catch (TransportePublicoException ex) {
            log.warn("Falló la búsqueda de paradas '{}': {}", consulta, ex.getMessage());
            return new ResultadoDeBusqueda(consulta, List.of(), List.of(), null, SIN_DATOS_DE_PARADAS);
        }

        final List<ParadaEncontrada> paradas = resultado.paradas().stream()
                .map(p -> new ParadaEncontrada(p.codigo(), p.descripcion(),
                        p.ubicacion() == null ? null : p.ubicacion().latitud(),
                        p.ubicacion() == null ? null : p.ubicacion().longitud()))
                .toList();

        final List<ParadaProxima> cercanas = resultado.cercanasAlPunto().stream()
                .map(c -> new ParadaProxima(c.parada().codigo(), c.parada().descripcion(),
                        c.distanciaMetros(), c.distanciaLegible()))
                .toList();

        return new ResultadoDeBusqueda(consulta, paradas, cercanas,
                PuntoUbicado.desde(resultado.punto()), contextoDe(resultado));
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
        final List<ParadaProxima> cercanas;
        try {
            cercanas = paradaService
                    .cercanasA(new Coordenada(latitud, longitud), limite).stream()
                    .map(c -> new ParadaProxima(c.parada().codigo(), c.parada().descripcion(),
                            c.distanciaMetros(), c.distanciaLegible()))
                    .toList();
        }
        catch (TransportePublicoException ex) {
            log.warn("Fallaron las paradas cercanas a ({}, {}): {}", latitud, longitud, ex.getMessage());
            return new ResultadoCercanas(List.of(), SIN_DATOS_DE_PARADAS);
        }

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
                        No hay ninguna parada en ese cruce, pero SÍ pudimos ubicar dónde queda \
                        (la coordenada estimada va en "puntoUbicado"), y "cercanasAlPunto" trae \
                        las más próximas con su distancia real. Este es el caso más útil: decile \
                        al usuario que en esa esquina no hay parada y ofrecele la más cercana con \
                        la distancia ("la más cerca está a 150 m, en tal cruce"). Ojo: el cruce \
                        está estimado a partir de las paradas de cada calle, así que las \
                        distancias son aproximadas. Las de "paradas" son solo coincidencias \
                        parciales de texto, mucho menos relevantes: guiate por cercanasAlPunto.""";
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

        final String descripcion = descripcionDe(codigoParada);

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

    /**
     * Nombre legible de la parada, sin que un caché todavía vacío voltee la consulta entera.
     *
     * <p>Buscar la descripción puede disparar la primera carga del caché, y si en ese momento la
     * API está caída tiraría excepción antes de llegar al try de los arribos. El nombre es un
     * adorno: si no está, alcanza con el código.
     */
    private String descripcionDe(long codigoParada) {
        try {
            return paradaService.porCodigo(codigoParada)
                    .map(Parada::descripcion)
                    .orElse("Parada " + codigoParada);
        }
        catch (TransportePublicoException ex) {
            log.warn("No se pudo resolver la descripción de la parada {}: {}", codigoParada, ex.getMessage());
            return "Parada " + codigoParada;
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

        final Optional<Coordenada> desde;
        final Optional<Coordenada> hasta;
        final List<Viaje> viajes;
        try {
            desde = viajeService.ubicar(origen);
            hasta = viajeService.ubicar(destino);

            if (desde.isEmpty() || hasta.isEmpty()) {
                final String cual = desde.isEmpty() ? origen : destino;
                return new ResultadoViaje(origen, destino, List.of(),
                        "No se pudo ubicar \"" + cual + "\" en Montevideo. Verificalo con buscar_paradas "
                                + "y volvé a intentar con una dirección que exista, o pedile al usuario "
                                + "que la aclare.");
            }

            viajes = viajeService.comoLlegar(desde.get(), hasta.get());
        }
        catch (TransportePublicoException ex) {
            log.warn("Falló el viaje '{}' -> '{}': {}", origen, destino, ex.getMessage());
            return new ResultadoViaje(origen, destino, List.of(), SIN_DATOS_DE_PARADAS);
        }
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

    @McpTool(name = "proxima_salida",
            description = """
                    Calcula cuándo sale la próxima vez una línea de una parada según los horarios \
                    teóricos (programados), ya resuelto a fecha y hora concretas y con la espera \
                    en minutos. Usala en vez de deducirlo vos de horarios_teoricos: esta tool ya \
                    resuelve la trasnoche (la "00:30" que figura entre los sábados ocurre el \
                    domingo de madrugada) y el salto al día siguiente cuando hoy no quedan \
                    salidas.

                    Son horarios programados, no tiempo real: si la salida está cerca, confirmá \
                    con consultar_arribos, que dice dónde viene el bus de verdad.""")
    public RespuestaProximaSalida proximaSalida(
            @McpToolParam(description = "Código de la parada", required = true) long codigoParada,
            @McpToolParam(description = "Línea de ómnibus, como \"185\" o \"CE1\"", required = true)
            String linea,
            @McpToolParam(description = "Cuántas salidas devolver; por defecto 3",
                    required = false) Integer cantidad) {

        final int cuantas = (cantidad == null || cantidad < 1) ? 3 : Math.min(cantidad, 10);
        final LocalDateTime ahora = LocalDateTime.now(HorarioTeoricoService.ZONA_MONTEVIDEO);
        final List<SalidaProxima> salidas = horarioTeoricoService
                .proximasSalidas(codigoParada, linea, cuantas).stream()
                .map(salida -> SalidaProxima.desde(salida, ahora))
                .toList();

        return new RespuestaProximaSalida(codigoParada, linea.trim().toUpperCase(Locale.ROOT),
                salidas, contextoDeProximas(salidas));
    }

    private static String contextoDeProximas(List<SalidaProxima> salidas) {
        if (salidas.isEmpty()) {
            return "No hay ninguna salida programada de esa línea en esa parada en los próximos "
                    + "días. O la línea no pasa por ahí (verificalo con consultar_arribos, que "
                    + "lista las líneas de la parada), o los horarios todavía no se importaron. "
                    + "No inventes horarios: decile al usuario que no los tenés.";
        }
        return """
                Salidas programadas, no tiempo real: el bondi puede pasar unos minutos antes o \
                después. "enMinutos" es la espera desde ahora, con la trasnoche y el cambio de \
                día ya resueltos: la fecha manda. Si la primera está a pocos minutos, confirmá \
                con consultar_arribos dónde viene de verdad. Los feriados no están modelados: \
                suelen correr con horario de domingo.""";
    }

    @McpTool(name = "recorrido_de_linea",
            description = """
                    Devuelve el recorrido completo de una línea de ómnibus: todas sus paradas en \
                    orden, un sentido por dirección, cada sentido nombrado por su destino. Sirve \
                    para responder "¿por dónde va la 185?" o "¿la 185 pasa por tal calle?".

                    No responde horarios ni posiciones: para eso están proxima_salida, \
                    horarios_teoricos y buses_en_vivo. Y para saber cómo ir de un punto a otro \
                    usá como_llego, que además elige en qué parada subir y bajar.""")
    public RespuestaRecorrido recorridoDeLinea(
            @McpToolParam(description = "Línea de ómnibus, como \"185\" o \"CE1\"", required = true)
            String linea) {

        final RecorridoDeLinea recorrido = recorridoService.recorridoDe(linea);
        final List<SentidoDeLinea> sentidos = recorrido.sentidos().stream()
                .map(sentido -> new SentidoDeLinea(
                        sentido.destino(),
                        sentido.paradas().size(),
                        sentido.paradas().stream()
                                .map(p -> new ParadaDeRecorrido(p.codigo(), p.descripcion()))
                                .toList()))
                .toList();

        return new RespuestaRecorrido(recorrido.linea(), sentidos, contextoDeRecorrido(sentidos));
    }

    private static String contextoDeRecorrido(List<SentidoDeLinea> sentidos) {
        if (sentidos.isEmpty()) {
            return "No conocemos el recorrido de esa línea. O el nombre no es exacto (probá como "
                    + "figura en el cartel del coche: \"185\", \"CE1\", \"D5\"), o el GTFS todavía "
                    + "no se importó. No inventes el recorrido: decile al usuario que no lo tenés.";
        }
        return """
                Cada sentido muestra la variante más larga de la línea; existen salidas cortas \
                que hacen solo una parte, así que no asumas que todo coche recorre la lista \
                entera. Las paradas van en el orden real en que el bus las toca, y el destino de \
                cada sentido es como la gente lo nombra ("la 185 hacia tal lado"). Para armar un \
                viaje concreto usá como_llego, que mira este mismo orden.""";
    }

    @McpTool(name = "buses_en_vivo",
            description = """
                    Dónde está ahora mismo cada coche de una línea (posición GPS de hace unos \
                    segundos), con ambos sentidos mezclados: por la posición sola no se puede \
                    saber para qué lado va cada uno. Sirve para "¿dónde anda la 185?" a nivel \
                    ciudad. Para saber cuándo llega a UNA parada usá consultar_arribos, que \
                    además trae el tiempo estimado de espera.""")
    public RespuestaBusesEnVivo busesEnVivo(
            @McpToolParam(description = "Línea de ómnibus, como \"185\" o \"CE1\"", required = true)
            String linea) {

        final String normalizada = linea.trim().toUpperCase(Locale.ROOT);
        try {
            final List<BusUbicado> buses = busEnVivoService.deLinea(linea).stream()
                    .filter(bus -> bus.ubicacion() != null)
                    .map(bus -> new BusUbicado(bus.id(), bus.destino(),
                            bus.ubicacion().latitud(), bus.ubicacion().longitud()))
                    .toList();
            return new RespuestaBusesEnVivo(normalizada, buses, contextoDeBuses(buses), null);
        }
        catch (TransportePublicoException ex) {
            log.warn("Fallaron los buses en vivo de la línea {}: {}", linea, ex.getMessage());
            return new RespuestaBusesEnVivo(normalizada, List.of(),
                    "El servicio de tiempo real de la Intendencia no respondió. Es una falla "
                            + "temporal de ellos, no del usuario: decíselo y ofrecele reintentar "
                            + "en un rato.",
                    "No se pudieron obtener los buses: la API de la Intendencia no respondió correctamente.");
        }
    }

    private static String contextoDeBuses(List<BusUbicado> buses) {
        if (buses.isEmpty()) {
            return "Ningún coche de esa línea está transmitiendo posición ahora. Suele ser "
                    + "horario nocturno o que las unidades no salieron; también puede ser que el "
                    + "nombre de la línea no sea exacto. proxima_salida dice cuándo debería salir "
                    + "el próximo según los horarios programados.";
        }
        return """
                Posiciones de hace unos segundos, con ambos sentidos mezclados: NO deduzcas el \
                sentido de un coche por su posición. Si el usuario quiere saber cuándo le llega \
                a él, pedile la parada (o buscala con buscar_paradas) y usá consultar_arribos.""";
    }

    @McpTool(name = "conectividad",
            description = """
                    Mide qué tan bien servida por ómnibus está una dirección de Montevideo, con \
                    un puntaje de 0 a 100 y sus componentes: distancia a la parada más cercana, \
                    líneas distintas, cada cuántos minutos sale un bondi de día, servicio \
                    nocturno y a qué parte de la ciudad se llega sin transbordo.

                    Sirve para preguntas de decisión, no de viaje: "¿me conviene mudarme a X?", \
                    "¿está bien conectado este apartamento?", "¿qué zona tiene mejor transporte?". \
                    Para saber cómo ir de un lado a otro está como_llego.""")
    public RespuestaConectividad conectividad(
            @McpToolParam(description = "Dirección, cruce de calles o lugar a evaluar",
                    required = true) String lugar) {

        final Optional<Coordenada> punto;
        try {
            punto = viajeService.ubicar(lugar);
        }
        catch (TransportePublicoException ex) {
            log.warn("Falló la conectividad de '{}': {}", lugar, ex.getMessage());
            return new RespuestaConectividad(lugar, null, SIN_DATOS_DE_PARADAS);
        }
        if (punto.isEmpty()) {
            return new RespuestaConectividad(lugar, null,
                    "No se pudo ubicar \"" + lugar + "\" en Montevideo. Verificalo con "
                            + "buscar_paradas o pedile al usuario que aclare la dirección.");
        }

        final Conectividad conectividad = conectividadService.medir(punto.get());
        return new RespuestaConectividad(lugar, IndiceDeConectividad.desde(conectividad),
                contextoDeConectividad(conectividad));
    }

    private static String contextoDeConectividad(Conectividad conectividad) {
        if (conectividad.sinParadasCerca()) {
            return "No hay ninguna parada a menos de 400 m de ese punto: para el STM es una zona "
                    + "sin servicio caminable. Decíselo claro al usuario; puede que haya paradas "
                    + "más lejos, que podés explorar con paradas_cercanas.";
        }
        return """
                El puntaje sale de cuatro componentes: cercanía de la parada (0-25), variedad de \
                líneas (0-25), frecuencia diurna (0-30) y alcance sin transbordo (0-20). Es una \
                medida del SERVICIO PROGRAMADO, no del tránsito de hoy. Si esperaMediaDiurna \
                viene null, los horarios aún no se importaron y el puntaje está incompleto: \
                decilo. Las distancias son en línea recta. Presentale al usuario el nivel y dos \
                o tres datos concretos, no la lista entera de números.""";
    }

    /** Parada devuelta por la búsqueda. */
    public record ParadaEncontrada(long codigo, String descripcion, Double latitud, Double longitud) {
    }

    /** El índice de conectividad, con los datos que justifican el puntaje. */
    public record IndiceDeConectividad(int puntaje, String nivel, int paradasCercanas,
            Integer metrosALaParadaMasCercana, List<String> lineas, int salidasSemanales,
            Integer esperaMediaDiurnaMinutos, int salidasNocturnasSemanales,
            long paradasAlcanzablesSinTransbordo, int porcentajeDeLaCiudadAlcanzable) {

        public IndiceDeConectividad {
            lineas = List.copyOf(lineas);
        }

        static IndiceDeConectividad desde(Conectividad conectividad) {
            return new IndiceDeConectividad(
                    conectividad.puntaje(),
                    conectividad.nivel(),
                    conectividad.paradasCercanas(),
                    conectividad.metrosALaParadaMasCercana(),
                    conectividad.lineas(),
                    conectividad.salidasSemanales(),
                    conectividad.esperaMediaDiurnaMinutos(),
                    conectividad.salidasNocturnasSemanales(),
                    conectividad.paradasAlcanzables(),
                    conectividad.porcentajeAlcanzable());
        }
    }

    /** Resultado de conectividad. {@code indice} viene null si el lugar no se pudo ubicar. */
    public record RespuestaConectividad(String lugar, IndiceDeConectividad indice, String contexto) {
    }

    /** Una salida programada ya resuelta a fecha y hora reales. */
    public record SalidaProxima(String fecha, String hora, long enMinutos, String tipoDia) {

        static SalidaProxima desde(SalidaTeorica salida, LocalDateTime ahora) {
            return new SalidaProxima(
                    salida.momento().toLocalDate().toString(),
                    "%02d:%02d".formatted(salida.momento().getHour(), salida.momento().getMinute()),
                    Math.max(0, Duration.between(ahora, salida.momento()).toMinutes()),
                    salida.tipoDia().name());
        }
    }

    /** Resultado de proxima_salida. */
    public record RespuestaProximaSalida(long codigoParada, String linea,
            List<SalidaProxima> salidas, String contexto) {

        public RespuestaProximaSalida {
            salidas = List.copyOf(salidas);
        }
    }

    /** Una parada dentro de un recorrido. Sin coordenadas a propósito: serían cientos de números. */
    public record ParadaDeRecorrido(long codigo, String descripcion) {
    }

    /** Un sentido del recorrido, nombrado por su destino. */
    public record SentidoDeLinea(String destino, int cantidadParadas, List<ParadaDeRecorrido> paradas) {

        public SentidoDeLinea {
            paradas = List.copyOf(paradas);
        }
    }

    /** Resultado de recorrido_de_linea. */
    public record RespuestaRecorrido(String linea, List<SentidoDeLinea> sentidos, String contexto) {

        public RespuestaRecorrido {
            sentidos = List.copyOf(sentidos);
        }
    }

    /** Un coche en la calle, con su posición de hace unos segundos. */
    public record BusUbicado(Integer id, String destino, double latitud, double longitud) {
    }

    /** Resultado de buses_en_vivo. {@code error} viene null cuando salió todo bien. */
    public record RespuestaBusesEnVivo(String linea, List<BusUbicado> buses, String contexto,
            String error) {

        public RespuestaBusesEnVivo {
            buses = List.copyOf(buses);
        }
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
            List<ParadaProxima> cercanasAlPunto, PuntoUbicado puntoUbicado, String contexto) {

        public ResultadoDeBusqueda {
            paradas = List.copyOf(paradas);
            cercanasAlPunto = List.copyOf(cercanasAlPunto);
        }
    }

    /**
     * El lugar pedido ya ubicado: la referencia contra la que se midieron las cercanas.
     *
     * <p>Sin esto el agente sabe que hay paradas "a 150 m" pero no de qué punto, y no puede
     * encadenar (por ejemplo, pedir más paradas alrededor con paradas_cercanas). {@code null}
     * cuando no hizo falta ubicar nada o no se pudo.
     */
    public record PuntoUbicado(double latitud, double longitud, String nombre, String tipo) {

        static PuntoUbicado desde(PuntoDeReferencia punto) {
            return punto == null ? null : new PuntoUbicado(
                    punto.coordenada().latitud(),
                    punto.coordenada().longitud(),
                    punto.descripcion(),
                    tipoDe(punto.origen()));
        }

        /** A mano y no con {@code name()}: el nombre del enum es interno y puede cambiar. */
        private static String tipoDe(PuntoDeReferencia.Origen origen) {
            return switch (origen) {
                case DIRECCION_OFICIAL -> "DIRECCION";
                case LUGAR_CONOCIDO -> "LUGAR";
                case CRUCE_ESTIMADO -> "CRUCE";
            };
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
