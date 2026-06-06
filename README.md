# BertiniBot V2

Bot de Discord en Java 21 sobre **JDA 5** y **Lavaplayer 2** con `youtube-source`. Reproduce musica en canales de voz, expone solo comandos slash y trae un panel de control con botones. Sin yt-dlp ni binarios embebidos.

> Para el codigo original (V1, Java 17 + LavaPlayer 1.3 + yt-dlp), ver la carpeta [`legacy/`](legacy/).

---

## Caracteristicas

- **Audio**: Lavaplayer 2.2.x + `youtube-source` (en proceso, sin yt-dlp ni FFmpeg). Pass-through de Opus, cero transcodificacion cuando la fuente lo permite.
- **Cache LRU + TTL** (200 entradas, 1 h) sobre las resoluciones de YouTube. Evita reconsultar al pedir la misma cancion.
- **Slash commands** con autocomplete en `/play` (sugiere queries previos del cache).
- **Panel de control** con botones (`Pausa/Reanudar`, `Saltar`, `Loop`, `Mezclar`, `Parar`, `Vol -/+`). Se edita en el mismo mensaje en lugar de spamear.
- **Loop** (off / pista / cola), **shuffle**, **seek** (`mm:ss` / `hh:mm:ss`), **volumen** 0-150 %.
- **Auto-desconexion** por inactividad y cuando todos los humanos abandonan el canal del bot.
- **Sin intents privilegiados**: solo `GUILD_VOICE_STATES`. Adios `MESSAGE_CONTENT`.
- **Token por variable de entorno** (`DISCORD_TOKEN`), con fallback a `config.properties` para desarrollo.
- **Logback** con rotacion diaria + por tamano (10 MB, 7 dias, 100 MB total).
- **Dockerfile multi-stage** sobre `eclipse-temurin:21-jre`, ~280 MB de imagen final.

## Comandos

### Musica
| Comando | Descripcion |
|---|---|
| `/play <query>` | URL de YouTube o texto a buscar. Autocomplete sugiere queries del cache. |
| `/skip` | Salta a la siguiente pista. |
| `/queue` | Muestra la cola con `Now Playing` y proximas 15. |
| `/clear` | Vacia la cola y detiene la reproduccion. |
| `/loop <off\|track\|queue>` | Configura el modo de loop. |
| `/shuffle` | Baraja la cola pendiente. |
| `/seek <mm:ss>` | Salta a un punto de la pista actual. |
| `/volume <0-150>` | Ajusta el volumen del player. |
| `/panel` | Abre el panel de control con botones. |

### Utilidades
| Comando | Descripcion |
|---|---|
| `/moneda` | Lanza una moneda con animacion. |
| `/build <champion> [mode]` | Runas + objetos de un campeon de LoL via METAsrc. |
| `/ping` | Latencia con la API de Discord. |
| `/info` | Version, uptime, memoria y latencia. |
| `/help` | Lista de comandos. |

## Arquitectura

```
bertinibot/src/main/java/com/main
├── Bootstrap.java                 # main(): config, JDA, registro
├── config/Config.java             # env-first, .properties fallback
├── core/
│   ├── SlashCommand.java          # contrato comun (+ AutoCompletable)
│   ├── CommandRegistry.java       # registro + dispatch + autocomplete
│   └── ComponentRouter.java       # botones por prefijo "panel:xxx"
├── audio/
│   ├── AudioService.java          # API publica + LavaPlayerManager + cache
│   ├── GuildAudio.java            # estado por guild
│   ├── Scheduler.java             # cola, loop, shuffle, callbacks
│   ├── ResolveCache.java          # LRU + TTL
│   └── OpusSendHandler.java       # puente JDA <-> Lavaplayer
├── commands/                      # Slash commands
├── panel/                         # Panel: embed, botones, dispatcher
├── filters/TrackFilter.java       # bloqueo por substring (CSV en env)
├── service/MetasrcService.java    # scraping LoL builds
├── util/
│   ├── EmbedFactory.java
│   └── AsyncHttp.java             # HttpClient compartido con virtual threads
└── listeners/VoiceChannelListener.java
```

## Requisitos

- **Java 21** (Temurin / Adoptium recomendado).
  ```powershell
  winget install EclipseAdoptium.Temurin.21.JDK
  ```
- **Maven NO hace falta**: el proyecto incluye Maven Wrapper (`mvnw` / `mvnw.cmd`) que se autodescarga al primer uso.
- Un **bot de Discord** con su token. En el Developer Portal NO hace falta activar ningun intent privilegiado.

> Windows: si tienes JDK 17 instalado tambien (por compatibilidad con el legacy), asegurate de que la sesion donde compilas usa el 21:
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
> $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
> ```

## Configuracion

Variables de entorno (todas leidas tambien desde `config.properties` como fallback de desarrollo):

| Variable | Obligatoria | Descripcion |
|---|---|---|
| `DISCORD_TOKEN` | Si | Token del bot. |
| `BLOCKED_TITLES` | No | CSV de substrings a bloquear en titulos. Vacio = no bloquea nada. |
| `LOG_PATH` | No | Carpeta para los logs rotativos. Default: `logs/`. |

Para desarrollo local copia `src/main/resources/config.properties.example` a `src/main/resources/config.properties` y rellena `discord.token`. Ese fichero esta gitignored.

## Ejecutar

### Local (Maven Wrapper)

```powershell
cd bertinibot
.\mvnw.cmd clean package
$env:DISCORD_TOKEN = "tu_token"
java -jar target\bertinibot-2.0.0.jar
```

En Linux/Mac es lo mismo con `./mvnw clean package`.

> El proyecto incluye `.mvn/jvm.config` con `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` para que Maven use el truststore de Windows. Esto evita el clasico `unable to find valid certification path` cuando un antivirus o proxy hace inspeccion HTTPS. En Linux/Mac esa opcion se ignora silenciosamente, asi que no molesta.

### Docker

```bash
cd bertinibot
DISCORD_TOKEN=tu_token docker compose up -d --build
docker compose logs -f
```

La imagen final pesa unos ~280 MB (JRE 21 + el shaded jar). El contenedor monta `./logs` como volumen para que la rotacion persista.

## Notas de seguridad

- El token del V1 se subio inicialmente al repo via un `.gitignore` mal formateado. Si todavia no lo has hecho, **regeneralo** en el Developer Portal de Discord antes de desplegar el V2.
- En el V2 el token se lee preferentemente de la env var `DISCORD_TOKEN` y `config.properties` esta cubierto por `**/config.properties` en el `.gitignore` raiz.

## Hosting recomendado

Para un solo servidor:

1. PC siempre encendido + Docker o servicio Windows (NSSM) - la opcion gratuita y con IP residencial (sin bloqueos de YouTube).
2. Raspberry Pi 4/5 en casa - 5 W de consumo, 24/7 sin pagar VPS.
3. VPS pequeno (Hetzner CX22, Oracle Free Tier ARM). Si vas por aqui, ten previstas **cookies de YouTube** porque tarde o temprano la IP del datacenter se bloquea.

## Codigo legacy (V1)

El proyecto original esta en `legacy/`, con su propio `pom.xml` y su README local. Sigue siendo compilable con `mvn -f legacy/pom.xml package`, pero LavaPlayer 1.3.77 esta abandonado y se rompera con cambios futuros de YouTube. No lo uses para nada nuevo.

## Roadmap futuro

- Spotify -> YouTube (mapeo de metadata).
- Persistencia (SQLite): historial, replay de las ultimas N.
- Filtros de audio (bass boost, nightcore) via Lavalink como nodo separado.
- Tests de integracion con mock de Discord.
