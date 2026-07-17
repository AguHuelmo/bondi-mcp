package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.persistence.GtfsImportEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.GtfsImportRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.HorarioTeoricoDao;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaLineaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaLineaRepository;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoParadaWriter;
import com.bondi_mcp.mcp_stm_montevideo.persistence.RecorridoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Importa del GTFS estático la relación línea↔parada.
 *
 * <p>Solo reimporta cuando cambia la versión publicada: bajar y parsear son ~17 MB y ~2,2 millones
 * de filas, no algo para repetir en cada arranque.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GtfsImportService {

    private final TransportePublicoClient client;
    private final ParadaLineaRepository paradaLineaRepository;
    private final GtfsImportRepository gtfsImportRepository;
    private final RecorridoRepository recorridoRepository;
    private final RecorridoParadaWriter recorridoParadaWriter;
    private final HorarioTeoricoDao horarioTeoricoDao;

    /** Importa si la versión publicada cambió o si nunca se importó. */
    @Transactional
    public void importarSiHaceFalta() {
        final String publicada = client.obtenerVersionGtfs();
        final Optional<String> importada = gtfsImportRepository.findById(GtfsImportEntity.FILA_UNICA)
                .map(GtfsImportEntity::getVersion);

        // No alcanza con que coincida la versión: si agregamos una tabla nueva que sale del GTFS,
        // una base con la versión ya registrada la dejaría vacía para siempre. Reimportamos
        // también cuando falta alguno de los datos que el GTFS debería haber dejado.
        final boolean versionAlDia = importada.filter(publicada::equals).isPresent();
        final boolean datosCompletos = paradaLineaRepository.count() > 0
                && recorridoRepository.count() > 0
                && horarioTeoricoDao.contar() > 0;
        if (versionAlDia && datosCompletos) {
            log.debug("GTFS al día (versión {})", publicada);
            return;
        }

        log.info("Importando GTFS: versión publicada {}, importada {}",
                publicada, importada.orElse("ninguna"));
        importar(publicada);
    }

    @Transactional
    public void importar(String version) {
        final long inicio = System.currentTimeMillis();
        final DatosGtfs datos = GtfsParser.parsear(client.descargarGtfs());

        final List<ParadaLineaEntity> filas = datos.lineasPorParada().entrySet().stream()
                .flatMap(e -> e.getValue().stream().map(linea -> new ParadaLineaEntity(e.getKey(), linea)))
                .toList();

        paradaLineaRepository.deleteAllInBatch();
        paradaLineaRepository.saveAll(filas);

        // El borrado va antes de insertar; el ON DELETE CASCADE limpia recorrido_parada.
        recorridoParadaWriter.borrarTodo();
        recorridoRepository.deleteAllInBatch();
        int paradasDeRecorrido = 0;
        for (final RecorridoGtfs recorrido : datos.recorridos()) {
            final RecorridoEntity guardado = recorridoRepository.save(
                    new RecorridoEntity(recorrido.linea(), recorrido.direccion()));
            recorridoParadaWriter.insertar(guardado.getId(), recorrido.paradas());
            paradasDeRecorrido += recorrido.paradas().size();
        }

        horarioTeoricoDao.borrarTodo();
        final List<HorarioTeoricoDao.Fila> horarios = datos.horarios().stream()
                .flatMap(h -> h.minutos().stream()
                        .map(minuto -> new HorarioTeoricoDao.Fila(h.parada(), h.linea(), h.tipoDia(), minuto)))
                .toList();
        horarioTeoricoDao.insertar(horarios);

        gtfsImportRepository.save(new GtfsImportEntity(version, Instant.now()));

        log.info("GTFS importado: {} pares parada-línea, {} recorridos con {} paradas, "
                        + "{} horarios teóricos, en {} ms",
                filas.size(), datos.recorridos().size(), paradasDeRecorrido, horarios.size(),
                System.currentTimeMillis() - inicio);
    }
}
