# mcp-stm-montevideo

Datos de los ómnibus de Montevideo, expuestos de tres formas sobre la misma lógica:

- **Servidor MCP** (Model Context Protocol), para que agentes de IA como Claude puedan buscar
  paradas y consultar cuándo llega el próximo bondi.
- **API REST**, que consume el frontend React incluido.
- **Bot de Telegram** (opcional): le mandás un número de parada, un cruce o tu ubicación y te
  contesta con datos reales. Funciona sin ningún LLM (gratis por mensaje); con una API key de
  Anthropic pasa a entender lenguaje natural.

Los datos salen de la [API pública de la Intendencia de Montevideo](https://api.montevideo.gub.uy),
la misma que usa la app *Cómo Ir*.

## Qué hace (v0)

| | MCP | REST |
|---|---|---|
| Buscar paradas por dirección o cruce | `buscar_paradas` | `GET /api/paradas?query=...` |
| Próximos arribos, y todas las líneas de la parada | `consultar_arribos` | `GET /api/paradas/{codigo}/arribos` |
| Paradas más cercanas a un punto | `paradas_cercanas` | `GET /api/paradas/cercanas?lat=&lon=` |
| Cómo ir de un lugar a otro, con o sin transbordo | `como_llego` | `GET /api/viajes?origen=&destino=` |
| Horarios teóricos de una línea en una parada | `horarios_teoricos` | `GET /api/paradas/{codigo}/lineas/{linea}/horarios` |
| Próxima salida programada, con la trasnoche ya resuelta | `proxima_salida` | — |
| El recorrido completo de una línea, por sentido | `recorrido_de_linea` | `GET /api/lineas/{linea}/recorridos` |
| Dónde está cada coche de una línea, en vivo | `buses_en_vivo` | `GET /api/lineas/{linea}/buses` |

La búsqueda es tolerante a errores: puntúa por palabras coincidentes en vez de exigirlas todas, y
si el cruce que pediste no tiene parada, **estima dónde queda y te ofrece las más cercanas con la
distancia**. Buscar "gabriel pereira y chucarro" —un cruce que no existe— devuelve la parada de
Gabriel A Pereira y Pedro F Berro a 55 m.

Las tools MCP devuelven además un campo `contexto` que le explica al LLM qué tiene entre manos
(coincidencia exacta, aproximación, o cruce estimado) y qué conviene repreguntarle al usuario.

## Stack

- **Backend**: Java 25, Spring Boot 4.1, Spring AI 2.0 (MCP server sobre Streamable HTTP),
  Spring Data JPA, PostgreSQL, Flyway. Build con **Gradle**.
- **Frontend**: React 19 + TypeScript, con Vite.

## Requisitos

- JDK 25
- Un Postgres corriendo (lo levantás vos; la app no administra contenedores)
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

## 2. Configurar las credenciales

**En local**, ponelas en un YAML que git ignora:

```bash
cp config/application.yaml.example config/application.yaml
# editá config/application.yaml con tu client id y secret
```

Spring Boot lee `./config/` del directorio de trabajo solo, así que `./gradlew bootRun` lo toma
sin variables de entorno ni flags. Ese archivo está en `.gitignore`: no lo commitees.

> No lo pongas en `src/main/resources/`: todo lo de ahí se empaqueta dentro del jar y terminarías
> distribuyendo el secreto en el artefacto.

**En producción**, no uses ese archivo: exportá las variables desde el gestor de secretos del
server, que rellenan los placeholders del `application.yaml` empaquetado.

```bash
export MONTEVIDEO_CLIENT_ID=tu-client-id
export MONTEVIDEO_CLIENT_SECRET=tu-client-secret
```

> **Ojo con la precedencia**: `config/application.yaml` le gana a las variables de entorno. La
> variable solo rellena un placeholder del `application.yaml` empaquetado, que es la fuente de
> menor precedencia, mientras que `config/application.yaml` define la propiedad directamente. Si
> ese archivo queda en un server, las credenciales del entorno se ignoran **en silencio**.

Si no hay ninguna de las dos fuentes, la app **no arranca** y te dice qué propiedad falta.

## 3. Levantar la base

La app no administra contenedores: la base la levantás vos, como más te guste. Por ejemplo:

```bash
docker run -d --name stm-postgres -p 5432:5432 \
  -e POSTGRES_DB=mcp_stm_montevideo \
  -e POSTGRES_USER=user -e POSTGRES_PASSWORD=password \
  postgres:17-alpine
```

**Flyway crea las tablas, no la base.** Si usás un Postgres que ya tenías, creala una vez:

```sql
CREATE DATABASE mcp_stm_montevideo;
```

Los defaults de la app son `localhost:5432`, base `mcp_stm_montevideo`, usuario y contraseña
`user`/`password`. Para apuntar a otro lado, cualquiera de estas variables:

| Variable | Default |
|---|---|
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `mcp_stm_montevideo` |
| `POSTGRES_USER` | `user` |
| `POSTGRES_PASSWORD` | `password` |

También podés fijarlas en `config/application.yaml`, junto a las credenciales.

> La migración `V1` hace `CREATE EXTENSION IF NOT EXISTS pg_trgm`, que necesita permisos de
> superusuario. Con el usuario por defecto de un contenedor Postgres va bien; con uno limitado,
> no.

## 4. Levantar el backend

```bash
./gradlew bootRun
```

Flyway corre las migraciones al arrancar. El backend queda en <http://localhost:8080>:

- REST en `/api/paradas`
- MCP en `/mcp`

Probalo:

```bash
curl "http://localhost:8080/api/paradas?query=18 de julio y ejido"
```

## 5. Levantar el frontend

```bash
cd frontend
npm install
npm run dev
```

Queda en <http://localhost:5173>. Vite proxya `/api` al backend en `:8080`, así que no hay que
configurar CORS.

## 6. Conectarlo a Claude Desktop

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

## 7. El bot de Telegram (opcional)

Con solo un token de [@BotFather](https://t.me/BotFather) la app levanta un bot que responde
sobre la misma capa de servicio del MCP. No hay webhook ni puerto público: escucha por long
polling, así que corre desde cualquier máquina con internet.

```yaml
# config/application.yaml
bot:
  telegram:
    token: 123456789:tu-token-de-botfather
```

`./gradlew bootRun` y escribile. El **modo comandos** (el por defecto) no usa ningún LLM: cada
mensaje cuesta cero y se puede dejar público sin miedo. Entiende:

| Mandás | Recibís |
|---|---|
| `3977` (el código del cartel) | los próximos arribos de esa parada |
| `3977 185` | las próximas salidas programadas de la 185 por ahí |
| `18 de julio y ejido` | las paradas de ese cruce (o las más cercanas si no hay) |
| `estadio centenario > pocitos` | qué línea tomar, dónde subir y dónde bajar |
| `linea 185` | el recorrido y cuántos coches andan ahora |
| `avisame 3977 185` | un aviso cuando la 185 esté a 5 min o menos de esa parada |
| `avisame 3977 185 10` | lo mismo pero con 10 minutos de anticipación |
| `alertas` / `cancelar` | ver o cortar tus alertas activas |
| tu ubicación con el clip 📎 | las paradas más cercanas con la distancia |

Las alertas son la gracia del bot: en vez de mirar la app parado en la esquina, le pedís que te
avise y seguís con lo tuyo. La guardia revisa los arribos cada 30 segundos (una sola consulta
por parada vigilada, tenga una alerta o diez), avisa una única vez y se apaga sola; si en 45
minutos la línea nunca se acercó, también te lo dice en vez de morir en silencio. Viven en
memoria: un reinicio las pierde, y volver a pedirla cuesta un mensaje.

**Extra opcional**: con una API key de Anthropic en `bot.claude.api-key`, el bot pasa a modo
lenguaje natural — Claude decide qué herramienta llamar (las mismas ocho del MCP) y podés
escribirle "¿cuándo pasa la 405 por 18 y Ejido?" como a una persona. Ojo: cada mensaje llama a
la API de Anthropic y lo pagás vos, así que es para uso propio o demos, no para abrirlo al
público.

El historial de cada chat vive en memoria y se pierde al reiniciar.

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
  parada" en una llamada. `ArriboService` averigua primero qué líneas pasan y después consulta.
  Las líneas van separadas por comas (`lines=60,104`); repetir el parámetro (`lines=60&lines=104`)
  hace que la API responda 500.
- **`/buses/busstops/{id}/lines` está roto**: devuelve 400 para cualquier parada. Sería el
  endpoint natural para saber qué líneas paran ahí. Por eso hay dos fuentes distintas:
  `GET /buses?busstopId=` da las líneas **con buses circulando** (las únicas que pueden producir
  un arribo), y el **GTFS estático** da las que pasan siempre, que es lo que se muestra cuando no
  viene ningún ómnibus.
- **La API de líneas es case-sensitive**: el GTFS escribe `Ce1` y `upcomingbuses` responde 400 a
  eso, pero 200 a `CE1`. Las líneas se normalizan a mayúscula al importar.
- **El GTFS pesa 17 MB** (~2,2 millones de filas en `stop_times.txt`). Se importa al arrancar, en
  un hilo aparte, y solo si cambió la versión publicada (`version.txt`). Tarda ~10 segundos.
- **Que una línea pase por dos paradas NO significa que lleve de una a la otra.** La 62 aparece en
  Gabriel Pereira y Berro y también en 18 de Julio y Ejido, pero de sus 90 viajes que tocan la
  primera y los 90 que tocan la segunda, **cero tocan ambas**: son los dos sentidos del recorrido.
  Por eso `como_llego` no cruza listas de líneas sino que exige que, dentro de un mismo recorrido,
  la parada de bajada tenga **orden mayor** que la de subida. Los 37.490 viajes del GTFS colapsan
  en ~1.080 recorridos distintos (~60.600 filas), que es lo que guarda `recorrido_parada`.
- **Si la Intendencia se cae**, un caché vencido se sigue sirviendo (una parada no se muda) y el
  REST responde `503` con un mensaje claro en vez de un 500 opaco.

## Limitaciones conocidas

- **Todavía no se busca por número de línea**, aunque ya se puede: la tabla `parada_linea` tiene
  la relación completa desde que importamos el GTFS. Falta solo exponerlo en la búsqueda.
- **No hay geocoder.** El catálogo de la Intendencia solo publica transporte y playas, así que no
  existe forma de convertir una dirección en un punto. El cruce se **estima** a partir de las
  paradas de cada calle: si la más cercana de una y de la otra están a menos de 250 m, el cruce
  está entre las dos. Es una heurística: acierta en cruces reales y se abstiene cuando las calles
  no se tocan, pero no es geocodificación.
- **Las distancias son en línea recta**, no caminando: la real siempre es algo mayor.
- **La spec de la Intendencia no refleja la API real.** `upcomingbuses` declara devolver
  `BusLineVariantItem[]`, que no tiene ningún campo de tiempo de arribo, cuando en realidad
  devuelve `ETAItem[]` (verificado contra la API en producción); el `$ref` de la spec está mal.
  `/buses` tampoco coincide con su `VehicleItem`: trae `company` en vez de `companyName`, más
  `eType` y `speed`. Por eso todos los DTO del cliente ignoran campos desconocidos y mapean solo
  lo necesario.
- Las paradas favoritas tienen su tabla (`V2__paradas_favoritas.sql`) pero todavía no se exponen:
  falta definir cómo se autentican los usuarios.

## Punto de extensión: posición en tiempo real

La API ya expone la posición en vivo de los buses en `GET /buses?lines=...`, devolviendo
`VehicleItem` con `location`, `timestamp`, `line`, `origin` y `destination`. No está implementado
en v0. El lugar para engancharlo está marcado en `TransportePublicoClient`.

## Problemas comunes

- **`database "mcp_stm_montevideo" does not exist`**: Flyway crea las tablas, no la base. Corré
  `CREATE DATABASE mcp_stm_montevideo;` una vez.
- **`Bind for 0.0.0.0:5432 failed: port is already allocated`**: ya tenés otro Postgres corriendo.
  Paralo, o levantá este en otro puerto y pasale `POSTGRES_PORT` a `bootRun`.
- **`Connection refused` al arrancar el backend**: la base no está levantada. La app no la
  levanta sola.
- **`503` en todas las consultas**: casi siempre son las credenciales. Fijate en el log si dice
  "No se pudo obtener el token OAuth2".
- **Las credenciales del server parecen ignorarse**: fijate que no haya quedado un
  `config/application.yaml` en el directorio de trabajo; le gana a las variables de entorno.

## Licencia

MIT. Ver [LICENSE](LICENSE).
