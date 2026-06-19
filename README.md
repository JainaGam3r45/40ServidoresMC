40ServidoresMC
==========

Inicio
------------
40ServidoresMC es una web de rankings de servidores Online. 
Con este plugin podrás otorgar a tus jugadores premios por votar a tu servidor, y potenciar así el puesto en el ranking.

Puedes encontrar mucha más información en nuestra [Wiki](https://github.com/Cadiducho/40ServidoresMC/wiki) y aprende todo lo necesario sobre este plugin!

Configuración
------------
Las versiones actuales usan una estructura de `config.yml` en inglés y agrupada por secciones. Las instalaciones antiguas con claves en español se migran automáticamente con backup.

Claves principales:

| Sección | Uso |
| --- | --- |
| `api.key` | Clave privada del servidor en 40ServidoresMC. |
| `api.readTimeout` / `api.connectTimeout` | Timeouts HTTP en milisegundos. |
| `messages.prefix` | Prefijo de los mensajes del plugin. |
| `messages.voteClaim` | Mensaje al entregar una recompensa de voto. |
| `messages.alreadyRewarded` | Mensaje cuando el jugador ya recibió la recompensa. Usa `%time%`. |
| `broadcast.enabled` / `broadcast.message` | Activa y configura el anuncio global de recompensa. |
| `rewards.commands` | Comandos ejecutados desde consola al premiar un voto. |
| `streakRewards` | Premios por hitos de racha. |
| `autoReward` | Reintentos automáticos tras votar en la web. |
| `voteReminder` | Recordatorios locales para volver a votar. |

Las claves antiguas como `clave`, `mensaje`, `tag` y `comandosCustom` siguen funcionando como fallback, pero el migrador las moverá a la estructura nueva para evitar confusión.

Placeholders en la configuración
------------
Estos placeholders los resuelve el propio plugin al enviar mensajes o ejecutar comandos. No requieren PlaceholderAPI.

| Placeholder | Dónde se usa | Descripción |
| --- | --- | --- |
| `%player%` | `rewards.commands`, `broadcast.message`, `streakRewards` | Nombre del jugador premiado. |
| `%time%` | `messages.alreadyRewarded` | Tiempo restante hasta poder volver a votar. |
| `%uuid%` | `streakRewards` | UUID del jugador al entregar un premio de racha. |
| `%streak%` | `streakRewards` | Racha actual al entregar un premio de racha. |

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
