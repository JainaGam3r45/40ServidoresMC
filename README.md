40ServidoresMC
==========

Inicio
------------
40ServidoresMC es una web de rankings de servidores Online.
Con este plugin podrás otorgar a tus jugadores premios por votar a tu servidor, y potenciar así el puesto en el ranking.

Puedes encontrar mucha más información en nuestra [Wiki](https://github.com/Cadiducho/40ServidoresMC/wiki) y aprende todo lo necesario sobre este plugin!

Compatibilidad
------------
Hay dos artefactos de producción:

| JAR | Dónde va |
| --- | --- |
| `40ServidoresMC-*-Bukkit.jar` | Servidores Spigot, Paper, Purpur y forks basados en Bukkit/Spigot. |
| `40ServidoresMC-*-Sponge-API7.jar` | Servidores Sponge API 7. |

**Paper / Purpur / Spigot.** El JAR Bukkit es el que debes instalar en el servidor de juego. Paper y Purpur suelen funcionar igual que Spigot porque siguen la API Bukkit; no hace falta un JAR aparte.

**Folia.** El JAR Bukkit declara `folia-supported: true`. Si el servidor es Folia, las tareas van por `GlobalRegionScheduler` (global) y `EntityScheduler` (por jugador), en lugar del scheduler clásico de un solo hilo. Eso es preparación real del scheduler; no es un “soporte Folia completo” probado en producción.

**Proxies (BungeeCord, Velocity, Waterfall, etc.).** El plugin no se instala en el proxy. Va en cada servidor de juego (backend) donde quieras premios y comandos. Detrás de un proxy funciona como en un Spigot/Paper normal, siempre que el JAR esté en ese backend.

**Sponge.** Usa el JAR de Sponge API 7. No mezcles el JAR Bukkit en un servidor Sponge.

Configuración
------------
Las versiones actuales usan una estructura de `config.yml` en inglés y agrupada por secciones. Las instalaciones antiguas con claves en español se migran automáticamente con backup.

Claves principales:

| Sección | Uso |
| --- | --- |
| `debug` | Registro extra en consola (HTTP, auto-recompensa y traza `[VoteTrace/...]` de `/voto40`). Apagado por defecto. |
| `api.key` | Clave privada del servidor. También se envía como `Authorization: Bearer` en el reclamo v3. |
| `api.readTimeout` / `api.connectTimeout` | Timeouts HTTP en milisegundos. |
| `messages.prefix` | Prefijo de los mensajes del plugin. |
| `messages.voteClaim` | Mensaje al entregar una recompensa de voto. |
| `messages.alreadyRewarded` | Mensaje cuando el jugador ya recibió la recompensa. Usa `%time%`. |
| `messages.commands.*` | Errores genéricos de comandos (`noPermission`, `onlyPlayer`, `unexpectedError`, `cooldown`). |
| `messages.apiException` | Error genérico de la API en voto y estadísticas. |
| `messages.vote.*` | Mensajes del flujo de voto (`checking`, `notVotedToday`, `error`, `rewardSaveFailed`, `deliveryFailed`, `ackFailed`). |
| `messages.stats.lines` | Lista de líneas de `/stats40`. Placeholders: `%server%`, `%rank%`, `%votesToday%`, `%rewardedToday%`, `%votesWeek%`, `%rewardedWeek%`, `%lastVotes%`. |
| `messages.streak.own.lines` / `admin.lines` / `usage.lines` | Listas de líneas de `/streak40`. |
| `messages.streak.formats.*` | Textos para fechas y disponibilidad de voto en rachas. |
| `messages.metrics.lines` | Lista de líneas de `/metrics40`. Placeholders: `%apiRequests%`, `%apiFailures%`, `%retries%`, `%httpRejections%`, `%voteChecks%`, `%rewardsDelivered%`. |
| `messages.reload.lines` | Lista de líneas de `/reload40`. Placeholder: `%version%`. |
| `messages.test.lines` | Lista de líneas de `/test40`. Placeholder: `%voteClaim%`. |
| `broadcast.enabled` / `broadcast.message` | Activa y configura el anuncio global de recompensa. |
| `rewards.commands` | Comandos ejecutados desde consola al premiar un voto. |
| `streakRewards` | Premios por hitos de racha. |
| `autoReward` | Reintentos automáticos tras mostrar el enlace (polling de votos pendientes v3). |
| `voteReminder` | Recordatorios locales para volver a votar. |

Si un jugador vota en la web y `/voto40` no se comporta bien, pon `debug: true`, haz `/reload40`, reproduce el caso y copia las líneas `[VoteTrace/...]` de la consola (o del chat si tienes el permiso `40servidores.votedebug`). No compartas `api.key`. Cuando termines, vuelve a poner `debug: false`.

API de votos (v3)
------------
El reclamo con `/voto40` usa el protocolo v3 de 40ServidoresMC. La URL base está fija en el código (`https://www.40servidoresmc.es`), no en `config.yml`:

1. `GET /api/vote/v3/pending?nick=...` con `Authorization: Bearer <api.key>`
2. Si hay votos pendientes: se entrega el premio local y luego `POST /api/vote/v3/ack`
3. Si no hay pendientes y `puede_votar_ya=true`: se muestra el enlace directo `https://www.40servidoresmc.es/{slug}/votar` (el `slug` viene en la respuesta pending).
4. Si no hay pendientes y `puede_votar_ya=false`: mensaje de ya recompensado con `siguiente_voto`

Las estadísticas (`/stats40`) siguen usando el endpoint legacy `api2.php?estadisticas=1` (no hay equivalente v3).

Cooldown de voto (regla oficial)
------------
La web de 40ServidoresMC permite un nuevo voto cuando se cumplen **las dos** condiciones:

- no haber votado ese mismo **día natural UTC**
- haber pasado al menos **12 horas** desde el voto anterior

En la práctica el plugin calcula el próximo momento válido como el **más tardío** entre “último voto + 12 h” y “medianoche UTC del día siguiente”. Por eso a veces el tiempo restante se acerca a ~24 h (por ejemplo si votaste temprano en el día UTC): no es un bug ni una ventana fija de 24 horas; es el máximo de esas dos condiciones.

El plugin usa esa misma regla para `%time%` en `messages.alreadyRewarded`, recordatorios y placeholders de disponibilidad. En v3, la web también puede devolver `siguiente_voto` en la respuesta pending.

### Qué ve el jugador con `/voto40`

| Situación | Qué debería ver |
| --- | --- |
| Pending vacío y `puede_votar_ya=true` | Enlace de voto (`messages.vote.notVotedToday` + `/{slug}/votar`). |
| Pending con votos | Recompensa (`messages.voteClaim`), comandos de `rewards.commands`, y ack a la web. |
| Pending vacío y `puede_votar_ya=false` | Mensaje “ya recompensado” con tiempo desde `siguiente_voto` (o cooldown local). |
| Premio entregado pero el ack falla | Mensaje de ack fallido; el siguiente `/voto40` reintenta el ack sin volver a dar el premio. |

Las claves antiguas como `clave`, `mensaje`, `tag` y `comandosCustom` siguen funcionando como fallback, pero el migrador las moverá a la estructura nueva para evitar confusión. Si falta una clave nueva de `messages.*`, el plugin usa el texto en español embebido en el código como respaldo. Las configs v9 con claves por línea (`messages.metrics.header`, etc.) se convierten automáticamente a listas si falta `messages.*.lines`.

Los bloques multi-línea usan listas YAML. Puedes reordenar, añadir u omitir entradas, e insertar líneas vacías con `- ""`. Si una línea contiene un placeholder cuyo valor está vacío (por ejemplo `%lastVotes%` sin votos recientes), esa línea no se imprime.

Mensajes no configurables
------------
Estos textos están fijados en el código y no tienen claves en `config.yml`:

- Avisos del **updater** (actualización disponible, al día, error de comprobación).
- Mensaje de **clave API incorrecta** (`invalidApiKey`), que incluye la URL oficial de 40ServidoresMC.

Placeholders en la configuración
------------
Estos placeholders los resuelve el propio plugin al enviar mensajes o ejecutar comandos. No requieren PlaceholderAPI.

| Placeholder | Dónde se usa | Descripción |
| --- | --- | --- |
| `%player%` | `rewards.commands`, `broadcast.message`, `streakRewards`, `messages.streak.*` | Nombre del jugador premiado o consultado. |
| `%time%` | `messages.alreadyRewarded`, `messages.commands.cooldown`, `messages.streak.formats.canVoteIn` | Tiempo restante hasta poder volver a votar o usar un comando. |
| `%command%` | `messages.commands.cooldown` | Etiqueta del comando en cooldown. |
| `%web%` | Prefijo de enlace cuando el jugador no ha votado (se concatena con la URL de voto del servidor). | Texto antes del enlace clicable. |
| `%server%` / `%rank%` | `messages.stats.lines` | Nombre del servidor y posición en el ranking. |
| `%votesToday%` / `%rewardedToday%` / `%votesWeek%` / `%rewardedWeek%` | `messages.stats.lines` | Contadores de votos. |
| `%lastVotes%` | `messages.stats.lines` | Lista formateada de últimos votos. Se omite la línea si no hay datos. |
| `%apiRequests%` / `%apiFailures%` / `%retries%` / `%httpRejections%` / `%voteChecks%` / `%rewardsDelivered%` | `messages.metrics.lines` | Contadores de métricas internas. |
| `%streak%` / `%bestStreak%` | `messages.streak.own.lines`, `messages.streak.admin.lines`, `streakRewards` | Racha actual o mejor racha registrada. |
| `%lastVote%` / `%canVote%` | `messages.streak.own.lines` | Texto amigable del último voto o cuándo puede votar de nuevo. |
| `%player%` / `%uuid%` / `%milestones%` | `messages.streak.admin.lines`, `streakRewards` | Datos del jugador consultado o premiado. |
| `%days%` | `messages.streak.formats.daysAgo` | Días transcurridos desde el último voto. |
| `%version%` | `messages.reload.lines` | Versión del plugin en ejecución. |
| `%voteClaim%` | `messages.test.lines` | Mensaje de recompensa configurado en `messages.voteClaim`. |

Las plantillas por defecto usan `%player%`. Las configuraciones antiguas con `{0}` siguen funcionando como compatibilidad hacia atrás.

PlaceholderAPI
------------
La integración con PlaceholderAPI es opcional. Si PlaceholderAPI no está instalado, el plugin cargará igual. Los placeholders no hacen llamadas HTTP directas: usan caché, datos locales o valores fallback configurables.

Identificador de la expansión: `%40servidoresmc_<nombre>%`.

### Servidor

| Placeholder | Descripción | Ejemplo |
| --- | --- | --- |
| `%40servidoresmc_server_name%` | Nombre del servidor en 40ServidoresMC desde la caché. | `Mi Servidor` |
| `%40servidoresmc_server_rank%` | Puesto del servidor desde la caché de estadísticas. | `12` |
| `%40servidoresmc_server_votes%` | Votos totales si la API los devuelve. | `348` |
| `%40servidoresmc_votes_today%` | Votos recibidos hoy desde la caché. | `18` |
| `%40servidoresmc_votes_week%` | Votos recibidos esta semana desde la caché. | `92` |
| `%40servidoresmc_votes_month%` | Votos mensuales si la API los devuelve. | `245` |
| `%40servidoresmc_rewarded_today%` | Votos premiados hoy desde la caché. | `16` |
| `%40servidoresmc_rewarded_week%` | Votos premiados esta semana desde la caché. | `74` |

### Jugador

Requieren contexto de jugador (por ejemplo, `/papi parse <jugador> ...`).

| Placeholder | Descripción | Ejemplo |
| --- | --- | --- |
| `%40servidoresmc_player_last_vote%` | Fecha y hora del último voto local registrado. Usa `placeholderapi.formats.dateTime`. | `18/06/2026 15:30` |
| `%40servidoresmc_player_last_vote_ago%` | Tiempo transcurrido desde el último voto local. | `2h 15m` |
| `%40servidoresmc_player_can_vote%` | Indica si el jugador puede votar según el cooldown local. | `true` |
| `%40servidoresmc_player_next_vote_in%` | Tiempo restante para volver a votar según datos locales. | `21h 45m` |
| `%40servidoresmc_player_streak%` | Racha actual guardada localmente. | `4` |
| `%40servidoresmc_player_best_streak%` | Mejor racha guardada localmente. | `9` |
| `%40servidoresmc_player_next_streak_reward%` | Próximo hito configurado en `streakRewards`. | `7` |
| `%40servidoresmc_player_pending_reward%` | Indica si hay una comprobación de autoReward pendiente. | `false` |

### Sistema

| Placeholder | Descripción | Ejemplo |
| --- | --- | --- |
| `%40servidoresmc_api_status%` | Estado de la última llamada API: `ok`, `error` o `unknown`. | `ok` |
| `%40servidoresmc_cache_age%` | Edad de la caché de estadísticas del servidor. | `45s` |

Cuando un dato todavía no existe, el placeholder devuelve `N/A`, `0` o `false`, según corresponda. Los formatos configurables viven en `placeholderapi.formats`.

QA manual con PlaceholderAPI
------------
1. Instala PlaceholderAPI junto al plugin en un servidor Bukkit/Spigot compatible.
2. Arranca el servidor y confirma que no hay errores en consola.
3. Ejecuta placeholders antes de votar para comprobar los fallbacks.
4. Ejecuta `/stats40` para poblar la caché de estadísticas y vuelve a consultar los placeholders del servidor.
5. Ejecuta el flujo de `/voto40` y revisa los placeholders de jugador, racha y recompensa pendiente.

Build reproducible
------------
El proyecto genera bytecode Java 8 (`sourceCompatibility` y `targetCompatibility` en `1.8`) para mantener compatibilidad con servidores antiguos. Para compilar de forma reproducible usa un JDK LTS compatible con Gradle 8.0 y el plugin Lombok actual; JDK 17 es la opción recomendada para CI y desarrollo local.

JDK 21 no es compatible con la versión actual de `io.freefair.lombok` usada por el proyecto y puede fallar durante la compilación con errores internos de `javac`. Si se quiere compilar oficialmente con JDK 21, actualiza el plugin Lombok en un cambio independiente y valida todos los módulos antes de mezclar cambios funcionales.

Comandos de validación:

```
./gradlew clean test --no-daemon
./gradlew clean test shadowJar --no-daemon
```
