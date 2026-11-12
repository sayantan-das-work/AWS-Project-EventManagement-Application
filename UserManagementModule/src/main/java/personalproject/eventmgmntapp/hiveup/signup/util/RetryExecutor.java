package personalproject.eventmgmntapp.hiveup.signup.util;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Runs an operation with a bounded number of attempts and fixed backoff delays. */
public class RetryExecutor {
    /** Pause abstraction so tests do not really sleep. */
    @FunctionalInterface
    public interface Sleeper {
        /**
         * @param millis time to pause
         * @throws InterruptedException if interrupted while pausing
         */
        void sleep(long millis) throws InterruptedException;
    }

    private final long[] backoffMillis;
    private final Sleeper sleeper;

    /**
     * @param backoffMillis delay before each retry; attempts = length + 1 (e.g. {100, 200} = 3 attempts)
     * @param sleeper       pause implementation
     */
    public RetryExecutor(long[] backoffMillis, Sleeper sleeper) {
        this.backoffMillis = backoffMillis.clone();
        this.sleeper = sleeper;
    }

    /** @return executor with 3 attempts and 100ms/200ms backoff using {@link Thread#sleep} */
    public static RetryExecutor standard() {
        return new RetryExecutor(new long[]{100, 200}, Thread::sleep);
    }

    /**
     * Executes the operation, retrying while the failure is retryable.
     *
     * @param operation operation to run
     * @param retryable decides whether a failure may be retried
     * @param <T>       result type
     * @return the operation result
     * @throws RuntimeException the last failure when attempts are exhausted or it is not retryable
     */
    public <T> T execute(Supplier<T> operation, Predicate<RuntimeException> retryable) {
        int attempt = 0;
        while (true) {
            try {
                return operation.get();
            } catch (RuntimeException e) {
                if (attempt >= backoffMillis.length || !retryable.test(e)) {
                    throw e;
                }
                pause(backoffMillis[attempt]);
                attempt++;
            }
        }
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during retry backoff");
        }
    }
}
