package com.bondi_mcp.mcp_stm_montevideo.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.bondi_mcp.mcp_stm_montevideo.config.AccesoProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** La puerta del /mcp: abierta si no hay token configurado, cerrada con llave si lo hay. */
class TokenMcpFilterTest {

    private static final String TOKEN = "un-token-secreto";

    private MockHttpServletResponse pedir(TokenMcpFilter filtro, String ruta, String autorizacion)
            throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", ruta);
        if (autorizacion != null) {
            request.addHeader("Authorization", autorizacion);
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static TokenMcpFilter conToken() {
        return new TokenMcpFilter(new AccesoProperties(TOKEN, 0));
    }

    @Test
    void con_el_token_correcto_pasa() throws Exception {
        assertThat(pedir(conToken(), "/mcp", "Bearer " + TOKEN).getStatus()).isEqualTo(200);
    }

    @Test
    void sin_header_authorization_es_401() throws Exception {
        final MockHttpServletResponse respuesta = pedir(conToken(), "/mcp", null);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void con_un_token_equivocado_es_401() throws Exception {
        assertThat(pedir(conToken(), "/mcp", "Bearer otro-token").getStatus()).isEqualTo(401);
    }

    @Test
    void un_prefijo_del_token_no_alcanza() throws Exception {
        assertThat(pedir(conToken(), "/mcp", "Bearer un-token").getStatus()).isEqualTo(401);
    }

    @Test
    void el_rest_no_pide_token() throws Exception {
        assertThat(pedir(conToken(), "/api/paradas", null).getStatus()).isEqualTo(200);
    }

    @Test
    void sin_token_configurado_el_mcp_queda_abierto() throws Exception {
        final TokenMcpFilter abierto = new TokenMcpFilter(new AccesoProperties("", 0));

        assertThat(pedir(abierto, "/mcp", null).getStatus()).isEqualTo(200);
    }
}
