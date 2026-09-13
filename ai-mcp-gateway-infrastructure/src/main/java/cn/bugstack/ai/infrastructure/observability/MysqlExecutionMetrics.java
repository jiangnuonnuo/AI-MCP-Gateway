package cn.bugstack.ai.infrastructure.observability;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** MVP MySQL 查询指标适配器，按结果状态记录次数、耗时、行数和结果字节数。 */
@Component
public class MysqlExecutionMetrics implements IMysqlExecutionMetricsPort {
    /** 按结果状态聚合的计数器。 */
    private Map<String, Counter> counters = new ConcurrentHashMap<>();

    @Override
    public void record(String outcome, long durationMs, int rowCount, long resultBytes) {
        Counter counter = counters.computeIfAbsent(outcome == null ? "UNKNOWN" : outcome, key -> new Counter());
        counter.count.incrementAndGet();
        counter.durationMs.addAndGet(Math.max(0, durationMs));
        counter.rows.addAndGet(Math.max(0, rowCount));
        counter.bytes.addAndGet(Math.max(0, resultBytes));
    }

    public Map<String, Map<String, Long>> snapshot() {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        counters.forEach((key, value) -> result.put(key, Map.of(
                "count", value.count.get(),
                "durationMs", value.durationMs.get(),
                "rowCount", value.rows.get(),
                "resultBytes", value.bytes.get())));
        return result;
    }

    private static final class Counter {
        /** 执行次数。 */
        private AtomicLong count = new AtomicLong();
        /** 累计耗时，单位毫秒。 */
        private AtomicLong durationMs = new AtomicLong();
        /** 累计返回行数。 */
        private AtomicLong rows = new AtomicLong();
        /** 累计结果估算字节数。 */
        private AtomicLong bytes = new AtomicLong();
    }
}
