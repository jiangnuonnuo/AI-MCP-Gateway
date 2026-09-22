package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionStage;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionTrace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MysqlExecutionTraceTest {

    @Test
    void keepsStableFiveStageLifecycleAndSafeFailure() {
        MysqlExecutionTrace trace = new MysqlExecutionTrace();

        trace.start("PARAMETER_VALIDATION");
        trace.succeed("PARAMETER_VALIDATION");
        trace.start("POLICY_VALIDATION");
        trace.fail("POLICY_VALIDATION", "SQL_POLICY_REJECTED", "read only policy rejected");

        assertEquals(5, trace.snapshot().size());
        assertEquals(MysqlExecutionStage.Status.SUCCEEDED, trace.snapshot().get(0).getStatus());
        assertEquals(MysqlExecutionStage.Status.FAILED, trace.snapshot().get(1).getStatus());
        assertEquals("SQL_POLICY_REJECTED", trace.snapshot().get(1).getErrorCode());
        assertEquals(MysqlExecutionStage.Status.PENDING, trace.snapshot().get(2).getStatus());
    }
}
