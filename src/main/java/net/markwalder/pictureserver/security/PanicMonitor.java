package net.markwalder.pictureserver.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.markwalder.pictureserver.Logger;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings.PanicSettings;

public final class PanicMonitor {

    private static final Set<String> KNOWN_ATTACK_PATH_PREFIXES = Set.of(
            "/wp-admin", "/wp-login", "/.env", "/.git", "/.htaccess",
            "/admin", "/phpmyadmin", "/config", "/etc/", "/proc/",
            "/cgi-bin", "/xmlrpc.php", "/shell", "/console"
    );

    private final PanicSettings settings;
    private final SessionManager sessionManager;
    private final Runnable shutdownAction;
    private final AtomicBoolean panicking = new AtomicBoolean(false);
    private final Map<String, Deque<Long>> eventLog = new ConcurrentHashMap<>();

    public PanicMonitor(PanicSettings settings, SessionManager sessionManager, Runnable shutdownAction) {
        this.settings = settings;
        this.sessionManager = sessionManager;
        this.shutdownAction = shutdownAction;
    }

    public void checkPath(String path, String sourceIp, String userAgent) {
        if (!settings.enabled() || !settings.knownAttackProbeEnabled()) return;
        String lower = path.toLowerCase();
        boolean isAttackProbe = KNOWN_ATTACK_PATH_PREFIXES.stream().anyMatch(lower::startsWith);
        if (isAttackProbe) {
            logEvent(ThreatEvent.KNOWN_ATTACK_PROBE, sourceIp, userAgent, "path=" + path);
            triggerPanic(ThreatEvent.KNOWN_ATTACK_PROBE, sourceIp, userAgent);
        }
    }

    public void recordEvent(ThreatEvent event, String sourceIp, String userAgent) {
        if (!settings.enabled()) return;
        switch (event) {
            case PATH_TRAVERSAL_ATTEMPT -> {
                if (settings.pathTraversalEnabled()) {
                    logEvent(event, sourceIp, userAgent, null);
                    triggerPanic(event, sourceIp, userAgent);
                }
            }
            case KNOWN_ATTACK_PROBE -> {
                if (settings.knownAttackProbeEnabled()) {
                    logEvent(event, sourceIp, userAgent, null);
                    triggerPanic(event, sourceIp, userAgent);
                }
            }
            case FAILED_LOGIN -> checkThreshold(event, sourceIp, userAgent,
                    settings.failedLoginsThreshold(), settings.failedLoginsWindowSeconds());
            case INVALID_SESSION -> checkThreshold(event, sourceIp, userAgent,
                    settings.invalidSessionThreshold(), settings.invalidSessionWindowSeconds());
            case EXCESSIVE_404 -> checkThreshold(event, sourceIp, userAgent,
                    settings.excessive404Threshold(), settings.excessive404WindowSeconds());
            case INVALID_REQUEST -> checkThreshold(event, sourceIp, userAgent,
                    settings.invalidRequestThreshold(), settings.invalidRequestWindowSeconds());
        }
    }

    private void checkThreshold(ThreatEvent event, String sourceIp, String userAgent, int threshold, int windowSeconds) {
        String key = event.name() + ":" + sourceIp;
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        Deque<Long> timestamps = eventLog.computeIfAbsent(key, k -> new ArrayDeque<>());
        int count;
        synchronized (timestamps) {
            timestamps.addLast(now);
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            count = timestamps.size();
        }

        logEvent(event, sourceIp, userAgent, "count=" + count + "/" + threshold);
        if (count >= threshold) {
            triggerPanic(event, sourceIp, userAgent);
        }
    }

    private void logEvent(ThreatEvent event, String sourceIp, String userAgent, String details) {
        if (details != null) {
            Logger.log("SECURITY EVENT: %s from %s (User-Agent: %s, %s)", event, sourceIp, userAgent, details);
        } else {
            Logger.log("SECURITY EVENT: %s from %s (User-Agent: %s)", event, sourceIp, userAgent);
        }
    }

    private void triggerPanic(ThreatEvent event, String sourceIp, String userAgent) {
        if (!panicking.compareAndSet(false, true)) {
            return;
        }
        Logger.log("PANIC MODE: %s from %s (User-Agent: %s) — shutting down", event, sourceIp, userAgent);
        sessionManager.clearAllSessions();
        new Thread(shutdownAction, "panic-shutdown-thread").start();
    }
}
