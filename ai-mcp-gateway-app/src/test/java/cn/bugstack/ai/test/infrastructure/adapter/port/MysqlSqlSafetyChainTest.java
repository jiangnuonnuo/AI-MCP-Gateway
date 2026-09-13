package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.safety.MysqlSqlSafetyChain;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlSqlParser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlSqlSafetyChainTest {

    private final MysqlSqlSafetyChain chain = configuredChain();
    private final MysqlQueryPolicy policy = MysqlQueryPolicy.defaults();

    private static MysqlSqlSafetyChain configuredChain() {
        MysqlSqlSafetyChain value = MysqlSqlSafetyChain.defaultChain();
        ReflectionTestUtils.setField(value, "analysisPort", new MysqlSqlParser());
        return value;
    }

    @Test
    void acceptsSelectCteJoinAggregateAndParameters() {
        SqlSafetyDecision decision = chain.validate("WITH recent AS (SELECT channel_id, pay_amount FROM fact_order "
                + "WHERE order_time >= :fromTime AND order_time < :toTime) "
                + "SELECT c.channel_name, COUNT(*) AS order_count, SUM(r.pay_amount) AS total_amount "
                + "FROM recent r JOIN dim_channel c ON c.channel_id = r.channel_id "
                + "GROUP BY c.channel_name", Map.of("fromTime", "2024-01-01", "toTime", "2025-01-01"), policy);

        assertTrue(decision.isAllowed(), decision.getReason());
    }

    @Test
    void rejectsWriteLockFileOutputCallAndMultipleStatements() {
        for (String sql : new String[]{
                "INSERT INTO t(a) VALUES (1)",
                "UPDATE t SET a = 1",
                "DELETE FROM t",
                "CREATE TABLE t(a INT)",
                "SELECT * FROM t FOR UPDATE",
                "SELECT * FROM t INTO OUTFILE '/tmp/result'",
                "CALL refresh_report()",
                "SELECT 1; SELECT 2"}) {
            SqlSafetyDecision decision = chain.validate(sql, Map.of(), policy);
            assertEquals(SqlSafetyDecision.Status.REJECTED, decision.getStatus(), sql);
        }
    }

    @Test
    void failsClosedForParseAndParameterErrors() {
        assertEquals(SqlSafetyDecision.Status.PARSE_ERROR,
                chain.validate("SELECT * FROM", Map.of(), policy).getStatus());
        assertEquals(SqlSafetyDecision.Status.REJECTED,
                chain.validate("SELECT * FROM t WHERE id = :id", Map.of(), policy).getStatus());
        assertEquals(SqlSafetyDecision.Status.POLICY_NOT_CONFIGURED,
                chain.validate("SELECT 1", Map.of(), null).getStatus());
    }
}
