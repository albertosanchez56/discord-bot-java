# BertiniBot

Bot de Discord en Java (JDA) con reproducción de música desde YouTube usando **LavaPlayer** + **yt-dlp**, gestión de cola, embeds bonitos, comandos con prefijo `!` y comandos *slash* `/`. Incluye utilidades como lanzar una moneda y mostrar builds (objetos) de campeones de LoL desde METAsrc.

> **Nota**: Este README resume lo implementado. Se omite deliberadamente la parte de “runas”.

---

## ✨ Funcionalidades principales

* 🎵 **Reproducción de audio** en canales de voz:

  * Soporte para **enlaces de YouTube** y **búsqueda por nombre** (fallback).
  * Integración con **yt-dlp** para obtener el *stream* directo (evita muchos bloqueos que sufre LavaPlayer).
  * **Cola de reproducción** (enqueue), *skip* (`!skip`), limpieza (`!clearList`) y listado (`!list`).
  * **Miniaturas** y *embeds* informativos de la pista actual y de la cola.
  * **Filtro** de títulos (ej.: bloquear temas que contengan "roxanne").
  * **Desconexión por inactividad**: si la cola queda vacía o todos abandonan el canal, el bot programa su desconexión pasado un tiempo.

* 🧠 **Mejores embeds**: mensajes enriquecidos (título, autor, duración, miniatura, enlace al vídeo, gif temático, etc.).

* 🎲 **Utilidades**:

  * `/moneda` (cara o cruz) con animación (GIF) y edición del mensaje tras unos segundos.

* 🧩 **Arquitectura limpia**: separación en paquetes `audio`, `commands`, `listeners`, `service`, `util`, `model`.

---

## 🧭 Comandos

### Prefijo `!`

* `!play <url | texto>`

  * Si pasas **URL de YouTube**, se usa `yt-dlp` para obtener el stream.
  * Si pasas **texto**, se busca en YouTube y se encola el primer resultado.
* `!skip` — Salta a la siguiente pista en la cola.
* `!clearList` — Limpia la cola y detiene la pista actual.
* `!list` — Muestra la cola actual (incluye *Now Playing*).

### Comandos *slash*

* `/moneda` — Lanza una moneda con animación y muestra el resultado.

> Si usas ambos estilos (prefijo y slash), recuerda registrar los *slash commands* al arrancar (ver sección de **Arranque**).

---

## 🏗️ Arquitectura y clases clave

```
src/main/java/com/main
├─ audio/
│  ├─ AudioTrackScheduler.java     # Cola, títulos, miniaturas, inactividad
│  ├─ GuildMusicManager.java       # Une player + scheduler por servidor
│  ├─ PlayerManager.java           # Singleton de LavaPlayer
│  ├─ AudioPlayerSendHandler.java  # Puente JDA <-> LavaPlayer
│  └─ YtDlpManager.java            # Invoca yt-dlp (título, videoId, url directa)
│
├─ commands/
│  ├─ PlayCommand.java             # !play (URL o búsqueda) + embeds + filtros
│  ├─ SkipCommand.java             # !skip
│  ├─ CleanListCommand.java        # !clearList
│  ├─ ListCommand.java             # !list
│  ├─ HelpCommand.java             # /help o similar
│  ├─ CoinFlipCommand.java         # /moneda con animación
│  └─ BuildCommand.java            # /build (objetos desde METAsrc)
│
├─ listeners/
│  ├─ CommandListener.java         # enruta slash commands a sus clases
│  └─ VoiceChannelListener.java    # desconecta si el canal queda sin usuarios
│
├─ model/
│  └─ TrackInfo.java               # record(title, directUrl, videoId)
│
├─ service/
│  ├─ YtDlpService.java            # Lógica de detección URL/búsqueda + título/ID
│  └─ MetasrcService.java          # Scraping de METAsrc (objetos + urls)
│
├─ util/
│  ├─ EmbedFactory.java            # Embeds bonitos (Now Playing, Cola)
│  └─ TrackFilter.java             # Bloqueos por título (ej. "roxanne")
│
└─ BertiniBot.java                 # Main, intents, registro de slash, pool yt-dlp
```

### Flujo de reproducción (resumen)

