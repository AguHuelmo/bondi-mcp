package com.bondi_mcp.mcp_stm_montevideo.mcp;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;
import com.bondi_mcp.mcp_stm_montevideo.domain.Arribo;
import com.bondi_mcp.mcp_stm_montevideo.domain.Parada;
import com.bondi_mcp.mcp_stm_montevideo.service.ArriboService;
import com.bondi_mcp.mcp_stm_montevideo.service.ParadaService;

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

    @McpTool(name = "buscar_paradas",
            description = """
                    Busca paradas de ómnibus de Montevideo por dirección o cruce de calles \
                    (por ejemplo "18 de julio y ejido") o por código de parada. Devuelve las \
                    paradas candidatas con su código, que sirve para consultar_arribos. \
                    No admite búsqueda por número de línea.""")
    public List<ParadaEncontrada> buscarParadas(
            @McpToolParam(description = "Dirección, cruce de calles o código de parada",
                    required = true) String consulta) {

        final List<Parada> paradas = paradaService.buscar(consulta);
        return paradas.stream()
                .map(p -> new ParadaEncontrada(p.codigo(), p.descripcion(),
                        p.ubicacion() == null ? null : p.ubicacion().latitud(),
                        p.ubicacion() == null ? null : p.ubicacion().longitud()))
                .toList();
    }

    @McpTool(name = "consultar_arribos",
            description = """
                    Devuelve los próximos ómnibus que llegan a una parada, con la línea, el \
                    destino y el tiempo estimado de espera en minutos. El código de parada se \
                    obtiene con buscar_paradas.""")
    public ArribosDeParada consultarArribos(
            @McpToolParam(description = "Código de la parada", required = true) long codigoParada) {

        final String descripcion = paradaService.porCodigo(codigoParada)
                .map(Parada::descripcion)
                .orElse("Parada " + codigoParada);

        try {
            final List<Arribo> arribos = arriboService.proximosArribos(codigoParada);
            final List<ProximoArribo> proximos = arribos.stream()
                    .map(a -> new ProximoArribo(a.linea(), a.destino(), a.esperaEnMinutos(),
                            a.distanciaMetros(), a.empresa()))
                    .toList();
            return new ArribosDeParada(codigoParada, descripcion, proximos, null);
        }
        catch (TransportePublicoException ex) {
            // Un fallo de la API externa se le devuelve al agente como dato, no como excepción:
            // así puede explicarlo en vez de cortar la conversación.
            log.warn("Falló la consulta de arribos de la parada {}: {}", codigoParada, ex.getMessage());
            return new ArribosDeParada(codigoParada, descripcion, List.of(),
                    "No se pudieron obtener los arribos: la API de la Intendencia no respondió correctamente.");
        }
    }

    /** Parada devuelta por la búsqueda. */
    public record ParadaEncontrada(long codigo, String descripcion, Double latitud, Double longitud) {
    }

    /** Próximo ómnibus en llegar. */
    public record ProximoArribo(String linea, String destino, long esperaEnMinutos,
            Integer distanciaMetros, String empresa) {
    }

    /** Resultado de consultar_arribos. {@code error} viene null cuando salió todo bien. */
    public record ArribosDeParada(long codigoParada, String descripcion,
            List<ProximoArribo> arribos, String error) {

        public ArribosDeParada {
            arribos = List.copyOf(arribos);
        }
    }
}
