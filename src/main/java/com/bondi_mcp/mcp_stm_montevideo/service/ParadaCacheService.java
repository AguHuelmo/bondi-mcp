package com.bondi_mcp.mcp_stm_montevideo.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoClient;
import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.config.ParadasProperties;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaEntity;
import com.bondi_mcp.mcp_stm_montevideo.persistence.ParadaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mantiene el caché local de paradas.
 *
 * <p>Se llena de forma perezosa: la primera búsqueda dispara la carga, y a partir de ahí se
 * refresca cuando vence el TTL. Si el refresco falla pero hay datos viejos, se sirven los viejos
 * antes que fallar — una parada de ómnibus no se muda, así que un caché vencido sigue siendo
 * mucho mejor que un error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParadaCacheService {

    /** Clave del advisory lock del refresco. Arbitraria; solo tiene que ser única en la base. */
    private static final long LOCK_REFRESCO = 727_431_954L;

    private final TransportePublicoClient client;
    private final ParadaRepository repository;
    private final ParadasProperties properties;

    /** Garantiza que el caché tenga datos utilizables antes de una búsqueda. */
    @Transactional
    public void asegurarCacheFresco() {
        if (estaFresco()) {
            return;
        }
        // Refresca uno solo; los demás esperan en el lock. Como el lock se suelta al commit de
        // quien refrescó, el re-chequeo de acá abajo ya ve el caché nuevo y no repite el trabajo.
        repository.bloquearRefresco(LOCK_REFRESCO);
        if (estaFresco()) {
            return;
        }
        try {
            refrescar();
        }
        catch (TransportePublicoException ex) {
            if (repository.count() == 0) {
                throw ex;
            }
            log.warn("No se pudo refrescar el caché de paradas; se sigue usando el anterior. Causa: {}",
                    ex.getMessage());
        }
    }

    /** Baja el listado completo de paradas y reemplaza el caché. */
    @Transactional
    public int refrescar() {
        final List<Parada> paradas = client.obtenerTodasLasParadas();
        if (paradas.isEmpty()) {
            // Nunca vaciar el caché por una respuesta vacía: casi seguro es un problema del lado
            // de la API, no que Montevideo se haya quedado sin paradas.
            log.warn("La API devolvió cero paradas; se conserva el caché actual");
            return 0;
        }

        final Instant ahora = Instant.now();
        final List<ParadaEntity> entidades = paradas.stream()
                .map(parada -> ParadaEntity.desde(parada, textoDeBusqueda(parada), ahora))
                .toList();

        repository.deleteAllInBatch();
        repository.saveAll(entidades);
        log.info("Caché de paradas refrescado: {} paradas", entidades.size());
        return entidades.size();
    }

    private boolean estaFresco() {
        final Optional<Instant> ultima = repository.ultimaActualizacion();
        return ultima.isPresent() && vigente(ultima.get(), properties.cacheTtl());
    }

    private static boolean vigente(Instant actualizado, Duration ttl) {
        return actualizado.plus(ttl).isAfter(Instant.now());
    }

    /**
     * Texto indexado: código + descripción normalizada.
     *
     * <p>Se indexa la descripción completa ("CORUNA Y PURIFICACION"), con el conector incluido,
     * y no las calles sueltas: así una búsqueda tal como la escribe la gente ("18 de julio y
     * ejido") matchea palabra por palabra.
     */
    private static String textoDeBusqueda(Parada parada) {
        return parada.codigo() + " " + TextoNormalizador.normalizar(parada.descripcion());
    }
}
