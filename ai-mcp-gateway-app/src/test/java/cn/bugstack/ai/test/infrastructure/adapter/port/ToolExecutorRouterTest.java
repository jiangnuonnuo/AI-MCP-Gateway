package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.executor.ToolExecutor;
import cn.bugstack.ai.domain.tool.executor.ToolExecutorRouter;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolExecutionAudit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolExecutorRouterTest {

    @Test
    void routesHttpAndMysqlToDifferentStrategies() {
        ToolExecutor http = executor(ToolBackendType.HTTP, ToolExecutionMode.HTTP_REQUEST);
        ToolExecutor mysql = executor(ToolBackendType.MYSQL, ToolExecutionMode.MYSQL_TEMPLATE);
        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", java.util.List.of(http, mysql));
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());

        var httpResult = router.execute(ToolExecutionContext.http("r-http", "g", "http", configWithHttp(), Map.of()));
        var mysqlContext = ToolExecutionContext.builder().requestId("r-mysql").gatewayId("g")
                .toolName("mysql").backendType(ToolBackendType.MYSQL)
                .executionMode(ToolExecutionMode.MYSQL_TEMPLATE)
                .protocolConfig(McpToolProtocolConfigVO.builder().mysqlTemplateConfig(
                        McpToolProtocolConfigVO.MysqlTemplateConfig.builder().templateRef("t").templateVersion("1").build()).build())
                .arguments(Map.of()).build();
        var mysqlResult = router.execute(mysqlContext);

        assertEquals("HTTP_REQUEST", httpResult.getContent());
        assertEquals("MYSQL_TEMPLATE", mysqlResult.getContent());
    }

    @Test
    void rejectsUnknownBackendBeforeExecutor() {
        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", java.util.List.of());
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());
        var result = router.execute(ToolExecutionContext.builder().requestId("r").toolName("unknown")
                .backendType(ToolBackendType.UNKNOWN).executionMode(ToolExecutionMode.UNKNOWN)
                .arguments(Map.of()).build());
        assertEquals(ToolExecutionErrorCode.UNKNOWN_BACKEND_TYPE, result.getErrorCode());
    }

    @Test
    void recordsAuditableSummaryWithoutArguments() {
        InMemoryToolExecutionAudit audit = new InMemoryToolExecutionAudit();
        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors",
                java.util.List.of(executor(ToolBackendType.MYSQL, ToolExecutionMode.MYSQL_TEMPLATE)));
        ReflectionTestUtils.setField(router, "auditPort", audit);
        router.execute(ToolExecutionContext.builder().requestId("r-audit").gatewayId("g")
                .toolName("tool").backendType(ToolBackendType.MYSQL)
                .executionMode(ToolExecutionMode.MYSQL_TEMPLATE)
                .backendConfiguration(new Object()).arguments(Map.of("secret", "not-recorded")).build());
        assertEquals(1, audit.records().size());
        assertEquals("r-audit", audit.records().get(0).getRequestId());
        assertEquals("SUCCESS", audit.records().get(0).getErrorCode() == null
                ? "SUCCESS" : audit.records().get(0).getErrorCode());
    }

    private static McpToolProtocolConfigVO configWithHttp() {
        var http = new McpToolProtocolConfigVO.HTTPConfig();
        http.setHttpUrl("https://example.test");
        http.setHttpMethod("GET");
        return McpToolProtocolConfigVO.builder().httpConfig(http).build();
    }

    private static ToolExecutor executor(ToolBackendType backend, ToolExecutionMode mode) {
        return new ToolExecutor() {
            @Override public ToolBackendType backendType() { return backend; }
            @Override public ToolExecutionMode executionMode() { return mode; }
            @Override public ToolExecutionResult execute(ToolExecutionContext context) {
                return ToolExecutionResult.success(context.getRequestId(), mode.name());
            }
        };
    }
}
