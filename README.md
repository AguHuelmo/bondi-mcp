# mcp-stm-montevideo

Datos de los ómnibus de Montevideo, expuestos de dos formas sobre la misma lógica:

- **Servidor MCP** (Model Context Protocol), para que agentes de IA como Claude puedan buscar
  paradas y consultar cuándo llega el próximo bondi.
- **API REST**, que consume el frontend React incluido.

Los datos salen de la [API pública de la Intendencia de Montevideo](https://api.montevideo.gub.uy),
la misma que usa la app *Cómo Ir*.

## Qué hace (v0)

| | MCP | REST |
|---|---|---|
| Buscar paradas por dirección o cruce | `buscar_paradas` | `GET /api/paradas?query=...` |
| Próximos arribos a una parada | `consultar_arribos` | `GET /api/paradas/{codigo}/arribos` |

## Stack

- **Backend**: Java 25, Spring Boot 4.1, Spring AI 2.0 (MCP server sobre Streamable HTTP),
  Spring Data JPA, PostgreSQL, Flyway. Build con **Gradle**.
- **Frontend**: React 19 + TypeScript, con Vite.

## Requisitos

- JDK 25
- Docker (para el Postgres local; lo levanta solo)
- Node 20+ (para el frontend)

## 1. Conseguir las credenciales

La API de la Intendencia es gratuita pero pide registro. **No usa API key: usa OAuth2
client_credentials**, así que vas a obtener un *client id* y un *client secret*.

1. Entrá a <https://api.montevideo.gub.uy> y creá una cuenta gratuita.
2. Iniciá sesión y andá al [catálogo de APIs](https://api.montevideo.gub.uy/docs) →
   *Servicios de transporte público*.
3. Copiá las credenciales de tu aplicación.

Por defecto te dan un límite razonable de consultas por segundo. Para uso intensivo, escribile a
`pci@imm.gub.uy`.

## 2. Configurar el entorno

Las credenciales se leen del entorno y **nunca se hardcodean**:

```bash
cp .env.example .env
# editá .env con tu client id y secret
```

```bash
export MONTEVIDEO_CLIENT_ID=tu-client-id
export MONTEVIDEO_CLIENT_SECRET=tu-client-secret
```

`.env` está en `.gitignore`. No lo commitees.

## 3. Levantar el backend

```bash
./gradlew bootRun
```

Gracias al soporte de Docker Compose de Spring Boot, esto levanta el Postgres de `compose.yaml`
solo y le corre las migraciones de Flyway. El backend queda en <http://localhost:8080>:

- REST en `/api/paradas`
- MCP en `/mcp`

Probalo:

```bash
curl "http://localhost:8080/api/paradas?query=18 de julio y ejido"
```

## 4. Levantar el frontend

```bash
cd frontend
npm install
npm run dev
```

Queda en <http://localhost:5173>. Vite proxya `/api` al backend en `:8080`, así que no hay que
configurar CORS.

## 5. Conectarlo a Claude Desktop

El servidor habla **Streamable HTTP**, no stdio. Claude Desktop se conecta con el proxy
`mcp-remote`. Con el backend corriendo, agregá esto a tu `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "stm-montevideo": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8080/mcp"]
    }
  }
}
```

El archivo está en:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

Reiniciá Claude Desktop y deberían aparecer `buscar_paradas` y `consultar_arribos`. Después podés
preguntarle cosas como *"¿cuándo pasa el próximo bondi por 18 de julio y ejido?"*.

Si tu versión de Claude Desktop soporta conectores personalizados por URL, también podés apuntarlo
directo a `http://localhost:8080/mcp` desde Settings → Connectors.

## Tests

```bash
./gradlew test
```

Cubren la capa de servicio con la API externa mockeada. El smoke test del contexto completo
(`McpStmMontevideoApplicationTests`) está `@Disabled` porque necesita Postgres y credenciales
reales; el javadoc explica cómo correrlo.

## Arquitectura

```
src/main/java/.../client   -> cliente HTTP a api.montevideo.gub.uy + OAuth2
src/main/java/.../service  -> lógica de negocio (caché, búsqueda, arribos)
src/main/java/.../mcp      -> herramientas @McpTool  ─┐ ambas delegan
src/main/java/.../web      -> controllers REST        ─┘ al mismo service
src/main/java/.../domain   -> records del dominio
src/main/resources/db/migration -> migraciones Flyway
frontend/                  -> React 19 + Vite
```

MCP y REST no duplican lógica: son dos fachadas delgadas sobre las mismas clases de servicio.

### Detalles de la API de la Intendencia que condicionan el diseño

Estos no son caprichos, salen de la [spec real](https://api.montevideo.gub.uy/apidocs/publictransport):

- **`GET /buses/busstops` no acepta ningún filtro**: devuelve todas las paradas y no ofrece
  búsqueda por texto. Por eso existe la tabla `parada_cache`, que se refresca sola y sobre la
  cual buscamos localmente. No es una optimización, es la única forma.
- **`upcomingbuses` exige el parámetro `lines`**: no se puede pedir "todos los arribos de esta
  parada" en una llamada. `ArriboService` encadena `/lines` y después `/upcomingbuses`.
- **Si la Intendencia se cae**, un caché vencido se sigue sirviendo (una parada no se muda) y el
  REST responde `503` con un mensaje claro en vez de un 500 opaco.

## Limitaciones conocidas

- **No se puede buscar paradas por número de línea.** El endpoint de paradas solo devuelve calle,
  esquina y ubicación; la relación línea↔parada no está ahí. Habría que parsear el GTFS estático
  (`/buses/gtfs/static/latest/google_transit.zip`) o pegarle a `/lines` por cada parada.
- **El parseo de arribos no está verificado contra la API real.** La spec de la Intendencia es
  inconsistente: declara que `upcomingbuses` devuelve `BusLineVariantItem[]` (que no tiene ningún
  campo de tiempo de arribo) y a la vez define un `ETAItem` con `eta`, `distance` y `position` que
  ningún endpoint referencia. Asumimos que la respuesta real es `ETAItem[]` y el parseo es
  tolerante, pero **conviene confirmarlo con credenciales reales**. Ver `EtaItem.java`.
- Las paradas favoritas tienen su tabla (`V2__paradas_favoritas.sql`) pero todavía no se exponen:
  falta definir cómo se autentican los usuarios.

## Punto de extensión: posición en tiempo real

La API ya expone la posición en vivo de los buses en `GET /buses?lines=...`, devolviendo
`VehicleItem` con `location`, `timestamp`, `line`, `origin` y `destination`. No está implementado
en v0. El lugar para engancharlo está marcado en `TransportePublicoClient`.

## Problemas comunes

- **`Bind for 0.0.0.0:5432 failed: port is already allocated`**: ya tenés otro Postgres corriendo.
  Paralo, o cambiá el puerto publicado en `compose.yaml`.
- **`503` en todas las consultas**: casi siempre son las credenciales. Fijate en el log si dice
  "No se pudo obtener el token OAuth2".

## Licencia

MIT. Ver [LICENSE](LICENSE).
