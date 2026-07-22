package com.bondi_mcp.mcp_stm_montevideo.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.bondi_mcp.mcp_stm_montevideo.config.AccesoProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** El tope por IP: qué rutas cubre, cómo identifica al cliente y cuándo corta. */
class LimiteDeTasaFilterTest {

    private final LimiteDeTasaFilter filtro = new LimiteDeTasaFilter(new AccesoProperties("", 3));

    private MockHttpServletResponse pedir(String ruta, String ip, String reenviadas)
            throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", ruta);
        request.setRemoteAddr(ip);
        if (reenviadas != null) {
            request.addHeader("X-Forwarded-For", reenviadas);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse pedir(String ruta, String ip) throws Exception {
        return pedir(ruta, ip, null);
    }

    @Test
    void hasta_el_limite_pasa_y_el_siguiente_es_429() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(pedir("/api/paradas", "1.1.1.1").getStatus()).isEqualTo(200);
        }

        final MockHttpServletResponse cortada = pedir("/api/paradas", "1.1.1.1");

        assertThat(cortada.getStatus()).isEqualTo(429);
        assertThat(cortada.getHeader("Retry-After")).isNotNull();
        assertThat(cortada.getContentAsString()).contains("demasiadas_consultas");
    }

    @Test
    void cada_ip_tiene_su_propio_balde() throws Exception {
        for (int i = 0; i < 4; i++) {
            pedir("/api/paradas", "1.1.1.1");
        }

        assertThat(pedir("/api/paradas", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    /** Detrás del proxy, la IP de confianza es la ÚLTIMA: la que el propio proxy agregó. */
    @Test
    void detras_de_un_proxy_manda_la_ultima_ip_reenviada() throws Exception {
        for (int i = 0; i < 3; i++) {
            pedir("/api/paradas", "10.0.0.1", "9.9.9.9, 8.8.8.8");
        }

        assertThat(pedir("/api/paradas", "10.0.0.1", "9.9.9.9, 8.8.8.8").getStatus())
                .isEqualTo(429);
        assertThat(pedir("/api/paradas", "10.0.0.1", "9.9.9.9, 7.7.7.7").getStatus())
                .isEqualTo(200);
    }

    @Test
    void las_rutas_que_no_gastan_cuota_no_se_limitan() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(pedir("/cartelera.html", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void con_el_limite_en_cero_no_se_corta_nunca() throws Exception {
        final LimiteDeTasaFilter apagado = new LimiteDeTasaFilter(new AccesoProperties("", 0));

        for (int i = 0; i < 50; i++) {
            final MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/api/paradas");
            request.setRemoteAddr("1.1.1.1");
            final MockHttpServletResponse response = new MockHttpServletResponse();
            apagado.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
