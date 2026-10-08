package dev.apimonitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class CheckHistory {

    private final int windowSize;
    private final Map<String, Deque<CheckResult>> histories =
            new HashMap<>();

    public CheckHistory(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException(
                    "Window size must be at least 1"
            );
        }
        this.windowSize = windowSize;
    }

    public Summary add(CheckResult result) {
        Deque<CheckResult> history = histories.computeIfAbsent(
                result.endpointName(),
                name -> new ArrayDeque<>()
        );

        history.addLast(result);

        if (history.size() > windowSize) {
            history.removeFirst();
        }

        int failures = 0;

        for (CheckResult check : history) {
            if (check.health() == HealthStatus.FAILED) {
                failures++;
            }
        }

        double failureRate = 100.0 * failures / history.size();

        return new Summary(
                history.size(),
                failures,
                failureRate,
                history.size() == windowSize
        );
    }

    public record Summary(
            int checks,
            int failures,
            double failureRatePercent,
            boolean windowFull
    ) {}
}