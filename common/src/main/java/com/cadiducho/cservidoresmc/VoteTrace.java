package com.cadiducho.cservidoresmc;

import com.cadiducho.cservidoresmc.api.CSCommandSender;
import com.cadiducho.cservidoresmc.api.CSPlugin;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vote flow tracer for admins and 40ServidoresMC support.
 * Active only when {@code debug: true}. When off, every method returns immediately.
 */
public final class VoteTrace {

    private final boolean enabled;
    private final String sessionId;
    private final CSPlugin plugin;
    private final CSCommandSender tellTarget;
    private final boolean tellPlayer;
    private final int maxBodyChars;
    private final boolean logFullIp;
    private final AtomicBoolean finished = new AtomicBoolean();

    private VoteTrace(boolean enabled, String sessionId, CSPlugin plugin, CSCommandSender tellTarget,
                      boolean tellPlayer, int maxBodyChars, boolean logFullIp) {
        this.enabled = enabled;
        this.sessionId = sessionId;
        this.plugin = plugin;
        this.tellTarget = tellTarget;
        this.tellPlayer = tellPlayer;
        this.maxBodyChars = Math.max(80, maxBodyChars);
        this.logFullIp = logFullIp;
    }

    public static VoteTrace noop() {
        return new VoteTrace(false, "", null, null, false, 800, false);
    }

    public static VoteTrace start(CSPlugin plugin, CSCommandSender sender) {
        if (plugin == null || !plugin.isDebug()) {
            return noop();
        }
        // Console always gets the full trace. Chat echo only for operators with the permission.
        CSCommandSender target = null;
        if (sender != null && sender.hasPermission("40servidores.votedebug")) {
            target = sender;
        }
        String sessionId = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0x1000000));
        VoteTrace trace = new VoteTrace(true, sessionId, plugin, target, target != null, 800, false);
        trace.step("START", "nick=" + safe(sender == null ? null : sender.getName())
                + " uuid=" + safe(sender == null ? null : sender.getUniqueId())
                + " plugin=" + plugin.getPluginVersion()
                + " platform=" + plugin.getServerPlatform()
                + "/" + plugin.getServerVersion());
        return trace;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void step(String code, String detail) {
        if (!enabled || finished.get()) {
            return;
        }
        String line = "[VoteTrace/" + sessionId + "] " + code + (detail == null || detail.isEmpty() ? "" : " " + detail);
        plugin.log(line);
        if (tellPlayer && tellTarget != null) {
            tellTarget.sendMessageWithTag("&7" + code + (detail == null || detail.isEmpty() ? "" : " &8" + trimForChat(detail)));
        }
    }

    public void pendingHttp(String nick, int status, long millis, String path) {
        step("PENDING_HTTP", "nick=" + safe(nick) + " method=GET path=" + safe(path)
                + " status=" + status + " ms=" + millis);
    }

    public void pendingBody(int voteCount, boolean puedeVotarYa, String siguienteVoto, int reserva, String rawJson) {
        step("PENDING_BODY", "votos=" + voteCount
                + " puede_votar_ya=" + puedeVotarYa
                + " siguiente_voto=" + safe(siguienteVoto)
                + " reserva_segundos=" + reserva
                + " raw=" + truncateBody(rawJson));
    }

    public void branch(String name) {
        step("BRANCH", name);
    }

    public void delivery(boolean ok, int commandsOk, int commandsTotal, boolean online, String markResult) {
        step("DELIVER", "ok=" + ok
                + " commands=" + commandsOk + "/" + commandsTotal
                + " online=" + online
                + " mark=" + safe(markResult));
    }

    public void ackHttp(int status, boolean entregado, String ids, String userIpFlag) {
        step("ACK_HTTP", "status=" + status
                + " entregado=" + entregado
                + " ids=" + safe(ids)
                + " user_ip=" + safe(userIpFlag));
    }

    public void ackBody(String rawJson) {
        step("ACK_BODY", "raw=" + truncateBody(rawJson));
    }

    public void recheck(int attempt, long delaySeconds) {
        step("RECHECK", "attempt=" + attempt + " delaySeconds=" + delaySeconds);
    }

    public void earlyExit(String reason) {
        step("EARLY_EXIT", reason);
    }

    public void error(String message) {
        step("ERROR", safe(message));
    }

    public void retryAcks(int idCount, boolean success) {
        step("RETRY_ACK", "ids=" + idCount + " success=" + success);
    }

    public void done() {
        if (!enabled || !finished.compareAndSet(false, true)) {
            return;
        }
        stepUnlocked("DONE", "");
    }

    public void aborted(String reason) {
        if (!enabled || !finished.compareAndSet(false, true)) {
            return;
        }
        stepUnlocked("ABORTED", safe(reason));
    }

    public String describeIp(String userIp) {
        if (!enabled) {
            return "";
        }
        if (userIp == null || userIp.isEmpty()) {
            return "empty";
        }
        if (logFullIp) {
            return userIp;
        }
        return "set";
    }

    private void stepUnlocked(String code, String detail) {
        String line = "[VoteTrace/" + sessionId + "] " + code + (detail == null || detail.isEmpty() ? "" : " " + detail);
        plugin.log(line);
        if (tellPlayer && tellTarget != null) {
            tellTarget.sendMessageWithTag("&7" + code + (detail == null || detail.isEmpty() ? "" : " &8" + trimForChat(detail)));
        }
    }

    private String truncateBody(String raw) {
        if (raw == null) {
            return "";
        }
        String compact = raw.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= maxBodyChars) {
            return compact;
        }
        return compact.substring(0, maxBodyChars) + "...";
    }

    private String trimForChat(String detail) {
        if (detail.length() <= 120) {
            return detail;
        }
        return detail.substring(0, 120) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
