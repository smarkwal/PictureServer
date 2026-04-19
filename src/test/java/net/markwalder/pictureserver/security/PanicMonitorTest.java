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
        PanicMonitor m = monitor(DEFAULTS);
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        assertPanicTriggered();
    }

    @Test
    void knownAttackProbeTriggersImmediatePanic() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        m.recordEvent(ThreatEvent.KNOWN_ATTACK_PROBE, IP, UA);
        assertPanicTriggered();
    }

    @Test
    void checkPathTriggersOnKnownPrefix() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        m.checkPath("/.env", IP, UA);
        assertPanicTriggered();
    }

    @Test
    void checkPathTriggersOnKnownPrefixWithSubpath() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        m.checkPath("/wp-admin/login.php", IP, UA);
        assertPanicTriggered();
    }

    @Test
    void checkPathDoesNotTriggerForNormalPath() {
        PanicMonitor m = monitor(DEFAULTS);
        m.checkPath("/vacation/photo.jpg", IP, UA);
        assertFalse(shutdownCalled.get());
    }

    @Test
    void failedLoginBelowThresholdDoesNotPanic() {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 4; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }
        assertFalse(shutdownCalled.get());
    }

    @Test
    void failedLoginAtThresholdTriggersPanic() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 5; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }
        assertPanicTriggered();
    }

    @Test
    void failedLoginFromDifferentIpDoesNotPanic() {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 4; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }
        m.recordEvent(ThreatEvent.FAILED_LOGIN, "10.0.0.1", UA);
        assertFalse(shutdownCalled.get());
    }

    @Test
    void invalidSessionAtThresholdTriggersPanic() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 5; i++) {
            m.recordEvent(ThreatEvent.INVALID_SESSION, IP, UA);
        }
        assertPanicTriggered();
    }

    @Test
    void excessive404AtThresholdTriggersPanic() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 10; i++) {
            m.recordEvent(ThreatEvent.EXCESSIVE_404, IP, UA);
        }
        assertPanicTriggered();
    }

    @Test
    void panicClearsAllSessions() throws InterruptedException {
        String session = sessionManager.createSession();
        assertTrue(sessionManager.isAuthenticated(session));

        PanicMonitor m = monitor(DEFAULTS);
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        assertPanicTriggered();

        assertFalse(sessionManager.isAuthenticated(session));
    }

    @Test
    void panicOnlyTriggersOnce() throws InterruptedException {
        AtomicInteger shutdownCount = new AtomicInteger(0);
        PanicMonitor m = new PanicMonitor(DEFAULTS, sessionManager, () -> {
            shutdownCount.incrementAndGet();
            panicLatch.countDown();
        });
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        assertPanicTriggered();
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        Thread.sleep(50);
        assertEquals(1, shutdownCount.get());
    }

    @Test
    void disabledPanicModeNeverTriggers() {
        PanicSettings disabled = new PanicSettings(false, true, true, 5, 60, 5, 60, 10, 60, 5, 60);
        PanicMonitor m = monitor(disabled);
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        m.recordEvent(ThreatEvent.KNOWN_ATTACK_PROBE, IP, UA);
        for (int i = 0; i < 10; i++) {
            m.recordEvent(ThreatEvent.FAILED_LOGIN, IP, UA);
        }
        assertFalse(shutdownCalled.get());
    }

    @Test
    void pathTraversalDisabledDoesNotPanic() {
        PanicSettings settings = new PanicSettings(true, false, true, 5, 60, 5, 60, 10, 60, 5, 60);
        PanicMonitor m = monitor(settings);
        m.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, IP, UA);
        assertFalse(shutdownCalled.get());
    }

    @Test
    void knownAttackProbeDisabledDoesNotPanic() {
        PanicSettings settings = new PanicSettings(true, true, false, 5, 60, 5, 60, 10, 60, 5, 60);
        PanicMonitor m = monitor(settings);
        m.checkPath("/.env", IP, UA);
        assertFalse(shutdownCalled.get());
    }

    @Test
    void invalidRequestBelowThresholdDoesNotPanic() {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 4; i++) {
            m.recordEvent(ThreatEvent.INVALID_REQUEST, IP, UA);
        }
        assertFalse(shutdownCalled.get());
    }

    @Test
    void invalidRequestAtThresholdTriggersPanic() throws InterruptedException {
        PanicMonitor m = monitor(DEFAULTS);
        for (int i = 0; i < 5; i++) {
            m.recordEvent(ThreatEvent.INVALID_REQUEST, IP, UA);
        }
        assertPanicTriggered();
    }
}
