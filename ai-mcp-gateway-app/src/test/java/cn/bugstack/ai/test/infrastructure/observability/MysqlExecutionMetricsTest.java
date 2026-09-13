package cn.bugstack.ai.test.infrastructure.observability;

import cn.bugstack.ai.infrastructure.observability.MysqlExecutionMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlExecutionMetricsTest {

    @Test
    void aggregatesOutcomeDurationRowsAndBytes() {
        MysqlExecutionMetrics metrics = new MysqlExecutionMetrics();
        metrics.record("SUCCESS", 12, 3, 100);
        metrics.record("SUCCESS", 8, 2, 40);
        assertEquals(2L, metrics.snapshot().get("SUCCESS").get("count"));
        assertEquals(20L, metrics.snapshot().get("SUCCESS").get("durationMs"));
        assertEquals(5L, metrics.snapshot().get("SUCCESS").get("rowCount"));
        assertEquals(140L, metrics.snapshot().get("SUCCESS").get("resultBytes"));
    }
}
