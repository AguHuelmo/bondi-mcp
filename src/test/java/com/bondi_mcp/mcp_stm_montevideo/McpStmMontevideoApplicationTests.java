package com.bondi_mcp.mcp_stm_montevideo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test del contexto completo.
 *
 * <p>Deshabilitado por defecto: levanta la app entera, así que necesita un Postgres corriendo
 * (`docker compose up -d`) y las credenciales de Montevideo en el entorno. Se corre a mano con:
 *
 * <pre>{@code
 * MONTEVIDEO_CLIENT_ID=... MONTEVIDEO_CLIENT_SECRET=... ./gradlew test --tests '*ApplicationTests' -Dtest.integracion=true
 * }</pre>
 *
 * La lógica de negocio se cubre en los tests del paquete {@code service}, que no necesitan infra.
 */
@SpringBootTest
@Disabled("Requiere Postgres y credenciales de la API de Montevideo; ver javadoc")
class McpStmMontevideoApplicationTests {

	@Test
	void contextLoads() {
	}

}
