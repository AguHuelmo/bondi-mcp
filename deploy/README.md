# Deploy a un VPS

Todo el sistema —API REST, servidor MCP, frontend, bot de Telegram, webhook de WhatsApp,
cartelera y demos— en un VPS chico, con HTTPS automático. Una vez andando, el historial de
puntualidad empieza a juntarse solo: cada día deployado agranda el dataset.

## 0. Qué necesitás

- **Un VPS con 2 GB de RAM o más** (4 GB holgado). Ubuntu 24.04. Opciones típicas:
  Hetzner (~4–6 €/mes), DigitalOcean, Vultr, Contabo.
- **Un dominio o subdominio** apuntando al VPS (registro A → la IP). Es lo que le permite a
  Caddy conseguir el certificado HTTPS solo; sin HTTPS no hay webhook de WhatsApp ni conector
  MCP remoto prolijo.
- Las **credenciales de la Intendencia** (ver README principal, sección 1).

## 1. Preparar el VPS (una vez)

```bash
ssh root@TU-IP

# Docker oficial
curl -fsSL https://get.docker.com | sh

# Firewall: solo SSH y web
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
```

## 2. Clonar y configurar

```bash
git clone https://github.com/AguHuelmo/bondi-mcp.git
cd bondi-mcp/deploy
cp .env.example .env
nano .env    # dominio, contraseña de la base, credenciales IMM, tokens opcionales
```

## 3. Levantar

```bash
docker compose up -d --build
```

La primera vez tarda varios minutos: compila el frontend y el backend adentro de Docker, y al
arrancar la app baja el listado de paradas y el GTFS completo (~17 MB que se procesan una sola
vez). Mirá el progreso con:

```bash
docker compose logs -f app
```

Cuando el log diga `GTFS importado`, está todo cargado.

## 4. Verificar

```bash
curl https://TU-DOMINIO/actuator/health          # {"status":"UP"}
curl "https://TU-DOMINIO/api/paradas?query=18 de julio y ejido"
```

Y en el navegador:

| Qué | URL |
|---|---|
| Frontend completo | `https://TU-DOMINIO/paradas` |
| Cartelera para pantallas | `https://TU-DOMINIO/cartelera.html` |
| Demo para inmobiliarias | `https://TU-DOMINIO/demo-conectividad.html` |

## 5. Conectar cada pata

- **Claude Desktop (MCP)**: en `claude_desktop_config.json`:

  ```json
  {
    "mcpServers": {
      "stm-montevideo": {
        "command": "npx",
        "args": ["-y", "mcp-remote", "https://TU-DOMINIO/mcp",
                 "--header", "Authorization: Bearer TU-MCP-TOKEN"]
      }
    }
  }
  ```

  El header sobra si dejaste `MCP_TOKEN` vacío, pero entonces `/mcp` queda **público** y
  cualquiera con la URL consume tu cuota de la Intendencia. La app lo avisa con un `WARN` al
  arrancar. `/api` queda público en cualquier caso: lo que lo protege es
  `LIMITE_POR_MINUTO` (120 requests por IP por minuto por defecto).

- **Telegram**: nada que configurar además del token en `.env` — el bot hace long polling
  desde el VPS hacia afuera.

- **WhatsApp**: en developers.facebook.com → tu app → WhatsApp → *Configuration*, registrá el
  webhook `https://TU-DOMINIO/webhook/whatsapp` con tu `WHATSAPP_VERIFY_TOKEN` y suscribite al
  campo **messages**. Ojo: hacen falta los **cuatro** `WHATSAPP_*` del `.env`, `APP_SECRET`
  incluido; sin él la pata de WhatsApp arranca apagada a propósito.

## 6. Operar

```bash
# Actualizar a la última versión del repo
git pull && docker compose up -d --build

# Logs
docker compose logs -f app

# Backup diario de la base (el historial de puntualidad ES el activo: no lo pierdas)
crontab -e
# 0 5 * * * cd /root/bondi-mcp/deploy && docker compose exec -T db pg_dump -U bondi mcp_stm_montevideo | gzip > /root/backup-bondi-$(date +\%u).sql.gz
```

El backup con `%u` (día de la semana) rota solo: siete archivos, siempre la última semana.

## Sin dominio (solo para probar)

Telegram y el MCP por IP funcionan sin dominio: cambiá en el `Caddyfile` la primera línea por
`:80` y conectá `http://TU-IP/...`. Sin HTTPS no hay webhook de WhatsApp, y Let's Encrypt no
emite certificados para IPs peladas — conseguí el subdominio, es la única pieza que no se puede
esquivar.
