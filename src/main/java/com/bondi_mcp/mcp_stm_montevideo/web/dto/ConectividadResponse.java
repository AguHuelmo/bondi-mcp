package com.bondi_mcp.mcp_stm_montevideo.web.dto;

import java.util.List;

import com.bondi_mcp.mcp_stm_montevideo.domain.Conectividad;

/**
 * El índice de conectividad tal como lo ve el frontend o un integrador.
 *
 * <p>Pensado para embeberse en una publicación inmobiliaria: el puntaje y el nivel son el
 * titular, el resto son los datos que lo justifican.
 */
public record ConectividadResponse(int puntaje, String nivel, int paradasCercanas,
        Integer metrosALaParadaMasCercana, List<String> lineas, int salidasSemanales,
        Integer esperaMediaDiurnaMinutos, int salidasNocturnasSemanales,
        long paradasAlcanzables, int porcentajeDeLaCiudadAlcanzable) {

    public static ConectividadResponse desde(Conectividad conectividad) {
        return new ConectividadResponse(
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
