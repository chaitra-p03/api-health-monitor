package dev.apimonitor;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

public class MonitorTimerFunction {

    @FunctionName("MonitorApis")
    public void run(
            @TimerTrigger(
                    name = "timerInfo",
                    schedule = "0 */5 * * * *"
            ) String timerInfo,
            ExecutionContext context
    ) {
        context.getLogger().info("Scheduled API monitoring started.");
        App.main(new String[0]);
    }
}