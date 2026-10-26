package ratelimit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MainTest {
    @Test
    void mainRunsBothRateLimiterDemos() throws Exception {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream capturedOutput =
                     new PrintStream(output, true, StandardCharsets.UTF_8.name())) {
            System.setOut(capturedOutput);
            Main.main(new String[0]);
        } finally {
            System.setOut(originalOutput);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("Token bucket:"));
        assertTrue(result.contains("Request 4: rejected"));
        assertTrue(result.contains("Request after refill: accepted"));
        assertTrue(result.contains("Available tokens:"));
        assertTrue(result.contains("Leaky bucket:"));
        assertTrue(result.contains("Request after leak: accepted"));
        assertTrue(result.contains("Current load:"));
    }
}
