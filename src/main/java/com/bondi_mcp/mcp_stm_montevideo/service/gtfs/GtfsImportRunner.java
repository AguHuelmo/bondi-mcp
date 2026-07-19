package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import java.time.Duration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dispara la importación del GTFS al arrancar y la re-chequea una vez por día.
 *
 * <p>En background a propósito: bajar y parsear el GTFS lleva su tiempo y no debe demorar el
 * arranque ni el primer request. Si falla, la app sigue viva y se reintenta en la próxima
 * pasada. El chequeo diario existe porque una app que queda semanas levantada no debería
 * quedarse con recorridos viejos hasta que alguien la reinicie: la Intendencia publica
 * versiones nuevas cada tanto y el chequeo de versión es una llamada baratísima.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GtfsImportRunner implements ApplicationRunner {

    private static final Duration ENTRE_CHEQUEOS = Duration.ofHours(24);

    private final GtfsImportService importService;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("gtfs-import").start(this::importarPeriodicamente);
    }

    private void importarPeriodicamente() {
        while (true) {
            try {
                importService.importarSiHaceFalta();
            }
            catch (RuntimeException ex) {
                log.warn("No se pudo importar el GTFS; se reintenta en el próximo chequeo diario.", ex);
            }
            try {
                Thread.sleep(ENTRE_CHEQUEOS);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
