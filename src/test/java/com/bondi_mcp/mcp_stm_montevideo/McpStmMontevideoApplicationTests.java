package com.bondi_mcp.mcp_stm_montevideo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test del contexto completo.
 *
 * <p>Apagado salvo que se pida explícitamente: levanta la app entera, así que necesita un
 * Postgres corriendo y las credenciales de Montevideo en el entorno. Se corre a mano con:
 *
 * <pre>{@code
 * MONTEVIDEO_CLIENT_ID=... MONTEVIDEO_CLIENT_SECRET=... \
 *   ./gradlew test --tests '*ApplicationTests' -Dtest.integracion=true
 * }</pre>
 *
 * <p>La condición es una property de sistema y no un {@code @Disabled} para que ese comando
 * realmente lo habilite: con {@code @Disabled} el flag quedaba de adorno y el test no corría
 * nunca, ni siquiera a mano.
 *
 * <p>La lógica de negocio se cubre en los tests del paquete {@code service}, que no necesitan infra.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "test.integracion", matches = "true")
class McpStmMontevideoApplicationTests {

	@Test
	void contextLoads() {
	}

}
