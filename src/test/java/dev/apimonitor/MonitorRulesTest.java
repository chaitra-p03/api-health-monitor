package dev.apimonitor;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonitorRulesTest {

    private final MonitorRules rules = new MonitorRules();

    private List<String> check(MonitorState state, HealthStatus health) {
        return rules.evaluate(new CheckResult(
                "demo-api",
                "https://example.com",
                Instant.EPOCH,
                health == HealthStatus.FAILED ? 500 : 200,
                health == HealthStatus.SLOW ? 3000L : 100L,
                health,
                null
        ), state);
    }

    @Test
    void outageAlertsOnceThenRecoversAndCanAlertAgain() {
        MonitorState state = new MonitorState();

        assertTrue(check(state, HealthStatus.FAILED).isEmpty());
        assertTrue(check(state, HealthStatus.FAILED).isEmpty());

        assertEquals(List.of(
                "ALERT: demo-api has failed 3 consecutive checks"
        ), check(state, HealthStatus.FAILED));

        assertTrue(check(state, HealthStatus.FAILED).isEmpty());

        assertEquals(List.of(
                "RECOVERY: demo-api is responding again (health=HEALTHY)"
        ), check(state, HealthStatus.HEALTHY));

        assertEquals(0, state.consecutiveFailures);
        assertFalse(state.outageAlerted);
        assertTrue(check(state, HealthStatus.HEALTHY).isEmpty());

        check(state, HealthStatus.FAILED);
        check(state, HealthStatus.FAILED);

        assertEquals(List.of(
                "ALERT: demo-api has failed 3 consecutive checks"
        ), check(state, HealthStatus.FAILED));
    }

    @Test
    void slowAlertWaitsForHealthyResponseBeforeRecovery() {
        MonitorState state = new MonitorState();

        assertTrue(check(state, HealthStatus.SLOW).isEmpty());
        assertTrue(check(state, HealthStatus.SLOW).isEmpty());

        assertEquals(List.of(
                "SLOW ALERT: demo-api exceeded its latency threshold for 3 consecutive checks"
        ), check(state, HealthStatus.SLOW));

        assertTrue(check(state, HealthStatus.SLOW).isEmpty());

        // A failed request must not claim that latency recovered.
        assertTrue(check(state, HealthStatus.FAILED).isEmpty());
        assertTrue(state.slowAlerted);
        assertEquals(0, state.consecutiveSlowChecks);

        assertEquals(List.of(
                "SLOW RECOVERY: demo-api is responding within its latency threshold"
        ), check(state, HealthStatus.HEALTHY));

        assertFalse(state.slowAlerted);
        assertTrue(check(state, HealthStatus.HEALTHY).isEmpty());
    }

    @Test
    void rateAlertNeedsFullWindowAndRecoversBelowThirtyPercent() {
        MonitorState state = new MonitorState();

        HealthStatus[] firstNine = {
                HealthStatus.FAILED, HealthStatus.HEALTHY,
                HealthStatus.FAILED, HealthStatus.HEALTHY,
                HealthStatus.FAILED, HealthStatus.HEALTHY,
                HealthStatus.HEALTHY, HealthStatus.HEALTHY,
                HealthStatus.HEALTHY
        };

        for (HealthStatus health : firstNine) {
            assertTrue(check(state, health).isEmpty());
        }

        assertFalse(state.rateAlerted);

        assertEquals(List.of(
                "RATE ALERT: demo-api failure rate is 30% over the latest 10 checks"
        ), check(state, HealthStatus.HEALTHY));

        // Check eleven removes the oldest failure from the window.
        assertEquals(List.of(
                "RATE RECOVERY: demo-api failure rate dropped to 20%"
        ), check(state, HealthStatus.HEALTHY));

        assertEquals(10, state.recentFailures.length());
        assertFalse(state.rateAlerted);
        assertTrue(check(state, HealthStatus.HEALTHY).isEmpty());
    }
}
