package com.bondi_mcp.mcp_stm_montevideo.client;

/** Falla al hablar con la API de la Intendencia: red, autenticación, error remoto o formato inesperado. */
public class TransportePublicoException extends RuntimeException {

    public TransportePublicoException(String message) {
        super(message);
    }

    public TransportePublicoException(String message, Throwable cause) {
        super(message, cause);
    }
}
