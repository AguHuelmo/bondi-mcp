package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.client.Geocoder;
import com.bondi_mcp.mcp_stm_montevideo.client.GeocoderException;
import com.bondi_mcp.mcp_stm_montevideo.config.ParadasProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.DireccionUbicada;
import com.bondi_mcp.mcp_stm_montevideo.domain.LugarUbicado;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.ParadaCercana;
import com.bondi_mcp.mcp_stm_montevideo.domain.PuntoDeReferencia;
import com.bondi_mcp.mcp_stm_montevideo.domain.ResultadoBusqueda;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;

/**
 * Búsqueda de paradas. Lógica compartida por las tools MCP y los controllers REST.
 *
 * <p>Busca sobre el caché local porque la API de la Intendencia no ofrece búsqueda por texto.
 *
 * <p>La búsqueda es tolerante a propósito: puntúa por palabras coincidentes en vez de exigirlas
 * todas, y cuando el cruce pedido no tiene parada, estima dónde queda y ofrece las más cercanas.
 * Quien busca una parada muchas veces no sabe cómo se escribe la calle ni dónde está la parada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParadaService {

    /** Cuántas paradas ofrecer cuando el lugar buscado no tiene ninguna. */
    private static final int CERCANAS_AL_PUNTO = 5;

    private final ParadaRepository repository;
    private final ParadaCacheService cacheService;
    private final ParadasProperties properties;
    private final Geocoder geocoder;

    /**
     * Busca paradas por dirección con número ("gabriel pereira 2470"), por cruce ("18 de julio y
     * ejido") o por código exacto.
     *
     * <p>Ordena por cuántas palabras coinciden, así que una consulta con una calle equivocada
     * devuelve igual las candidatas de la otra en vez de nada.
     */
    @Transactional
    public ResultadoBusqueda buscar(String consulta) {
        final List<String> palabras = TextoNormalizador.enPalabrasDeBusqueda(consulta);
        if (palabras.isEmpty()) {
            return ResultadoBusqueda.vacio(palabras);
        }

        cacheService.asegurarCacheFresco();

        final String[] patrones = palabras.stream().map(palabra -> "%" + palabra + "%").toArray(String[]::new);
        final List<Parada> paradas = repository
                .buscarPorPatrones(patrones, Limit.of(properties.maxResultadosBusqueda()))
                .stream()
                .map(ParadaEntity::aParada)
                .toList();

        final int exactas = (int) paradas.stream().filter(parada -> matcheaTodas(parada, palabras)).count();
        final ResultadoBusqueda resultado = new ResultadoBusqueda(paradas, palabras, exactas);
        log.debug("Búsqueda '{}' -> {} paradas ({} exactas)", consulta, paradas.size(), exactas);

        // Si no hay ninguna parada en ese lugar, la pregunta útil pasa a ser "¿cuál es la más
        // cerca?".
        if (exactas > 0) {
            return resultado;
        }
        return ubicar(consulta)
                .map(punto -> conParadasDeAlrededor(resultado, punto))
                .orElse(resultado);
    }

    /**
     * Suma al resultado el lugar ubicado y las paradas que tiene alrededor.
     *
     * <p>Cuando sabemos dónde queda el lugar, las coincidencias de texto se van: quien busca
     * "gabriel pereira 2470" quiere la parada más próxima a esa puerta, y no la lista de paradas
     * que se llaman parecido, que pueden estar a treinta cuadras. Vale igual para un lugar
     * conocido, que llega hasta acá solo si su nombre contiene todo lo que se buscó.
     *
     * <p>La excepción es el cruce estimado. Ahí el punto es una heurística que puede errarle (ver
     * {@link EstimadorDeCruce}), y si le erró, esas coincidencias son lo único que le queda al
     * usuario para darse cuenta y corregir.
     */
    private ResultadoBusqueda conParadasDeAlrededor(ResultadoBusqueda resultado, PuntoDeReferencia punto) {
        final List<ParadaCercana> cercanas = cercanasA(punto.coordenada(), CERCANAS_AL_PUNTO);
        if (punto.origen() == PuntoDeReferencia.Origen.CRUCE_ESTIMADO) {
            return resultado.withPunto(punto, cercanas);
        }
        return resultado.soloCercanasA(punto, cercanas);
    }

    /**
     * Ubica el lugar que se pidió, cuando no hay ninguna parada que matchee.
     *
     * <p>Cada forma de consulta se resuelve de una sola manera, y son excluyentes: una dirección
     * con número se geocodifica, una esquina se estima, y lo que no es ninguna de las dos se
     * busca como lugar conocido. Así una esquina que no se pudo estimar no termina preguntando
     * por un punto de interés que se llame parecido, y cada consulta paga a lo sumo una llamada
     * externa.
     */
    private Optional<PuntoDeReferencia> ubicar(String consulta) {
        if (TextoNormalizador.pareceDireccionConNumero(consulta)) {
            return ubicarDireccion(consulta).map(PuntoDeReferencia::deDireccion);
        }
        final Optional<List<String>> calles = TextoNormalizador.partirEnCalles(consulta);
        if (calles.isPresent()) {
            return ubicarCruce(calles.get()).map(PuntoDeReferencia::deCruceEstimado);
        }
        return ubicarLugar(consulta).map(PuntoDeReferencia::deLugar);
    }

    /**
     * Ubica un lugar conocido: el Estadio Centenario, la Terminal Tres Cruces, una facultad.
     *
     * <p>Acá está el guardarraíl, y no es opcional. El servicio de lugares matchea mucho más flojo
     * que el de direcciones y no tiene forma de avisar que está estirando: pedirle "18 de julio"
     * contesta "BROU 19 DE JUNIO", en Minas 1434, con el mismo {@code state} de un acierto. Por
     * eso no alcanza con que conteste algo: se le exige al nombre que devuelve la misma vara que a
     * una parada, que contenga todas las palabras buscadas. Con eso, "18 de julio" descarta al
     * BROU (no dice "JULIO") y "centenario" se queda con el Estadio.
     *
     * <p>Solo se llama cuando ninguna parada matcheó, así que las consultas de siempre no pagan
     * esta llamada.
     */
    private Optional<LugarUbicado> ubicarLugar(String consulta) {
        final List<String> palabras = TextoNormalizador.enPalabrasDeBusqueda(consulta);
        if (palabras.isEmpty()) {
            return Optional.empty();
        }
        try {
            return geocoder.buscarLugares(consulta).stream()
                    .filter(lugar -> contieneTodas(lugar.nombre(), palabras))
                    .findFirst();
        }
        catch (GeocoderException ex) {
            log.warn("No se pudo buscar el lugar '{}': {}", consulta, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ubica una dirección con número contra el padrón oficial.
     *
     * <p>El geocoder es de otro organismo y es un extra: si se cae, la búsqueda por texto sigue
     * respondiendo lo mismo que respondía antes de que existiera. No hay razón para hacer fallar
     * la consulta entera por esto.
     */
    private Optional<DireccionUbicada> ubicarDireccion(String consulta) {
        try {
            return geocoder.ubicar(consulta);
        }
        catch (GeocoderException ex) {
            log.warn("No se pudo geocodificar '{}': {}", consulta, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ubica el cruce que se pidió, usando las paradas de cada calle como referencia.
     *
     * <p>El geocoder no sirve para esto: ignora la segunda calle. Ver {@link EstimadorDeCruce}.
     */
    private Optional<Coordenada> ubicarCruce(List<String> calles) {
        return EstimadorDeCruce.estimar(soloParadasDe(calles.get(0)), soloParadasDe(calles.get(1)));
    }

    /** Candidatas de una sola calle, sin estimar cruces: corta la recursión. */
    private List<Parada> soloParadasDe(String calle) {
        final List<String> palabras = TextoNormalizador.enPalabrasDeBusqueda(calle);
        if (palabras.isEmpty()) {
            return List.of();
        }
        final String[] patrones = palabras.stream().map(p -> "%" + p + "%").toArray(String[]::new);
        return repository.buscarPorPatrones(patrones, Limit.of(properties.maxResultadosBusqueda()))
                .stream()
                .map(ParadaEntity::aParada)
                .filter(parada -> matcheaTodas(parada, palabras))
                .toList();
    }

    /**
     * Paradas más cercanas a un punto, de la más cerca a la más lejos.
     *
     * <p>Distancia en línea recta: la caminando siempre es mayor.
     */
    @Transactional
    public List<ParadaCercana> cercanasA(Coordenada punto, int limite) {
        cacheService.asegurarCacheFresco();
        return repository.cercanasA(punto.latitud(), punto.longitud(), Limit.of(limite)).stream()
                .map(fila -> new ParadaCercana(
                        new Parada(fila.getCodigo(), fila.getCalle(), fila.getEsquina(),
                                new Coordenada(fila.getLatitud(), fila.getLongitud())),
                        (int) Math.round(fila.getDistancia())))
                .toList();
    }

    /** Si la parada contiene todas las palabras buscadas, lo pedido apareció tal cual. */
    private static boolean matcheaTodas(Parada parada, List<String> palabras) {
        return contieneTodas(parada.codigo() + " " + parada.descripcion(), palabras);
    }

    /** La vara para creerle a un nombre: que estén todas las palabras que se buscaron. */
    private static boolean contieneTodas(String texto, List<String> palabras) {
        final String normalizado = TextoNormalizador.normalizar(texto);
        return palabras.stream().allMatch(normalizado::contains);
    }

    /** Una parada por su código, si está en el caché. */
    @Transactional
    public Optional<Parada> porCodigo(long codigo) {
        cacheService.asegurarCacheFresco();
        return repository.findById(codigo).map(ParadaEntity::aParada);
    }
}
