package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.tool.adapter.port.IMysqlExecutionMetricsPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** MVP MySQL 查询指标适配器，按结果状态记录次数、耗时、行数和结果字节数。 */
@Component
public class MysqlExecutionMetrics implements IMysqlExecutionMetricsPort {
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

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
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong durationMs = new AtomicLong();
        private final AtomicLong rows = new AtomicLong();
        private final AtomicLong bytes = new AtomicLong();
    }
}
