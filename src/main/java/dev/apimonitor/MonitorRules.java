package dev.apimonitor;

import java.util.ArrayList;
import java.util.List;

public class MonitorRules {

    public List<String> evaluate(CheckResult result, MonitorState state) {
        List<String> notifications = new ArrayList<>();
        String name = result.endpointName();

        boolean failed = result.health() == HealthStatus.FAILED;
        boolean slow = result.health() == HealthStatus.SLOW;
        boolean healthy = result.health() == HealthStatus.HEALTHY;

        // Consecutive availability failures.
        if (failed) {
            state.consecutiveFailures++;

            if (state.consecutiveFailures >= 3 && !state.outageAlerted) {
                state.outageAlerted = true;
                notifications.add(
                        "ALERT: " + name + " has failed "
                                + state.consecutiveFailures
                                + " consecutive checks"
                );
            }
        } else {
            state.consecutiveFailures = 0;

            if (state.outageAlerted) {
                state.outageAlerted = false;
                notifications.add(
                        "RECOVERY: " + name
                                + " is responding again"
                                + " (health=" + result.health() + ")"
                );
            }
        }

        // Consecutive slow responses.
        if (slow) {
            state.consecutiveSlowChecks++;

            if (state.consecutiveSlowChecks >= 3 && !state.slowAlerted) {
                state.slowAlerted = true;
                notifications.add(
                        "SLOW ALERT: " + name
                                + " exceeded its latency threshold for "
                                + state.consecutiveSlowChecks
                                + " consecutive checks"
                );
            }
        } else {
            state.consecutiveSlowChecks = 0;

            if (healthy && state.slowAlerted) {
                state.slowAlerted = false;
                notifications.add(
                        "SLOW RECOVERY: " + name
                                + " is responding within its latency threshold"
                );
            }
        }

        // Rolling window of the latest ten availability outcomes.
        state.recentFailures += failed ? "1" : "0";

        if (state.recentFailures.length() > 10) {
            state.recentFailures = state.recentFailures.substring(
                    state.recentFailures.length() - 10
            );
        }

        if (state.recentFailures.length() == 10) {
            long failures = state.recentFailures.chars()
                    .filter(value -> value == '1')
                    .count();

            int ratePercent = (int) failures * 10;

            if (ratePercent >= 30 && !state.rateAlerted) {
                state.rateAlerted = true;
                notifications.add(
                        "RATE ALERT: " + name
                                + " failure rate is " + ratePercent
                                + "% over the latest 10 checks"
                );
            } else if (ratePercent < 30 && state.rateAlerted) {
                state.rateAlerted = false;
                notifications.add(
                        "RATE RECOVERY: " + name
                                + " failure rate dropped to "
                                + ratePercent + "%"
                );
            }
        }

        return notifications;
    }
}