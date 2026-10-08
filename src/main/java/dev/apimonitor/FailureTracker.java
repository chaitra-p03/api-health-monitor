package dev.apimonitor;

import java.util.HashMap;
import java.util.Map;

public class FailureTracker {

    private final int failureThreshold;
    private final Map<String, EndpointState> states = new HashMap<>();

    public FailureTracker(int failureThreshold) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException(
                    "Failure threshold must be at least 1"
            );
        }
        this.failureThreshold = failureThreshold;
    }

    public String evaluate(CheckResult result) {
        EndpointState state = states.computeIfAbsent(
                result.endpointName(),
                name -> new EndpointState()
        );

        if (result.health() == HealthStatus.FAILED) {
            state.consecutiveFailures++;

            if (state.consecutiveFailures >= failureThreshold
                    && !state.outageAlerted) {

                state.outageAlerted = true;

                return "ALERT: " + result.endpointName()
                        + " has failed "
                        + state.consecutiveFailures
                        + " consecutive checks";
            }

        } else {
            state.consecutiveFailures = 0;

            if (state.outageAlerted) {
                state.outageAlerted = false;

                return "RECOVERY: " + result.endpointName()
                        + " is responding again"
                        + " (health=" + result.health() + ")";
            }
        }

        return null;
    }

    public int consecutiveFailures(String endpointName) {
        EndpointState state = states.get(endpointName);
        return state == null ? 0 : state.consecutiveFailures;
    }

    private static class EndpointState {
        private int consecutiveFailures;
        private boolean outageAlerted;
    }
}