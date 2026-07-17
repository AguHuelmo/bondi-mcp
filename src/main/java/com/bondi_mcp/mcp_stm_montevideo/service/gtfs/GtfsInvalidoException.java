package com.bondi_mcp.mcp_stm_montevideo.service.gtfs;

/** El GTFS no se pudo leer o no tiene la forma esperada. */
public class GtfsInvalidoException extends RuntimeException {

    public GtfsInvalidoException(String message) {
        super(message);
    }

    public GtfsInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
