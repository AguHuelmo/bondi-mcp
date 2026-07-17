package com.bondi_mcp.mcp_stm_montevideo.domain;

import java.util.List;

/**
 * Resultado de buscar paradas, con qué tan bien matcheó la consulta.
 *
 * <p>La distinción importa: la búsqueda puntúa por palabras coincidentes, así que también
 * devuelve resultados cuando el usuario le erra a una calle. Quien consuma esto necesita saber
 * si le estamos dando lo que pidió o una aproximación, para no afirmar de más.
 *
 * @param paradas          candidatas, de mejor a peor coincidencia
 * @param palabrasBuscadas palabras normalizadas que se usaron para buscar
 * @param exactas          cuántas de {@code paradas} contienen TODAS las palabras buscadas
 * @param cercanasAlPunto  paradas más cercanas a {@code punto}, cuando no hay ninguna en el lugar
 *                         pedido; vacío si no se pudo ubicar
 * @param punto            dónde queda lo que se pidió, cuando ninguna parada matcheó pero se pudo
 *                         ubicar igual; {@code null} si no se pudo o si no hizo falta
 */
public record ResultadoBusqueda(List<Parada> paradas, List<String> palabrasBuscadas, int exactas,
        List<ParadaCercana> cercanasAlPunto, PuntoDeReferencia punto) {

    public ResultadoBusqueda {
        paradas = List.copyOf(paradas);
        palabrasBuscadas = List.copyOf(palabrasBuscadas);
        cercanasAlPunto = List.copyOf(cercanasAlPunto);
    }

    public ResultadoBusqueda(List<Parada> paradas, List<String> palabrasBuscadas, int exactas) {
        this(paradas, palabrasBuscadas, exactas, List.of(), null);
    }

    public static ResultadoBusqueda vacio(List<String> palabrasBuscadas) {
        return new ResultadoBusqueda(List.of(), palabrasBuscadas, 0);
    }

    /** Devuelve una copia con el lugar pedido ya ubicado y las paradas más cercanas a él. */
    public ResultadoBusqueda withPunto(PuntoDeReferencia punto, List<ParadaCercana> cercanas) {
        return new ResultadoBusqueda(paradas, palabrasBuscadas, exactas, cercanas, punto);
    }

    /**
     * Devuelve una copia con el lugar ubicado y sus paradas cercanas, y sin las coincidencias de
     * texto.
     *
     * <p>Para cuando sabemos exactamente dónde queda lo que se pidió: ahí una parada "que se llama
     * parecido" no es una alternativa, es ruido que compite con la respuesta buena.
     */
    public ResultadoBusqueda soloCercanasA(PuntoDeReferencia punto, List<ParadaCercana> cercanas) {
        return new ResultadoBusqueda(List.of(), palabrasBuscadas, 0, cercanas, punto);
    }

    /**
     * {@code true} si no hay nada que ofrecer.
     *
     * <p>Mira las dos listas: cuando ubicamos el lugar, las cercanas son la respuesta y las
     * coincidencias de texto pueden no existir. Tener paradas vacío no significa no haber
     * encontrado nada.
     */
    public boolean sinResultados() {
        return paradas.isEmpty() && cercanasAlPunto.isEmpty();
    }

    /** {@code true} si ninguna parada matchea todas las palabras: lo que hay son aproximaciones. */
    public boolean soloAproximadas() {
        return !paradas.isEmpty() && exactas == 0;
    }

    /** {@code true} si pudimos ubicar el lugar pedido aunque no haya una parada justo ahí. */
    public boolean hayCercanasAlPunto() {
        return !cercanasAlPunto.isEmpty();
    }
}
