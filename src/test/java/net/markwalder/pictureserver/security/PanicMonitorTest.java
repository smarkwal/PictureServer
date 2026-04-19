package net.markwalder.pictureserver.security;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings.PanicSettings;

class PanicMonitorTest {

    private static final String IP = "192.168.1.1";
    private static final String UA = "TestAgent/1.0";

    private static final PanicSettings DEFAULTS = new PanicSettings(true, true, true, 5, 60, 5, 60, 10, 60, 5, 60);

    private SessionManager sessionManager;
    private CountDownLatch panicLatch;
    private AtomicBoolean shutdownCalled;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        panicLatch = new CountDownLatch(1);
        shutdownCalled = new AtomicBoolean(false);
    }

    private PanicMonitor monitor(PanicSettings settings) {
        return new PanicMonitor(settings, sessionManager, () -> {
            shutdownCalled.set(true);
            panicLatch.countDown();
        });
    }

    private void assertPanicTriggered() throws InterruptedException {
        assertTrue(panicLatch.await(1, TimeUnit.SECONDS), "expected panic to trigger");
    }

    @Test
    void pathTraversalTriggersImmediatePanic() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);

        // Assert
        assertPanicTriggered();
    }

    @Test
    void knownAttackProbeTriggersImmediatePanic() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        m.recordEvent(ThreatEvent.KNOWN_ATTACK_PROBE, IP, UA);

        // Assert
        assertPanicTriggered();
    }

    @Test
    void checkPathTriggersOnKnownPrefix() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        m.checkPath("/.env", IP, UA);

        // Assert
        assertPanicTriggered();
    }

    @Test
    void checkPathTriggersOnKnownPrefixWithSubpath() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        m.checkPath("/wp-admin/login.php", IP, UA);

        // Assert
        assertPanicTriggered();
    }

    @Test
    void checkPathDoesNotTriggerForNormalPath() {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        m.checkPath("/vacation/photo.jpg", IP, UA);

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void failedLoginBelowThresholdDoesNotPanic() {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 4; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void failedLoginAtThresholdTriggersPanic() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 5; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }

        // Assert
        assertPanicTriggered();
    }

    @Test
    void failedLoginFromDifferentIpDoesNotPanic() {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 4; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }
        m.recordEvent(ThreatEvent.FAILED_LOGIN, "10.0.0.1", UA);

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void invalidSessionAtThresholdTriggersPanic() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 5; i++) {
            m.recordEvent(ThreatEvent.INVALID_SESSION, IP, UA);
        }

        // Assert
        assertPanicTriggered();
    }

    @Test
    void excessive404AtThresholdTriggersPanic() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 10; i++) {
            m.recordEvent(ThreatEvent.EXCESSIVE_404, IP, UA);
        }

        // Assert
        assertPanicTriggered();
    }

    @Test
    void panicClearsAllSessions() throws InterruptedException {
        // Arrange
        String session = sessionManager.createSession("alice", IP, UA);
        assertTrue(sessionManager.isAuthenticated(session, IP, UA));
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        assertPanicTriggered();

        // Assert
        assertFalse(sessionManager.isAuthenticated(session, IP, UA));
    }

    @Test
    void panicOnlyTriggersOnce() throws InterruptedException {
        // Arrange
        AtomicInteger shutdownCount = new AtomicInteger(0);
        PanicMonitor m = new PanicMonitor(DEFAULTS, sessionManager, () -> {
            shutdownCount.incrementAndGet();
            panicLatch.countDown();
        });

        // Act
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        assertPanicTriggered();
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        Thread.sleep(50);

        // Assert
        assertEquals(1, shutdownCount.get());
    }

    @Test
    void disabledPanicModeNeverTriggers() {
        // Arrange
        PanicSettings disabled = new PanicSettings(false, true, true, 5, 60, 5, 60, 10, 60, 5, 60);
        PanicMonitor m = monitor(disabled);

        // Act
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        m.recordEvent(ThreatEvent.KNOWN_ATTACK_PROBE, IP, UA);
        for (int i = 0; i < 10; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void pathTraversalDisabledDoesNotPanic() {
        // Arrange
        PanicSettings settings = new PanicSettings(true, false, true, 5, 60, 5, 60, 10, 60, 5, 60);
        PanicMonitor m = monitor(settings);

        // Act
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void knownAttackProbeDisabledDoesNotPanic() {
        // Arrange
        PanicSettings settings = new PanicSettings(true, true, false, 5, 60, 5, 60, 10, 60, 5, 60);
        PanicMonitor m = monitor(settings);

        // Act
        m.checkPath("/.env", IP, UA);

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void invalidRequestBelowThresholdDoesNotPanic() {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 4; i++) {
            m.recordEvent(ThreatEvent.INVALID_REQUEST, IP, UA);
        }

        // Assert
        assertFalse(shutdownCalled.get());
    }

    @Test
    void invalidRequestAtThresholdTriggersPanic() throws InterruptedException {
        // Arrange
        PanicMonitor m = monitor(DEFAULTS);

        // Act
        for (int i = 0; i < 5; i++) {
            m.recordEvent(ThreatEvent.INVALID_REQUEST, IP, UA);
        }

        // Assert
        assertPanicTriggered();
    }
}