1. `!play <algo>`
2. `PlayCommand` detecta si es **URL** o **texto**.
3. Llama a `YtDlpService` / `YtDlpManager` para: **título**, **videoId**, **url directa**.
4. `PlayerManager` carga el `directUrl`; `AudioTrackScheduler.queue(...)` encola y/o reproduce.
5. `AudioTrackScheduler` maneja fin de pista, pasa a la siguiente y programa **desconexión por inactividad** si no quedan canciones.
6. `EmbedFactory` arma los embeds (*Now Playing*, Cola, miniaturas, enlaces, etc.).

---

## 🧰 Requisitos

* **Java 17**
* **Maven**
* **yt-dlp** en el PATH del sistema (o integrarlo como binario en recursos y extraerlo al arranque).
* **FFmpeg** instalado (recomendado; yt-dlp lo sugiere para mejor compatibilidad de formatos).
* **Token de Discord** válido.

---

## ⚙️ Configuración

1. Crea un archivo `config.properties` (o tu mecanismo actual) con tu **DISCORD\_TOKEN**.
2. En el **Portal de Desarrolladores** de Discord (tu aplicación > *Bot*):

   * Activa *Privileged Gateway Intents* necesarios (mensajes, contenido, voz).
   * Permite *Message Content Intent* si usas prefijo `!`.
   * Asegura permisos de **Enviar Mensajes**, **Insertar Embeds** y **Adjuntar Archivos**.
3. Instala **yt-dlp** y **ffmpeg** en el servidor/máquina donde corre el bot:

   * Windows: `winget install yt-dlp.yt-dlp` y `winget install Gyan.FFmpeg`
   * Linux: `pipx install yt-dlp` o paquete de la distro; `sudo apt install ffmpeg`.

---

## ▶️ Arranque

Compila *fat-jar* con Maven (assembly plugin):

```bash
mvn clean package
java -jar target/bertinibot-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Registra *slash commands* al iniciar (`BertiniBot.registerSlashCommands()` ya lo hace). Tardan unos segundos en propagarse.

Para **invitar** el bot (si es *público*), usa la URL OAuth2 con *scopes* `bot applications.commands` y los permisos que necesites.

> Si el bot es **privado**, sólo el propietario puede invitarlo desde el portal (no hay “default authorization link”).

---

## 🚨 Solución de problemas

* **`CreateProcess error=2 (yt-dlp no encontrado)`**

  * Asegúrate de tener `yt-dlp` instalado y en el **PATH**. Reinicia la consola/IDE tras instalar.

* **`WARNING: ffmpeg not found`**

  * Instala **FFmpeg**. Aunque a veces reproduce sin él, algunos formatos serán peores sin FFmpeg.

* **Títulos como `Unknown title` o enlace `https://youtu.be/ERROR:`**

  * Revisa que `YtDlpService`/`YtDlpManager` obtenga bien `videoId` (si es búsqueda vs URL directa).
  * No sobrescribas metadata con textos de error en los embeds.

* **No se ven imágenes en embeds**

  * El bot necesita permisos para *Embed Links* / *Attach Files*.
  * Evita enlaces con hotlink "+ raros" (Tenor suele fallar en embeds; usa Giphy/Gfycat o attachments).

* **Se desconecta mientras hay música** (tras `!skip`)

  * Asegúrate de **cancelar** el *idle task* cuando se encola o empieza una nueva pista (tu `AudioTrackScheduler` ya lo hace en `queue()` y en `skipTrack()`).

* **Tras vaciar la cola, no se desconecta**

  * Verifica que en `onTrackEnd()` programe la desconexión si `queue` está vacía (y no hay `mayStartNext`).

---

## 🛣️ Roadmap / Ideas futuras

* **Panel de control** con botones (componentes interactivos) para *pause/resume/skip/loop/volume*.
* **Guardar sonidos** enviados por usuarios (.mp3) para un *soundboard* y reproducirlos.
* **Soporte de listas** (YouTube playlists) con paginación al encolar.
* **Búsqueda mejorada** (`ytsearch:` multiresultado → menú de selección por botones).
* **Despliegue** en VPS/Docker con *systemd* y logs rotativos.
* **Spotify**: mapping de pistas/playlists a YouTube (sólo metadatos; no reproducir contenido cifrado de Spotify).

---

## 📄 Licencias / Notas

* Respeta los **TOS de Discord** y las políticas de YouTube/Google.

---

## 🙌 Créditos

* [JDA](https://github.com/discord-jda/JDA)
* [LavaPlayer](https://github.com/sedmelluq/lavaplayer)
* [yt-dlp](https://github.com/yt-dlp/yt-dlp)
* [FFmpeg](https://ffmpeg.org/)
