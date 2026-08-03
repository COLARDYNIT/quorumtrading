package com.quorumtrading.bootstrap.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates cryptic thread names into human-readable descriptions.
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>vert.x-eventloop-thread-2 → eventloop-io 3/8</li>
 *   <li>executor-thread-5 → worker-blocking 6/200</li>
 *   <li>quorumtrading-consensus-scheduler → quorumtrading-consensus-scheduler</li>
 *   <li>vert.x-worker-thread-3 → vertx-worker 4/20</li>
 * </ul>
 */
public class ThreadNameTranslator {

    // Thread pool sizes (should match application.properties)
    private static final int EVENTLOOP_SIZE = 8;  // quarkus.vertx.event-loops-pool-size (default: 2 * cores)
    private static final int WORKER_SIZE = 200;   // quarkus.vertx.worker-pool-size
    private static final int EXECUTOR_SIZE = 200; // quarkus.thread-pool.max-threads

    // Patterns for common thread names
    private static final Pattern EVENTLOOP_PATTERN = Pattern.compile("vert\\.x-eventloop-thread-(\\d+)");
    private static final Pattern WORKER_PATTERN = Pattern.compile("vert\\.x-worker-thread-(\\d+)");
    private static final Pattern EXECUTOR_PATTERN = Pattern.compile("executor-thread-(\\d+)");
    private static final Pattern MANAGED_EXECUTOR_PATTERN = Pattern.compile("managedExecutor-(\\d+)-thread-(\\d+)");
    private static final Pattern FJP_PATTERN = Pattern.compile("ForkJoinPool[.-]\\d+-worker-(\\d+)");

    /**
     * Translates a thread name to a human-readable format.
     *
     * @param threadName the original thread name
     * @return human-readable thread description
     */
    public static String translate(String threadName) {
        if (threadName == null) {
            return "unknown";
        }

        Matcher matcher;

        // Vert.x Event Loop (non-blocking I/O)
        matcher = EVENTLOOP_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            int num = Integer.parseInt(matcher.group(1)) + 1;
            return String.format("eventloop-io %d/%d", num, EVENTLOOP_SIZE);
        }

        // Vert.x Worker (blocking operations dispatched from reactive)
        matcher = WORKER_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            int num = Integer.parseInt(matcher.group(1)) + 1;
            return String.format("vertx-worker %d/%d", num, WORKER_SIZE);
        }

        // Default executor (REST API blocking handlers)
        matcher = EXECUTOR_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            int num = Integer.parseInt(matcher.group(1));
            return String.format("worker-blocking %d/%d", num, EXECUTOR_SIZE);
        }

        // ManagedExecutor (SmallRye Context Propagation)
        matcher = MANAGED_EXECUTOR_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            int poolId = Integer.parseInt(matcher.group(1));
            int threadNum = Integer.parseInt(matcher.group(2));
            return String.format("managed-exec-%d #%d", poolId, threadNum);
        }

        // ForkJoinPool (virtual threads, parallel streams)
        matcher = FJP_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            int num = Integer.parseInt(matcher.group(1));
            return String.format("fork-join #%d", num);
        }

        // Quarkus main thread
        if (threadName.equals("main")) {
            return "main-startup";
        }

        // Quarkus scheduler
        if (threadName.startsWith("quarkus-scheduler")) {
            return threadName.replace("quarkus-", "q-");
        }

        // Keep custom thread names as-is (they're already descriptive)
        if (threadName.startsWith("quorumtrading-")) {
            return threadName;
        }

        // Default: return original name
        return threadName;
    }

    /**
     * Gets a short description of what the thread type does.
     * Useful for documentation or tooltips.
     */
    public static String getThreadTypeDescription(String threadName) {
        if (threadName == null) return "Unknown thread";

        if (EVENTLOOP_PATTERN.matcher(threadName).matches()) {
            return "Non-blocking I/O event loop - handles WebSocket, HTTP parsing, reactive streams";
        }
        if (WORKER_PATTERN.matcher(threadName).matches()) {
            return "Vert.x worker pool - executes @Blocking annotated reactive handlers";
        }
        if (EXECUTOR_PATTERN.matcher(threadName).matches()) {
            return "Default worker pool - executes REST API handlers, JPA operations";
        }
        if (threadName.startsWith("quorumtrading-consensus")) {
            return "Scheduled consensus evaluation for LIVE simulations";
        }
        if (threadName.startsWith("quorumtrading-market-data")) {
            return "Scheduled market data collection";
        }

        return "Application thread";
    }
}
