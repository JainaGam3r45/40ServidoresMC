package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.config.CSConfiguration;
import com.cadiducho.cservidoresmc.model.ServerStats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginMessages {

    static final String DEFAULT_ALREADY_REWARDED = "&aYa has votado y recibido tu recompensa. Podrás volver a votar en &e%time%&a.";
    private static final String DEFAULT_NO_PERMISSION = "&cNo tienes permiso para usar este comando";
    private static final String DEFAULT_ONLY_PLAYER = "&cEste comando sólo puede ser ejecutado por usuarios";
    private static final String DEFAULT_UNEXPECTED_ERROR = "&cHa ocurrido un error inesperado";
    private static final String DEFAULT_COOLDOWN = "&ePodrás volver a ejecutar &6/%command% &een &6%time%&e.";
    static final String DEFAULT_INVALID_API_KEY = "&cClave incorrecta. Entra en &bhttps://40servidoresmc.es/miservidor.php &cy cambia esta.";
    private static final String DEFAULT_API_EXCEPTION = "&cHa ocurrido una excepción. Avisa a un administrador";
    private static final String DEFAULT_VOTE_CHECKING = "&7Obteniendo voto...";
    private static final String DEFAULT_NOT_VOTED_TODAY = "&6No has votado hoy! Puedes hacerlo en &a ";
    private static final String DEFAULT_VOTE_ALREADY_CLAIMED = "&aGracias por votar, pero ya has obtenido tu premio!";
    private static final String DEFAULT_VOTE_ERROR = "&7Ha ocurrido un error. Prueba más tarde o avisa a un adminsitrador";
    private static final String DEFAULT_REWARD_SAVE_FAILED = "&cNo se pudo registrar tu voto premiado. Avisa a un administrador.";
    private static final String DEFAULT_STATS_EXCEPTION = "&cHa ocurrido una excepción. Revisa la consola o avisa a un administrador";
    private static final String DEFAULT_STREAK_UNAVAILABLE = "&cEl sistema de rachas no está disponible.";
    private static final String DEFAULT_STREAK_LOADING = "&eLos datos de racha de &6%player% &ese están cargando. Inténtalo de nuevo en unos segundos.";
    private static final String DEFAULT_STREAK_RESET_SUCCESS = "&aRacha de &e%player% &areiniciada correctamente.";
    private static final String DEFAULT_STREAK_RESET_FAILED = "&cNo se pudo reiniciar la racha de &e%player%&c.";
    private static final String DEFAULT_STREAK_NONE = "ninguno";
    private static final String DEFAULT_STREAK_TODAY = "hoy";
    private static final String DEFAULT_STREAK_YESTERDAY = "ayer";
    private static final String DEFAULT_STREAK_DAYS_AGO = "hace %days% días";
    private static final String DEFAULT_STREAK_NO_VOTES_YET = "sin votos registrados";
    private static final String DEFAULT_STREAK_CAN_VOTE_NOW = "ahora";
    private static final String DEFAULT_STREAK_CAN_VOTE_IN = "en %time%";

    private static final List<String> DEFAULT_METRICS_LINES = Arrays.asList(
            "&9==> &7Métricas internas desde el último arranque",
            "&bPeticiones API: &6%apiRequests%",
            "&bFallos API: &6%apiFailures%",
            "&bReintentos: &6%retries%",
            "&bRechazos HTTP: &6%httpRejections%",
            "&bComprobaciones de voto: &6%voteChecks%",
            "&bRecompensas entregadas: &6%rewardsDelivered%"
    );

    private static final List<String> DEFAULT_STATS_LINES = Arrays.asList(
            "&9==> &7%server% &festá en el TOP &a%rank%",
            "&bVotos hoy: &6%votesToday%",
            "&bVotos premiados hoy: &6%rewardedToday%",
            "&bVotos semanales: &6%votesWeek%",
            "&bVotos premiados semanales: &6%rewardedWeek%",
            "&bÚltimos 20 votos: %lastVotes%"
    );

    private static final List<String> DEFAULT_STREAK_OWN_LINES = Arrays.asList(
            "&9Tu racha de votos",
            "&bRacha actual: &6%streak% días",
            "&bMejor racha: &6%bestStreak% días",
            "&bÚltimo voto: &6%lastVote%",
            "&bPuedes volver a votar: &6%canVote%",
            "&a¡Vota hoy para mantener tu racha!"
    );

    private static final List<String> DEFAULT_STREAK_ADMIN_LINES = Arrays.asList(
            "&9Racha de &e%player%&9:",
            "&bRacha actual: &6%streak%",
            "&bMejor racha: &6%bestStreak%",
            "&bÚltimo día de voto: &6%lastVote%",
            "&bUUID: &6%uuid%",
            "&bMilestones premiados: &6%milestones%"
    );

    private static final List<String> DEFAULT_STREAK_USAGE_LINES = Arrays.asList(
            "&cUso: /streak40 view <jugador>",
            "&cUso: /streak40 reset <jugador>"
    );

    private static final List<String> DEFAULT_RELOAD_LINES = Arrays.asList(
            "&aConfiguración recargada correctamente",
            "&aFuncionando la versión %version%"
    );

    private static final List<String> DEFAULT_TEST_LINES = Arrays.asList(
            "&bPlataforma de test para 40ServidoresMC:",
            "",
            "%voteClaim%"
    );

    private final CSConfiguration config;

    public PluginMessages(CSConfiguration config) {
        this.config = config;
    }

    public static void sendLines(CSCommandSender sender, List<String> lines) {
        for (String line : lines) {
            if (line.isEmpty()) {
                sender.sendMessage("");
            } else {
                sender.sendMessageWithTag(line);
            }
        }
    }

    public String voteClaim() {
        return config.getString("messages.voteClaim", "mensaje", "");
    }

    public String alreadyRewarded(String time) {
        String message = config.getString("messages.alreadyRewarded", "alreadyRewardedMessage", DEFAULT_ALREADY_REWARDED);
        return message.replace("%time%", time == null ? "" : time);
    }

    public String noPermission() {
        return get("messages.commands.noPermission", DEFAULT_NO_PERMISSION);
    }

    public String onlyPlayer() {
        return get("messages.commands.onlyPlayer", DEFAULT_ONLY_PLAYER);
    }

    public String unexpectedError() {
        return get("messages.commands.unexpectedError", DEFAULT_UNEXPECTED_ERROR);
    }

    public String cooldown(String command, String time) {
        return replace(get("messages.commands.cooldown", DEFAULT_COOLDOWN),
                "%command%", command,
                "%time%", time);
    }

    public String invalidApiKey() {
        return DEFAULT_INVALID_API_KEY;
    }

    public String apiException() {
        return get("messages.apiException", DEFAULT_API_EXCEPTION);
    }

    public String voteChecking() {
        return get("messages.vote.checking", DEFAULT_VOTE_CHECKING);
    }

    public String notVotedTodayPrefix() {
        return get("messages.vote.notVotedToday", DEFAULT_NOT_VOTED_TODAY);
    }

    public String voteAlreadyClaimed() {
        return get("messages.vote.alreadyClaimed", DEFAULT_VOTE_ALREADY_CLAIMED);
    }

    public String voteError() {
        return get("messages.vote.error", DEFAULT_VOTE_ERROR);
    }

    public String rewardSaveFailed() {
        return get("messages.vote.rewardSaveFailed", DEFAULT_REWARD_SAVE_FAILED);
    }

    public String statsException() {
        return get("messages.stats.exception", DEFAULT_STATS_EXCEPTION);
    }

    public List<String> metricsLines(PluginMetrics metrics) {
        Map<String, String> values = new HashMap<>();
        values.put("apiRequests", String.valueOf(metrics.getApiRequests()));
        values.put("apiFailures", String.valueOf(metrics.getApiFailures()));
        values.put("retries", String.valueOf(metrics.getRetries()));
        values.put("httpRejections", String.valueOf(metrics.getHttpRejections()));
        values.put("voteChecks", String.valueOf(metrics.getVoteChecks()));
        values.put("rewardsDelivered", String.valueOf(metrics.getRewardsDelivered()));
        return resolveLines(metricsTemplates(), values);
    }

    public List<String> statsLines(ServerStats serverStats, String lastVotesFormatted) {
        Map<String, String> values = new HashMap<>();
        values.put("server", serverStats.getServerName() == null ? "" : serverStats.getServerName());
        values.put("rank", String.valueOf(serverStats.getPosition()));
        values.put("votesToday", String.valueOf(serverStats.getDayVotes()));
        values.put("rewardedToday", String.valueOf(serverStats.getRewardedDayVotes()));
        values.put("votesWeek", String.valueOf(serverStats.getWeekVotes()));
        values.put("rewardedWeek", String.valueOf(serverStats.getRewardedWeekVotes()));
        values.put("lastVotes", lastVotesFormatted == null ? "" : lastVotesFormatted);
        return resolveLines(statsTemplates(), values);
    }

    public List<String> streakOwnLines(int streak, int bestStreak, String lastVote, String canVote) {
        Map<String, String> values = new HashMap<>();
        values.put("streak", String.valueOf(streak));
        values.put("bestStreak", String.valueOf(bestStreak));
        values.put("lastVote", lastVote == null ? "" : lastVote);
        values.put("canVote", canVote == null ? "" : canVote);
        return resolveLines(streakOwnTemplates(), values);
    }

    public List<String> streakAdminLines(String player, int streak, int bestStreak, String lastVote, String uuid, String milestones) {
        Map<String, String> values = new HashMap<>();
        values.put("player", player == null ? "" : player);
        values.put("streak", String.valueOf(streak));
        values.put("bestStreak", String.valueOf(bestStreak));
        values.put("lastVote", lastVote == null ? "" : lastVote);
        values.put("uuid", uuid == null ? "" : uuid);
        values.put("milestones", milestones == null ? "" : milestones);
        return resolveLines(streakAdminTemplates(), values);
    }

    public List<String> streakUsageLines() {
        return resolveLines(streakUsageTemplates(), Collections.emptyMap());
    }

    public List<String> reloadLines(String version) {
        Map<String, String> values = Collections.singletonMap("version", version == null ? "" : version);
        return resolveLines(reloadTemplates(), values);
    }

    public List<String> testLines() {
        Map<String, String> values = Collections.singletonMap("voteClaim", voteClaim());
        return resolveLines(testTemplates(), values);
    }

    public String streakUnavailable() {
        return get("messages.streak.unavailable", DEFAULT_STREAK_UNAVAILABLE);
    }

    public String streakLoading(String player) {
        return replace(get("messages.streak.loading", DEFAULT_STREAK_LOADING), "%player%", player);
    }

    public String streakResetSuccess(String player) {
        return replace(get("messages.streak.resetSuccess", DEFAULT_STREAK_RESET_SUCCESS), "%player%", player);
    }

    public String streakResetFailed(String player) {
        return replace(get("messages.streak.resetFailed", DEFAULT_STREAK_RESET_FAILED), "%player%", player);
    }

    public String streakNone() {
        return get("messages.streak.formats.none", DEFAULT_STREAK_NONE);
    }

    public String streakToday() {
        return get("messages.streak.formats.today", DEFAULT_STREAK_TODAY);
    }

    public String streakYesterday() {
        return get("messages.streak.formats.yesterday", DEFAULT_STREAK_YESTERDAY);
    }

    public String streakDaysAgo(long days) {
        return replace(get("messages.streak.formats.daysAgo", DEFAULT_STREAK_DAYS_AGO), "%days%", String.valueOf(days));
    }

    public String streakNoVotesYet() {
        return get("messages.streak.formats.noVotesYet", DEFAULT_STREAK_NO_VOTES_YET);
    }

    public String streakCanVoteNow() {
        return get("messages.streak.formats.canVoteNow", DEFAULT_STREAK_CAN_VOTE_NOW);
    }

    public String streakCanVoteIn(String time) {
        return replace(get("messages.streak.formats.canVoteIn", DEFAULT_STREAK_CAN_VOTE_IN), "%time%", time);
    }

    private List<String> metricsTemplates() {
        List<String> configured = config.getStringList("messages.metrics.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.metrics.header", null) != null) {
            return legacyMetricsLines();
        }
        return DEFAULT_METRICS_LINES;
    }

    private List<String> statsTemplates() {
        List<String> configured = config.getStringList("messages.stats.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.stats.header", null) != null) {
            return legacyStatsLines();
        }
        return DEFAULT_STATS_LINES;
    }

    private List<String> streakOwnTemplates() {
        List<String> configured = config.getStringList("messages.streak.own.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.streak.ownHeader", null) != null) {
            return legacyStreakOwnLines();
        }
        return DEFAULT_STREAK_OWN_LINES;
    }

    private List<String> streakAdminTemplates() {
        List<String> configured = config.getStringList("messages.streak.admin.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.streak.adminHeader", null) != null) {
            return legacyStreakAdminLines();
        }
        return DEFAULT_STREAK_ADMIN_LINES;
    }

    private List<String> streakUsageTemplates() {
        List<String> configured = config.getStringList("messages.streak.usage.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.streak.usageView", null) != null) {
            return Arrays.asList(
                    get("messages.streak.usageView", DEFAULT_STREAK_USAGE_LINES.get(0)),
                    get("messages.streak.usageReset", DEFAULT_STREAK_USAGE_LINES.get(1))
            );
        }
        return DEFAULT_STREAK_USAGE_LINES;
    }

    private List<String> reloadTemplates() {
        List<String> configured = config.getStringList("messages.reload.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.reload.success", null) != null) {
            return Arrays.asList(
                    get("messages.reload.success", DEFAULT_RELOAD_LINES.get(0)),
                    get("messages.reload.version", DEFAULT_RELOAD_LINES.get(1))
            );
        }
        return DEFAULT_RELOAD_LINES;
    }

    private List<String> testTemplates() {
        List<String> configured = config.getStringList("messages.test.lines", null);
        if (configured != null) {
            return configured;
        }
        if (config.getString("messages.test.header", null) != null) {
            return Arrays.asList(
                    get("messages.test.header", DEFAULT_TEST_LINES.get(0)),
                    "",
                    "%voteClaim%"
            );
        }
        return DEFAULT_TEST_LINES;
    }

    private List<String> legacyMetricsLines() {
        List<String> lines = new ArrayList<>();
        lines.add(get("messages.metrics.header", DEFAULT_METRICS_LINES.get(0)));
        lines.add(legacyMetricLine("messages.metrics.apiRequests", DEFAULT_METRICS_LINES.get(1), "apiRequests"));
        lines.add(legacyMetricLine("messages.metrics.apiFailures", DEFAULT_METRICS_LINES.get(2), "apiFailures"));
        lines.add(legacyMetricLine("messages.metrics.retries", DEFAULT_METRICS_LINES.get(3), "retries"));
        lines.add(legacyMetricLine("messages.metrics.httpRejections", DEFAULT_METRICS_LINES.get(4), "httpRejections"));
        lines.add(legacyMetricLine("messages.metrics.voteChecks", DEFAULT_METRICS_LINES.get(5), "voteChecks"));
        lines.add(legacyMetricLine("messages.metrics.rewardsDelivered", DEFAULT_METRICS_LINES.get(6), "rewardsDelivered"));
        return lines;
    }

    private String legacyMetricLine(String path, String fallback, String placeholder) {
        return get(path, fallback).replace("%value%", "%" + placeholder + "%");
    }

    private List<String> legacyStatsLines() {
        List<String> lines = new ArrayList<>();
        lines.add(get("messages.stats.header", DEFAULT_STATS_LINES.get(0)));
        lines.add(legacyStatsLine("messages.stats.votesToday", DEFAULT_STATS_LINES.get(1), "votesToday"));
        lines.add(legacyStatsLine("messages.stats.rewardedToday", DEFAULT_STATS_LINES.get(2), "rewardedToday"));
        lines.add(legacyStatsLine("messages.stats.votesWeek", DEFAULT_STATS_LINES.get(3), "votesWeek"));
        lines.add(legacyStatsLine("messages.stats.rewardedWeek", DEFAULT_STATS_LINES.get(4), "rewardedWeek"));
        lines.add(get("messages.stats.lastVotesHeader", DEFAULT_STATS_LINES.get(5)).replace("%votes%", "%lastVotes%"));
        return lines;
    }

    private String legacyStatsLine(String path, String fallback, String placeholder) {
        return get(path, fallback).replace("%votes%", "%" + placeholder + "%");
    }

    private List<String> legacyStreakOwnLines() {
        List<String> lines = new ArrayList<>();
        lines.add(get("messages.streak.ownHeader", DEFAULT_STREAK_OWN_LINES.get(0)));
        lines.add(get("messages.streak.ownCurrent", DEFAULT_STREAK_OWN_LINES.get(1)));
        lines.add(get("messages.streak.ownBest", DEFAULT_STREAK_OWN_LINES.get(2)));
        lines.add(get("messages.streak.ownLastVote", DEFAULT_STREAK_OWN_LINES.get(3)));
        lines.add(get("messages.streak.ownCanVote", DEFAULT_STREAK_OWN_LINES.get(4)));
        lines.add(get("messages.streak.ownReminder", DEFAULT_STREAK_OWN_LINES.get(5)));
        return lines;
    }

    private List<String> legacyStreakAdminLines() {
        List<String> lines = new ArrayList<>();
        lines.add(get("messages.streak.adminHeader", DEFAULT_STREAK_ADMIN_LINES.get(0)));
        lines.add(get("messages.streak.adminCurrent", DEFAULT_STREAK_ADMIN_LINES.get(1)));
        lines.add(get("messages.streak.adminBest", DEFAULT_STREAK_ADMIN_LINES.get(2)));
        lines.add(get("messages.streak.adminLastDay", DEFAULT_STREAK_ADMIN_LINES.get(3)));
        lines.add(get("messages.streak.adminUuid", DEFAULT_STREAK_ADMIN_LINES.get(4)));
        lines.add(get("messages.streak.adminMilestones", DEFAULT_STREAK_ADMIN_LINES.get(5)));
        return lines;
    }

    private List<String> resolveLines(List<String> templates, Map<String, String> values) {
        List<String> resolved = new ArrayList<>();
        for (String template : templates) {
            if (template == null || template.trim().isEmpty()) {
                resolved.add("");
                continue;
            }
            String line = replacePlaceholders(template, values);
            if (shouldSkipConditionalLine(template, values, line)) {
                continue;
            }
            resolved.add(line);
        }
        return resolved;
    }

    private boolean shouldSkipConditionalLine(String template, Map<String, String> values, String resolved) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "%" + entry.getKey() + "%";
            if (template.contains(placeholder) && (entry.getValue() == null || entry.getValue().trim().isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private String replacePlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("%" + entry.getKey() + "%", value);
        }
        return result;
    }

    private String get(String path, String fallback) {
        String value = config.getString(path, null);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    private static String replace(String template, String... pairs) {
        String result = template == null ? "" : template;
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            String key = pairs[index];
            String value = pairs[index + 1] == null ? "" : pairs[index + 1];
            result = result.replace(key, value);
        }
        return result;
    }
}
