package dev.apimonitor;

import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DashboardAccessTest {

    @Test
    void dataIsPublicAndGetOnly() throws Exception {
        HttpTrigger trigger = DashboardDataFunction.class
                .getMethod("data", HttpRequestMessage.class, ExecutionContext.class)
                .getParameters()[0].getAnnotation(HttpTrigger.class);

        assertNotNull(trigger);
        assertEquals(AuthorizationLevel.ANONYMOUS, trigger.authLevel());
        assertArrayEquals(new HttpMethod[]{HttpMethod.GET}, trigger.methods());
    }

    @Test
    void demoRequiresAKeyAndPost() throws Exception {
        HttpTrigger trigger = DashboardDemoFunction.class
                .getMethod("demo", HttpRequestMessage.class, ExecutionContext.class)
                .getParameters()[0].getAnnotation(HttpTrigger.class);

        assertNotNull(trigger);
        assertEquals(AuthorizationLevel.FUNCTION, trigger.authLevel());
        assertArrayEquals(new HttpMethod[]{HttpMethod.POST}, trigger.methods());
    }
}