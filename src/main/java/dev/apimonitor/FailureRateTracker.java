package dev.apimonitor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FailureRateTracker {

    private final double thresholdPercent;
    private final Set<String> alertedEndpoints = new HashSet<>();

    public FailureRateTracker(double thresholdPercent) {
        if (!Double.isFinite(thresholdPercent)
                || thresholdPercent <= 0
                || thresholdPercent > 100) {

            throw new IllegalArgumentException(
                    "Threshold must be greater than 0 and at most 100"
            );
        }

        this.thresholdPercent = thresholdPercent;
    }

    public String evaluate(
            String endpointName,
            CheckHistory.Summary summary
    ) {
        if (!summary.windowFull()) {
            return null;
        }

        boolean highFailureRate =
                summary.failureRatePercent() >= thresholdPercent;

        if (highFailureRate) {
            if (alertedEndpoints.add(endpointName)) {
                return String.format(
                        Locale.ROOT,
                        "RATE ALERT: %s failure rate is %.1f%%"
                                + " over the latest %d checks",
                        endpointName,
                        summary.failureRatePercent(),
                        summary.checks()
                );
            }

        } else if (alertedEndpoints.remove(endpointName)) {
            return String.format(
                    Locale.ROOT,
                    "RATE RECOVERY: %s failure rate dropped to %.1f%%",
                    endpointName,
                    summary.failureRatePercent()
            );
        }

        return null;
    }
}