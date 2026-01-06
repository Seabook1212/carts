package works.weave.socks.cart.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public final class TailLatencyFault {
    private static final Logger LOG = LoggerFactory.getLogger(TailLatencyFault.class);

    private TailLatencyFault() {
    }

    private static int envInt(String key, int def) {
        try {
            String v = System.getenv(key);
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Inject tail latency:
     * - enabled by env switches
     * - probability p% (e.g., 10 means 10%)
     * - sleep random in [minMs, maxMs]
     *
     * Optional gating by request header:
     * - if FAULT_TAIL_LATENCY_REQUIRE_HEADER=true, only inject when X-Fault: TL-01
     */
    public static void maybeInject(Integer percentage) {
        if (!CartsCommonHelper.envTrue("FAULTS_ENABLED"))
            return;
        if (!CartsCommonHelper.envTrue("FAULT_TAIL_LATENCY_ENABLED"))
            return;

        // Optional: require header to avoid polluting normal traffic during tests
        boolean requireHeader = CartsCommonHelper.envTrue("FAULT_TAIL_LATENCY_REQUIRE_HEADER");
        if (requireHeader) {
            String h = CartsCommonHelper.header("X-Fault");
            if (!"TL-01".equalsIgnoreCase(h))
                return;
        }

        int p = envInt("FAULT_TAIL_LATENCY_PCT", percentage); // default 10%
        int minMs = envInt("FAULT_TAIL_LATENCY_MIN_MS", 800); // "slow" lower bound
        int maxMs = envInt("FAULT_TAIL_LATENCY_MAX_MS", 2000); // "slow" upper bound

        if (p <= 0)
            return;
        int r = ThreadLocalRandom.current().nextInt(100); // 0..99
        if (r >= p)
            return; // (100-p)% requests are normal

        int low = Math.max(0, minMs);
        int highExclusive = Math.max(low + 1, maxMs + 1);
        int sleepMs = ThreadLocalRandom.current().nextInt(low, highExclusive);

        // 建议用 warn：既明显又不至于“error=异常”
        // LOG.warn("FAULT_INJECTED fault_id={} fault_type=tail_latency sleep_ms={}",
        // faultId, sleepMs);

        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
