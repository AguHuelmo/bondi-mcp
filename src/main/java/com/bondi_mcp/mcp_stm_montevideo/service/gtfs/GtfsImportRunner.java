package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dispara la importación del GTFS al arrancar, en un hilo aparte.
 *
 * <p>En background a propósito: bajar y parsear el GTFS lleva su tiempo y no debe demorar el
 * arranque ni el primer request. Si falla, la app sigue viva y solo se pierde el listado de
 * "líneas que pasan"; los arribos y la búsqueda no dependen de esto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GtfsImportRunner implements ApplicationRunner {

    private final GtfsImportService importService;

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("gtfs-import").start(() -> {
            try {
                importService.importarSiHaceFalta();
            }
            catch (RuntimeException ex) {
                log.warn("No se pudo importar el GTFS; el listado de líneas por parada queda "
                        + "sin datos hasta el próximo arranque.", ex);
            }
        });
    }
}
