package net.markwalder.pictureserver.config;

import java.nio.file.Path;

public record Settings(Path rootDirectory, int port, String username, String password, PanicSettings panic) {

    public record PanicSettings(
            boolean enabled,
            boolean pathTraversalEnabled,
            boolean knownAttackProbeEnabled,
            int failedLoginsThreshold,
            int failedLoginsWindowSeconds,
            int invalidSessionThreshold,
            int invalidSessionWindowSeconds,
            int excessive404Threshold,
            int excessive404WindowSeconds,
            int invalidRequestThreshold,
            int invalidRequestWindowSeconds
    ) {}
}
