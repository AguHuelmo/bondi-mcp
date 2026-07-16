package com.bondi_mcp.mcp_stm_montevideo.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoNormalizadorTest {

    @Test
    void saca_tildes_y_pasa_a_mayusculas() {
        assertThat(TextoNormalizador.normalizar("Coruña")).isEqualTo("CORUNA");
        assertThat(TextoNormalizador.normalizar("Av. 18 de Julio")).isEqualTo("AV 18 DE JULIO");
    }

    @Test
    void la_puntuacion_se_vuelve_separador() {
        assertThat(TextoNormalizador.normalizar("18 de julio y ejido.")).isEqualTo("18 DE JULIO Y EJIDO");
    }

    @Test
    void parte_en_palabras() {
        assertThat(TextoNormalizador.enPalabras("18 de Julio y Ejido"))
                .containsExactly("18", "DE", "JULIO", "Y", "EJIDO");
    }

    @Test
    void las_palabras_de_busqueda_no_incluyen_conectores() {
        assertThat(TextoNormalizador.enPalabrasDeBusqueda("18 de Julio y Ejido"))
                .containsExactly("18", "DE", "JULIO", "EJIDO");
        assertThat(TextoNormalizador.enPalabrasDeBusqueda("Coruña esquina Purificación"))
                .containsExactly("CORUNA", "PURIFICACION");
    }

    @Test
    void una_consulta_de_puros_conectores_no_deja_palabras() {
        assertThat(TextoNormalizador.enPalabrasDeBusqueda("y esq con")).isEmpty();
    }

    @Test
    void texto_sin_contenido_util_da_vacio() {
        assertThat(TextoNormalizador.enPalabras("   ")).isEmpty();
        assertThat(TextoNormalizador.enPalabras(null)).isEqualTo(List.of());
        assertThat(TextoNormalizador.enPalabras("!!!")).isEmpty();
    }
}
