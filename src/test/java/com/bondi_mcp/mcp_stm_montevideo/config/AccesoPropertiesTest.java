package com.bondi_mcp.mcp_stm_montevideo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El binding de {@code acceso.*}.
 *
 * <p>Vale la pena testearlo aparte: el smoke test del contexto completo necesita Postgres y
 * credenciales, así que no corre en CI. Sin esto, un nombre de propiedad mal escrito recién se
 * descubriría al levantar la app.
 */
class AccesoPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
                    .of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(AccesoProperties.class)
    static class Config {
    }

    @Test
    void sin_configurar_nada_el_mcp_queda_abierto_y_el_limite_en_120() {
        runner.run(context -> {
            final AccesoProperties acceso = context.getBean(AccesoProperties.class);

            assertThat(acceso.mcpProtegido()).isFalse();
            assertThat(acceso.limitePorMinuto()).isEqualTo(120);
            assertThat(acceso.limiteActivo()).isTrue();
        });
    }

    @Test
    void las_propiedades_se_bindean_con_los_nombres_del_yaml() {
        runner.withPropertyValues("acceso.mcp-token=secreto", "acceso.limite-por-minuto=10")
                .run(context -> {
                    final AccesoProperties acceso = context.getBean(AccesoProperties.class);

                    assertThat(acceso.mcpToken()).isEqualTo("secreto");
                    assertThat(acceso.mcpProtegido()).isTrue();
                    assertThat(acceso.limitePorMinuto()).isEqualTo(10);
                });
    }

    @Test
    void el_limite_en_cero_queda_apagado() {
        runner.withPropertyValues("acceso.limite-por-minuto=0")
                .run(context -> assertThat(context.getBean(AccesoProperties.class).limiteActivo())
                        .isFalse());
    }

    @Test
    void un_limite_negativo_no_arranca() {
        runner.withPropertyValues("acceso.limite-por-minuto=-1")
                .run(context -> assertThat(context).hasFailed());
    }
}
