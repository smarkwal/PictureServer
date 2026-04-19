package net.markwalder.pictureserver.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private static final String COOKIE_NAME = "PSSESSION";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Boolean> sessions = new ConcurrentHashMap<>();

    public String createSession() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(id, Boolean.TRUE);
        return id;
    }

    public boolean isAuthenticated(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public void clearAllSessions() {
        sessions.clear();
    }

    public String cookieName() {
        return COOKIE_NAME;
    }
}
