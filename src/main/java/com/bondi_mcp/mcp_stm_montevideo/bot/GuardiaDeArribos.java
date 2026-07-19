package com.bondi_mcp.mcp_stm_montevideo.bot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.ArribosDeParada;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Las alertas del bot: "avisame cuando la 185 esté a 5 minutos de la parada 3977".
 *
 * <p>Una guardia revisa los arribos cada tanto y, cuando la línea pedida está a la distancia
 * pedida, le escribe al chat y la alerta se apaga sola. Es la funcionalidad que convierte al
 * bot de "lo consulto" en "me avisa": uno deja de mirar la app parado en la esquina.
 *
 * <p>Las alertas viven en memoria a propósito: duran minutos (alguien esperando un bondi ahora)
 * y tienen vencimiento. Si la app se reinicia se pierden, y volver a pedirla cuesta un mensaje.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardiaDeArribos {

    static final int UMBRAL_POR_DEFECTO = 5;
    private static final int UMBRAL_MAXIMO = 30;

    /** Tope de alertas simultáneas por chat: esto es un despertador, no una flota. */
    private static final int MAXIMAS_POR_CHAT = 3;

    /** Cuánto vive una alerta sin cumplirse. Nadie espera un bondi más que esto. */
    private static final Duration VIDA_MAXIMA = Duration.ofMinutes(45);

    /** Cada cuánto revisa la guardia. Los arribos cambian minuto a minuto; 30 s alcanza. */
    private static final long INTERVALO_MS = 30_000;

    private final ParadaService paradaService;
    private final ArriboService arriboService;
    private final Mensajero mensajero;

    private final List<Alerta> alertas = new CopyOnWriteArrayList<>();
    private final AtomicBoolean vigilando = new AtomicBoolean(false);
    private volatile boolean activo = true;

    /**
     * Crea una alerta y devuelve el texto para el chat.
     *
     * <p>Valida contra nuestra base que la parada exista y que la línea pase por ahí: es el
     * momento de decirle al usuario que se equivocó, no media hora después en silencio. Y si el
     * bondi YA está a tiro, avisa directo y no crea nada.
     */
    public String crear(Charla charla, long codigoParada, String linea, Integer minutosPedidos) {
        final int minutos = minutosPedidos == null
                ? UMBRAL_POR_DEFECTO
                : Math.clamp(minutosPedidos, 1, UMBRAL_MAXIMO);
        final String nombreLinea = linea.trim().toUpperCase(Locale.ROOT);

        final Optional<Parada> parada = paradaService.porCodigo(codigoParada);
        if (parada.isEmpty()) {
            return "No encontré la parada " + codigoParada
                    + ". El código está en el cartel del refugio.";
        }

        final List<String> lineasDeLaParada = arriboService.lineasQuePasan(codigoParada);
        if (!lineasDeLaParada.isEmpty() && !lineasDeLaParada.contains(nombreLinea)) {
            return "La " + nombreLinea + " no pasa por la parada " + codigoParada
                    + ". Por ahí pasan: " + String.join(", ", lineasDeLaParada);
        }

        // Si ya viene a tiro, el aviso es ahora: crear una alerta sería avisar tarde.
        final Optional<Arribo> yaCerca = arriboCercano(codigoParada, nombreLinea, minutos);
        if (yaCerca.isPresent()) {
            return mensajeDeLlegada(nombreLinea, parada.get().descripcion(), codigoParada, yaCerca.get());
        }

        // Pedir de nuevo la misma alerta actualiza el umbral en vez de duplicarla.
        alertas.removeIf(alerta -> alerta.charla().equals(charla)
                && alerta.codigoParada() == codigoParada && alerta.linea().equals(nombreLinea));
        if (alertas.stream().filter(alerta -> alerta.charla().equals(charla)).count() >= MAXIMAS_POR_CHAT) {
            return "Ya tenés " + MAXIMAS_POR_CHAT + " alertas activas. Mandá \"cancelar\" para "
                    + "cortarlas, o esperá a que se cumplan.";
        }

        alertas.add(new Alerta(charla, codigoParada, parada.get().descripcion(), nombreLinea,
                minutos, Instant.now()));
        asegurarVigilancia();
        return "🔔 Listo. Te aviso cuando la " + nombreLinea + " esté a " + minutos
                + " min o menos de " + parada.get().descripcion() + " (parada " + codigoParada
                + "). Vigilo hasta 45 minutos; con \"cancelar\" la cortás antes.";
    }

    /** Las alertas activas de un chat, listas para mostrar. */
    public String listar(Charla charla) {
        final List<Alerta> mias = alertas.stream()
                .filter(alerta -> alerta.charla().equals(charla))
                .toList();
        if (mias.isEmpty()) {
            return "No tenés alertas activas. Creá una con: avisame 3977 185";
        }
        return "Tus alertas:\n"
                + mias.stream()
                        .map(alerta -> "🔔 " + alerta.linea() + " a " + alerta.minutos()
                                + " min o menos de " + alerta.descripcionParada()
                                + " (parada " + alerta.codigoParada() + ")")
                        .collect(Collectors.joining("\n"))
                + "\n\nPara borrarlas todas: cancelar";
    }

    /** Corta todas las alertas de un chat. */
    public String cancelar(Charla charla) {
        final boolean habia = alertas.removeIf(alerta -> alerta.charla().equals(charla));
        return habia ? "Listo, corté todas tus alertas." : "No tenías ninguna alerta activa.";
    }

    /**
     * Una pasada de la guardia: vence lo viejo y avisa lo que ya está cerca.
     *
     * <p>Agrupa por parada para pagar UNA consulta a la Intendencia por parada vigilada, tenga
     * una alerta o diez. Si la API falla, las alertas quedan vivas y se reintenta en la próxima
     * pasada: una caída de treinta segundos no puede costarle el aviso a nadie.
     */
    void revisar(Instant ahora) {
        final Map<Long, List<Alerta>> porParada = List.copyOf(alertas).stream()
                .collect(Collectors.groupingBy(Alerta::codigoParada));

        porParada.forEach((codigoParada, deLaParada) -> {
            final List<Alerta> vigentes = new ArrayList<>();
            for (final Alerta alerta : deLaParada) {
                if (Duration.between(alerta.creadaEn(), ahora).compareTo(VIDA_MAXIMA) >= 0) {
                    terminar(alerta, "⏳ Corté la guardia de la " + alerta.linea()
                            + " en la parada " + alerta.codigoParada() + ": pasaron 45 minutos y "
                            + "nunca estuvo a " + alerta.minutos() + " min. Si seguís esperando, "
                            + "pedímela de nuevo.");
                }
                else {
                    vigentes.add(alerta);
                }
            }
            if (vigentes.isEmpty()) {
                return;
            }

            final ArribosDeParada arribos;
            try {
                arribos = arriboService.proximosArribos(codigoParada);
            }
            catch (TransportePublicoException ex) {
                log.debug("La guardia no pudo consultar la parada {}: {}", codigoParada, ex.getMessage());
                return;
            }

            for (final Alerta alerta : vigentes) {
                arribos.arribos().stream()
                        .filter(arribo -> arribo.linea().equalsIgnoreCase(alerta.linea()))
                        .filter(arribo -> arribo.esperaEnMinutos() <= alerta.minutos())
                        .findFirst()
                        .ifPresent(arribo -> terminar(alerta, mensajeDeLlegada(alerta.linea(),
                                alerta.descripcionParada(), alerta.codigoParada(), arribo)));
            }
        });
    }

    private Optional<Arribo> arriboCercano(long codigoParada, String linea, int minutos) {
        try {
            return arriboService.proximosArribos(codigoParada).arribos().stream()
                    .filter(arribo -> arribo.linea().equalsIgnoreCase(linea))
                    .filter(arribo -> arribo.esperaEnMinutos() <= minutos)
                    .findFirst();
        }
        catch (TransportePublicoException ex) {
            // Sin tiempo real igual se puede dejar la guardia armada; ya reintentará ella.
            log.debug("No se pudo mirar la parada {} al crear la alerta: {}", codigoParada, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String mensajeDeLlegada(String linea, String descripcionParada,
            long codigoParada, Arribo arribo) {
        final String espera = arribo.esperaEnMinutos() <= 0
                ? "está llegando"
                : "está a " + arribo.esperaEnMinutos() + " min";
        final String distancia = arribo.distanciaMetros() == null
                ? ""
                : " (a " + arribo.distanciaMetros() + " m)";
        return "🚨 ¡Ahí viene! La " + linea + " " + espera + distancia + " de "
                + descripcionParada + ", parada " + codigoParada + ". ¡Salí!";
    }

    /** Termina una alerta: se borra primero y se avisa después, para no avisar dos veces. */
    private void terminar(Alerta alerta, String mensaje) {
        if (!alertas.remove(alerta)) {
            return;
        }
        try {
            mensajero.enviar(alerta.charla(), mensaje);
        }
        catch (RuntimeException ex) {
            log.warn("No se pudo avisar a la charla {}: {}", alerta.charla(), ex.getMessage());
        }
    }

    /** Arranca el hilo de la guardia la primera vez que alguien crea una alerta. */
    private void asegurarVigilancia() {
        if (vigilando.compareAndSet(false, true)) {
            Thread.ofVirtual().name("guardia-arribos").start(this::vigilar);
        }
    }

    private void vigilar() {
        log.info("Guardia de arribos en marcha (revisa cada {} s)", INTERVALO_MS / 1000);
        while (activo) {
            try {
                Thread.sleep(INTERVALO_MS);
                if (!alertas.isEmpty()) {
                    revisar(Instant.now());
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            catch (RuntimeException ex) {
                // La guardia no se muere por una pasada fallida: hay gente esperando el aviso.
                log.warn("Falló una pasada de la guardia de arribos: {}", ex.getMessage());
            }
        }
    }

    @PreDestroy
    void detener() {
        activo = false;
    }

    /** Una alerta activa: a qué charla avisarle, de qué línea en qué parada, y desde cuándo. */
    record Alerta(Charla charla, long codigoParada, String descripcionParada, String linea,
            int minutos, Instant creadaEn) {
    }
}
