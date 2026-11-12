package personalproject.eventmgmntapp.hiveup.signup.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryExecutorTest {
    private final List<Long> sleeps = new ArrayList<>();
    private final RetryExecutor executor = new RetryExecutor(new long[]{100, 200}, sleeps::add);

    @Test
    void returnsImmediatelyOnSuccess() {
        assertEquals("ok", executor.execute(() -> "ok", e -> true));
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void retriesRetryableFailureThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        String result = executor.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("transient");
            }
            return "done";
        }, e -> true);
        assertEquals("done", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(100L, 200L), sleeps);
    }

    @Test
    void givesUpAfterThreeAttemptsAndThrowsLastFailure() {
        AtomicInteger calls = new AtomicInteger();
        IllegalStateException last = assertThrows(IllegalStateException.class, () -> executor.execute(() -> {
            throw new IllegalStateException("fail" + calls.incrementAndGet());
        }, e -> true));
        assertEquals(3, calls.get());
        assertEquals("fail3", last.getMessage());
        assertEquals(List.of(100L, 200L), sleeps);
    }

    @Test
    void doesNotRetryNonRetryableFailure() {
        AtomicInteger calls = new AtomicInteger();
        IllegalArgumentException boom = new IllegalArgumentException("fatal");
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> executor.execute(() -> {
            calls.incrementAndGet();
            throw boom;
        }, e -> false));
        assertSame(boom, thrown);
        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void interruptedBackoffRestoresInterruptFlagAndFails() {
        RetryExecutor interrupted = new RetryExecutor(new long[]{10}, ms -> {
            throw new InterruptedException();
        });
        assertThrows(IllegalStateException.class, () -> interrupted.execute(() -> {
            throw new RuntimeException("x");
        }, e -> true));
        assertTrue(Thread.interrupted());
    }
}
