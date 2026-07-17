package com.bondi_mcp.mcp_stm_montevideo.domain;

/**
 * El lugar que se pidió, cuando no hay ninguna parada que matchee pero sí se pudo ubicar dónde
 * queda.
 *
 * <p>El {@link Origen} no es un detalle interno: dice cuánto creerle al punto. Una dirección sale
 * del padrón oficial y es exacta; un cruce lo estimamos nosotros a partir de las paradas de cada
 * calle y puede errarle. Quien muestre esto tiene que poder decir cuál de las dos cosas es.
 *
 * @param coordenada  dónde queda
 * @param origen      de dónde salió el punto
 * @param descripcion cómo nombrar el lugar ("GABRIEL A. PEREIRA 2470", "ESTADIO CENTENARIO");
 *                    {@code null} para un cruce estimado, que no tiene nombre oficial que mostrar
 */
public record PuntoDeReferencia(Coordenada coordenada, Origen origen, String descripcion) {

    /** De dónde salió el punto, y por lo tanto cuánto vale. */
    public enum Origen {

        /** Estimado a partir de las paradas de las dos calles. Aproximado. */
        CRUCE_ESTIMADO,

        /** Geocodificado contra el padrón oficial de direcciones. Exacto. */
        DIRECCION_OFICIAL,

        /** Un punto de interés del padrón cuyo nombre coincide con lo que se buscó. */
        LUGAR_CONOCIDO
    }

    public static PuntoDeReferencia deCruceEstimado(Coordenada coordenada) {
        return new PuntoDeReferencia(coordenada, Origen.CRUCE_ESTIMADO, null);
    }

    public static PuntoDeReferencia deDireccion(DireccionUbicada direccion) {
        return new PuntoDeReferencia(direccion.coordenada(), Origen.DIRECCION_OFICIAL,
                direccion.direccionOficial());
    }

    public static PuntoDeReferencia deLugar(LugarUbicado lugar) {
        return new PuntoDeReferencia(lugar.coordenada(), Origen.LUGAR_CONOCIDO, lugar.nombre());
    }
}
