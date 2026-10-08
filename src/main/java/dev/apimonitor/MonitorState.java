package dev.apimonitor;

public class MonitorState {
    int consecutiveFailures;
    int consecutiveSlowChecks;

    boolean outageAlerted;
    boolean slowAlerted;
    boolean rateAlerted;

    // Latest ten availability outcomes: 1 = failed, 0 = successful.
    String recentFailures = "";
}