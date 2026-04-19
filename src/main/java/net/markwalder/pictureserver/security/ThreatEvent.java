package net.markwalder.pictureserver.security;

public enum ThreatEvent {
    PATH_TRAVERSAL_ATTEMPT,
    KNOWN_ATTACK_PROBE,
    FAILED_LOGIN,
    INVALID_SESSION,
    EXCESSIVE_404,
    INVALID_REQUEST
}
