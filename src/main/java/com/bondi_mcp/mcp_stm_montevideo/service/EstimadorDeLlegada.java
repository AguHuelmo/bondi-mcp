package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.Coordenada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.domain.PronosticoDeLlegada;
import com.bondi_mcp.mcp_stm_montevideo.domain.SalidaTeorica;
import com.bondi_mcp.mcp_stm_montevideo.domain.Viaje;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Responde "¿llego a tiempo?" cruzando el planificador con el tiempo real.
 *
 * <p>La gracia está en un detalle que las apps de arribos ignoran: no sirve el bondi que pasa
 * ANTES de que llegues caminando a la parada. Acá el primer arribo alcanzable es el primero cuya
 * espera supera la caminata inicial; si el tiempo real no muestra ninguno (de noche, línea sin
 * unidades), se cae con honestidad a los horarios teóricos y el resultado lo dice.
 *
 * <p>Solo pronostica viajes directos. Prometer una hora con transbordo exigiría conocer la
 * espera de la segunda línea en un momento futuro, y eso ya no es estimar: es inventar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstimadorDeLlegada {

    /** Paso urbano tranquilo: 75 m por minuto (4,5 km/h), sobre distancia en línea recta. */
    private static final double METROS_CAMINANDO_POR_MINUTO = 75;

    /** Un bondi urbano promedia ~1,4 minutos por parada del recorrido, semáforos incluidos. */
    private static final double MINUTOS_POR_PARADA = 1.4;

    /** Si no se conoce el tramo, se estima por distancia: ~15 km/h de velocidad comercial. */
    private static final double METROS_EN_BONDI_POR_MINUTO = 250;

    /** Cuántas opciones de viaje evaluar: cada una puede costar una consulta de tiempo real. */
    private static final int MAXIMAS_OPCIONES = 3;

    private final ViajeService viajeService;
    private final ArriboService arriboService;
    private final HorarioTeoricoService horarioTeoricoService;
    private final RecorridoService recorridoService;

    /** Pronostica con el reloj de Montevideo. Vacío si no hay viaje directo evaluable. */
    @Transactional
    public Optional<PronosticoDeLlegada> pronosticar(Coordenada origen, Coordenada destino,
            LocalTime horaObjetivo) {
        return pronosticar(origen, destino, horaObjetivo,
                LocalDateTime.now(HorarioTeoricoService.ZONA_MONTEVIDEO));
    }

    Optional<PronosticoDeLlegada> pronosticar(Coordenada origen, Coordenada destino,
            LocalTime horaObjetivo, LocalDateTime ahora) {
        final LocalDateTime objetivo = ahora.toLocalDate().atTime(horaObjetivo);

        return viajeService.comoLlegar(origen, destino).stream()
                .filter(Viaje::esDirecto)
                .limit(MAXIMAS_OPCIONES)
                .map(viaje -> evaluar(viaje, origen, destino, ahora))
                .flatMap(Optional::stream)
                // Gana la opción que llega más temprano, no la que menos hace caminar.
                .min(Comparator.comparing(Parcial::llegada))
                .map(parcial -> parcial.contra(objetivo));
    }

    /** Evalúa una opción: caminata, primer bondi alcanzable, viaje y caminata final. */
    private Optional<Parcial> evaluar(Viaje viaje, Coordenada origen, Coordenada destino,
            LocalDateTime ahora) {
        final Viaje.Tramo tramo = viaje.tramos().getFirst();
        if (tramo.subida().ubicacion() == null || tramo.bajada().ubicacion() == null) {
            return Optional.empty();
        }

        final int caminataInicial = minutosCaminando(
                Distancias.entre(origen, tramo.subida().ubicacion()));
        final int caminataFinal = minutosCaminando(
                Distancias.entre(tramo.bajada().ubicacion(), destino));

        final Optional<Espera> espera = esperaDelAlcanzable(tramo, caminataInicial, ahora);
        if (espera.isEmpty()) {
            return Optional.empty();
        }

        final int minutosDeViaje = minutosArribaDelBondi(tramo);
        final LocalDateTime llegada = ahora.plusMinutes(
                (long) espera.get().minutos() + minutosDeViaje + caminataFinal);

        return Optional.of(new Parcial(tramo, llegada, caminataInicial, espera.get().minutos(),
                minutosDeViaje, caminataFinal, espera.get().enVivo()));
    }

    /**
     * Cuándo pasa el primer bondi que se ALCANZA: su espera tiene que superar la caminata.
     *
     * <p>Primero el tiempo real; si no hay ningún bus de la línea transmitiendo (o la API está
     * caída), los horarios teóricos. Vacío si tampoco hay salida programada en lo que queda del
     * día: prometer una llegada con el primer bondi de mañana no le sirve a nadie.
     */
    private Optional<Espera> esperaDelAlcanzable(Viaje.Tramo tramo, int caminataInicial,
            LocalDateTime ahora) {
        try {
            final Optional<Arribo> enVivo = arriboService
                    .proximosArribos(tramo.subida().codigo()).arribos().stream()
                    .filter(arribo -> arribo.linea().equalsIgnoreCase(tramo.linea()))
                    .filter(arribo -> arribo.esperaEnMinutos() >= caminataInicial)
                    .findFirst();
            if (enVivo.isPresent()) {
                return Optional.of(new Espera((int) enVivo.get().esperaEnMinutos(), true));
            }
        }
        catch (TransportePublicoException ex) {
            log.debug("Sin tiempo real para la parada {}: {}", tramo.subida().codigo(), ex.getMessage());
        }

        return horarioTeoricoService
                .proximasSalidas(tramo.subida().codigo(), tramo.linea(), 5).stream()
                .filter(salida -> !salida.momento().isBefore(ahora.plusMinutes(caminataInicial)))
                .filter(salida -> salida.momento().toLocalDate().equals(ahora.toLocalDate()))
                .findFirst()
                .map(SalidaTeorica::momento)
                .map(momento -> new Espera((int) Duration.between(ahora, momento).toMinutes(), false));
    }

    /**
     * Minutos arriba del bondi entre subida y bajada.
     *
     * <p>Por cantidad de paradas del tramo real del recorrido; si la línea no une esas paradas
     * en ninguna variante conocida, por distancia en línea recta a velocidad comercial.
     */
    private int minutosArribaDelBondi(Viaje.Tramo tramo) {
        final List<Parada> paradas = recorridoService.tramoDe(
                tramo.linea(), tramo.subida().codigo(), tramo.bajada().codigo());
        if (paradas.size() >= 2) {
            return (int) Math.max(1, Math.round((paradas.size() - 1) * MINUTOS_POR_PARADA));
        }
        final double metros = Distancias.entre(tramo.subida().ubicacion(), tramo.bajada().ubicacion());
        return (int) Math.max(1, Math.round(metros / METROS_EN_BONDI_POR_MINUTO));
    }

    private static int minutosCaminando(double metros) {
        return (int) Math.max(1, Math.ceil(metros / METROS_CAMINANDO_POR_MINUTO));
    }

    /** La espera hasta el bondi alcanzable y de dónde salió el dato. */
    private record Espera(int minutos, boolean enVivo) {
    }

    /** Una opción ya evaluada, a falta de compararla contra la hora objetivo. */
    private record Parcial(Viaje.Tramo tramo, LocalDateTime llegada, int caminataInicial,
            int espera, int viaje, int caminataFinal, boolean enVivo) {

        PronosticoDeLlegada contra(LocalDateTime objetivo) {
            final int margen = (int) Duration.between(llegada, objetivo).toMinutes();
            return new PronosticoDeLlegada(margen >= 0, margen, llegada, tramo.linea(),
                    tramo.subida(), tramo.bajada(), caminataInicial, espera, viaje,
                    caminataFinal, enVivo);
        }
    }
}
