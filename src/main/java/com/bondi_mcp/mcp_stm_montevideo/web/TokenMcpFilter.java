package com.bondi_mcp.mcp_stm_montevideo.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bondi_mcp.mcp_stm_montevideo.config.AccesoProperties;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deja {@code /mcp} solo para quien traiga el token, si hay uno configurado.
 *
 * <p>El MCP es la superficie más golosa: expone las once herramientas y cada llamada consume
 * cuota de la cuenta de la Intendencia. Con {@code acceso.mcp-token} definido, el cliente manda
 * {@code Authorization: Bearer <token>} —en Claude Desktop, vía el flag {@code --header} de
 * {@code mcp-remote}—. Sin token configurado el endpoint queda abierto, que es lo cómodo en
 * local, y el arranque lo avisa por log para que nadie lo publique así sin enterarse.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class TokenMcpFilter extends OncePerRequestFilter {

    private final AccesoProperties acceso;

    @PostConstruct
    void avisarSiEstaAbierto() {
        if (acceso.mcpProtegido()) {
            log.info("El endpoint /mcp exige token (acceso.mcp-token)");
        }
        else {
            log.warn("El endpoint /mcp está ABIERTO: cualquiera que sepa la URL gasta tu cuota "
                    + "de la API de la Intendencia. Configurá acceso.mcp-token antes de publicarlo.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !acceso.mcpProtegido() || !request.getRequestURI().startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        if (!tokenValido(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Comparación en tiempo constante: un {@code equals} normal filtra el token carácter a carácter. */
    private boolean tokenValido(String autorizacion) {
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            return false;
        }
        final byte[] recibido = autorizacion.substring("Bearer ".length())
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(recibido, acceso.mcpToken().getBytes(StandardCharsets.UTF_8));
    }
}
