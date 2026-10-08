package dev.apimonitor;

import java.util.HashMap;
import java.util.Map;

public class SlowResponseTracker {

    private final int slowThreshold;
    private final Map<String, EndpointState> states = new HashMap<>();

    public SlowResponseTracker(int slowThreshold) {
        if (slowThreshold < 1) {
            throw new IllegalArgumentException(
                    "Slow threshold must be at least 1"
            );
        }
        this.slowThreshold = slowThreshold;
    }

    public String evaluate(CheckResult result) {
        EndpointState state = states.computeIfAbsent(
                result.endpointName(),
                name -> new EndpointState()
        );

        if (result.health() == HealthStatus.SLOW) {
            state.consecutiveSlowChecks++;

            if (state.consecutiveSlowChecks >= slowThreshold
                    && !state.alerted) {

                state.alerted = true;

                return "SLOW ALERT: " + result.endpointName()
                        + " exceeded its latency threshold for "
                        + state.consecutiveSlowChecks
                        + " consecutive checks";
            }

        } else {
            state.consecutiveSlowChecks = 0;

            if (result.health() == HealthStatus.HEALTHY
                    && state.alerted) {

                state.alerted = false;

                return "SLOW RECOVERY: " + result.endpointName()
                        + " is responding within its latency threshold";
            }
        }

        return null;
    }

    private static class EndpointState {
        private int consecutiveSlowChecks;
        private boolean alerted;
    }
}