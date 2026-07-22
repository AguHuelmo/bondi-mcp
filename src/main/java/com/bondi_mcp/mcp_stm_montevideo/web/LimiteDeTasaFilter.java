package com.bondi_mcp.mcp_stm_montevideo.web;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bondi_mcp.mcp_stm_montevideo.config.AccesoProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tope de requests por IP sobre las superficies que gastan cuota de la Intendencia.
 *
 * <p>Ventana fija de un minuto, en memoria: no pretende ser un rate limiter distribuido, sino
 * evitar que un script suelto —o una integración con un bucle mal hecho— queme la cuota de la
 * cuenta en minutos. Con varias instancias cada una lleva su propia cuenta.
 *
 * <p>Corre antes de {@link TokenMcpFilter} a propósito: así el límite también aplica a los
 * intentos de adivinar el token.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LimiteDeTasaFilter extends OncePerRequestFilter {

    /** Techo de IPs recordadas. Al pasarlo se purgan las de ventanas viejas: el limitador no
     *  puede ser él mismo el vector de memoria. */
    private static final int MAX_IPS = 10_000;

    private final AccesoProperties acceso;
    private final Map<String, Ventana> ventanasPorIp = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String ruta = request.getRequestURI();
        return !acceso.limiteActivo() || !(ruta.startsWith("/api/") || ruta.startsWith("/mcp"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        final long minuto = Instant.now().getEpochSecond() / 60;
        final String ip = ipDelCliente(request);

        if (ventanasPorIp.size() > MAX_IPS) {
            ventanasPorIp.values().removeIf(ventana -> ventana.minuto() < minuto);
        }

        final Ventana ventana = ventanasPorIp.compute(ip,
                (clave, actual) -> actual == null || actual.minuto() != minuto
                        ? new Ventana(minuto, 1)
                        : actual.masUno());

        if (ventana.usados() > acceso.limitePorMinuto()) {
            responder429(response, minuto);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * La IP real del cliente.
     *
     * <p>Detrás de Caddy {@code getRemoteAddr()} es siempre la del proxy, así que todo el mundo
     * compartiría un solo balde. Se usa {@code X-Forwarded-For}, pero la <b>última</b> entrada:
     * el proxy agrega la IP que él vio al final de lo que venga, de modo que un cliente que
     * mande un {@code X-Forwarded-For} falso solo ensucia el principio de la lista.
     */
    private static String ipDelCliente(HttpServletRequest request) {
        final String reenviadas = request.getHeader("X-Forwarded-For");
        if (reenviadas == null || reenviadas.isBlank()) {
            return request.getRemoteAddr();
        }
        final int ultimaComa = reenviadas.lastIndexOf(',');
        return reenviadas.substring(ultimaComa + 1).trim();
    }

    private static void responder429(HttpServletResponse response, long minuto) throws IOException {
        final long segundosQueFaltan = (minuto + 1) * 60 - Instant.now().getEpochSecond();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, segundosQueFaltan)));
        response.getWriter().write("""
                {"codigo":"demasiadas_consultas",\
                "mensaje":"Superaste el límite de consultas por minuto. Probá de nuevo en un momento."}""");
    }

    /** Cuántos requests lleva una IP en el minuto en curso. */
    private record Ventana(long minuto, int usados) {

        Ventana masUno() {
            return new Ventana(minuto, usados + 1);
        }
    }
}
