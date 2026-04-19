package net.markwalder.pictureserver.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    private static final String USERNAME = "alice";
    private static final String IP = "192.168.1.1";
    private static final String UA = "TestBrowser/1.0";

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    void cookieName_returnsPSSESSION() {
        // Act & Assert
        assertThat(sessionManager.cookieName()).isEqualTo("PSSESSION");
    }

    @Test
    void createSession_returnsNonNullSessionId() {
        // Act
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);

        // Assert
        assertThat(sessionId).isNotNull().isNotEmpty();
    }

    @Test
    void createSession_returnsUrlSafeBase64WithoutPadding() {
        // Act
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);

        // Assert — URL-safe Base64 (no +, /, or = padding characters)
        assertThat(sessionId).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void createSession_generatesDifferentIdsForEachCall() {
        // Arrange
        Set<String> ids = new HashSet<>();

        // Act
        for (int i = 0; i < 10; i++) {
            ids.add(sessionManager.createSession(USERNAME + i, IP, UA));
        }

        // Assert
        assertThat(ids).hasSize(10);
    }

    @Test
    void createSession_replacesExistingSessionForSameUsername() {
        // Arrange
        String first = sessionManager.createSession(USERNAME, IP, UA);

        // Act
        String second = sessionManager.createSession(USERNAME, IP, UA);

        // Assert — old session is invalidated, new session is active
        assertThat(sessionManager.isAuthenticated(first, IP, UA)).isFalse();
        assertThat(sessionManager.isAuthenticated(second, IP, UA)).isTrue();
    }

    @Test
    void isAuthenticated_returnsTrueForValidSession() {
        // Arrange
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);

        // Act
        boolean result = sessionManager.isAuthenticated(sessionId, IP, UA);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void isAuthenticated_returnsFalseForNullSessionId() {
        // Act & Assert
        assertThat(sessionManager.isAuthenticated(null, IP, UA)).isFalse();
    }

    @Test
    void isAuthenticated_returnsFalseForUnknownSessionId() {
        // Act & Assert
        assertThat(sessionManager.isAuthenticated("unknown-session-id", IP, UA)).isFalse();
    }

    @Test
    void isAuthenticated_returnsFalseAndRemovesSessionWhenIpChanges() {
        // Arrange
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);

        // Act — attacker replays session from a different IP
        boolean result = sessionManager.isAuthenticated(sessionId, "10.0.0.1", UA);

        // Assert — access denied and session is permanently revoked
        assertThat(result).isFalse();
        assertThat(sessionManager.isAuthenticated(sessionId, IP, UA)).isFalse();
    }

    @Test
    void isAuthenticated_returnsFalseAndRemovesSessionWhenUserAgentChanges() {
        // Arrange
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);

        // Act — attacker replays session with a different User-Agent
        boolean result = sessionManager.isAuthenticated(sessionId, IP, "AttackerBot/2.0");

        // Assert — access denied and session is permanently revoked
        assertThat(result).isFalse();
        assertThat(sessionManager.isAuthenticated(sessionId, IP, UA)).isFalse();
    }

    @Test
    void removeSession_invalidatesSession() {
        // Arrange
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);

        // Act
        sessionManager.removeSession(sessionId);

        // Assert
        assertThat(sessionManager.isAuthenticated(sessionId, IP, UA)).isFalse();
    }

    @Test
    void removeSession_doesNothingForNullId() {
        // Act & Assert — must not throw
        sessionManager.removeSession(null);
    }

    @Test
    void clearAllSessions_invalidatesAllSessions() {
        // Arrange
        String s1 = sessionManager.createSession("alice", IP, UA);
        String s2 = sessionManager.createSession("bob", "10.0.0.2", "OtherAgent/1.0");

        // Act
        sessionManager.clearAllSessions();

        // Assert
        assertThat(sessionManager.isAuthenticated(s1, IP, UA)).isFalse();
        assertThat(sessionManager.isAuthenticated(s2, "10.0.0.2", "OtherAgent/1.0")).isFalse();
    }
}
