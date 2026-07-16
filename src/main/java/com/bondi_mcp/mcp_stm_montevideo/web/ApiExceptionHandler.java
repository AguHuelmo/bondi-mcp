package com.bondi_mcp.mcp_stm_montevideo.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bondi_mcp.mcp_stm_montevideo.client.TransportePublicoException;

import lombok.extern.slf4j.Slf4j;

/**
 * Traduce fallas a respuestas HTTP.
 *
 * <p>Si la Intendencia se cae o cambia el formato, esto sale como 503 y el servicio sigue en pie;
 * nunca se filtra el stacktrace ni un 500 opaco al frontend.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TransportePublicoException.class)
    public ResponseEntity<ErrorResponse> manejarFallaExterna(TransportePublicoException ex) {
        log.warn("La API de Montevideo falló: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "servicio_externo_no_disponible",
                        "No se pudieron obtener los datos del STM en este momento. Intentá de nuevo en un rato.",
                        Instant.now()));
    }

    /** Cuerpo de error de la API. */
    public record ErrorResponse(String codigo, String mensaje, Instant momento) {
    }
}
