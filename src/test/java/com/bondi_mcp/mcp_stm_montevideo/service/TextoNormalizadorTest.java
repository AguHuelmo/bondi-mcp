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
    void reconoce_una_direccion_con_numero_de_puerta() {
        assertThat(TextoNormalizador.pareceDireccionConNumero("gabriel pereira 2470")).isTrue();
        assertThat(TextoNormalizador.pareceDireccionConNumero("Coruña 2345")).isTrue();
        // La calle tiene número y la puerta también: solo la última cuenta.
        assertThat(TextoNormalizador.pareceDireccionConNumero("18 de julio 1360")).isTrue();
    }

    @Test
    void un_cruce_no_es_una_direccion_con_numero() {
        assertThat(TextoNormalizador.pareceDireccionConNumero("18 de julio y ejido")).isFalse();
        // Tiene número al final, pero el conector dice que se pidió una esquina.
        assertThat(TextoNormalizador.pareceDireccionConNumero("gabriel pereira y ejido 1360")).isFalse();
    }

    @Test
    void un_codigo_de_parada_suelto_no_es_una_direccion() {
        // Sin esto, buscar la parada 3977 se iría a geocodificar al pedo.
        assertThat(TextoNormalizador.pareceDireccionConNumero("3977")).isFalse();
        assertThat(TextoNormalizador.pareceDireccionConNumero("18 1360")).isFalse();
    }

    @Test
    void una_calle_sin_numero_no_es_una_direccion() {
        assertThat(TextoNormalizador.pareceDireccionConNumero("gabriel pereira")).isFalse();
        assertThat(TextoNormalizador.pareceDireccionConNumero("18 de julio")).isFalse();
    }

    @Test
    void un_numero_absurdamente_largo_no_es_una_puerta() {
        assertThat(TextoNormalizador.pareceDireccionConNumero("gabriel pereira 123456789")).isFalse();
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
