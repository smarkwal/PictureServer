package net.markwalder.pictureserver.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private static final String COOKIE_NAME = "PSSESSION";

    private record SessionData(String username, String sourceIp, String userAgent) {}

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    public String createSession(String username, String sourceIp, String userAgent) {
        // Remove any existing session for the same username
        sessions.entrySet().removeIf(e -> username.equals(e.getValue().username()));

        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(id, new SessionData(username, sourceIp, userAgent));
        return id;
    }

    public boolean isAuthenticated(String sessionId, String sourceIp, String userAgent) {
        if (sessionId == null) return false;
        SessionData data = sessions.get(sessionId);
        if (data == null) return false;
        if (!data.sourceIp().equals(sourceIp) || !data.userAgent().equals(userAgent)) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
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
