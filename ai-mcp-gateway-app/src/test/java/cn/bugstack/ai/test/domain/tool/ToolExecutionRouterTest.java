package cn.bugstack.ai.test.domain.tool;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.executor.ToolExecutor;
import cn.bugstack.ai.domain.tool.executor.ToolExecutorRouter;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.infrastructure.adapter.port.HttpToolExecutor;
import cn.bugstack.ai.infrastructure.gateway.GenericHttpGateway;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolExecutionAudit;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutionRouterTest {

    @Test
    void routesHttpAndMysqlByBackendAndMode() {
        RecordingExecutor http = new RecordingExecutor(ToolBackendType.HTTP, ToolExecutionMode.HTTP_REQUEST);
        RecordingExecutor mysql = new RecordingExecutor(ToolBackendType.MYSQL, ToolExecutionMode.MYSQL_TEMPLATE);
        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", List.of(http, mysql));
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());

        ToolExecutionResult httpResult = router.execute(ToolExecutionContext.builder()
                .requestId("http-1")
                .backendType(ToolBackendType.HTTP)
                .executionMode(ToolExecutionMode.HTTP_REQUEST)
                .backendConfiguration(new Object())
                .build());
        ToolExecutionResult mysqlResult = router.execute(ToolExecutionContext.builder()
                .requestId("mysql-1")
                .backendType(ToolBackendType.MYSQL)
                .executionMode(ToolExecutionMode.MYSQL_TEMPLATE)
                .backendConfiguration(new Object())
                .build());

        assertEquals("HTTP", httpResult.getContent());
        assertEquals("MYSQL", mysqlResult.getContent());
        assertEquals(1, http.invocations);
        assertEquals(1, mysql.invocations);
    }

    @Test
    void rejectsUnknownBackendAndMissingConfiguration() {
        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", List.of());
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());

        ToolExecutionResult unknown = router.execute(ToolExecutionContext.builder()
                .backendType(ToolBackendType.UNKNOWN)
                .executionMode(ToolExecutionMode.HTTP_REQUEST)
                .build());
        ToolExecutionResult missing = router.execute(ToolExecutionContext.builder()
                .backendType(ToolBackendType.HTTP)
                .executionMode(ToolExecutionMode.HTTP_REQUEST)
                .build());

        assertEquals(ToolExecutionErrorCode.UNKNOWN_BACKEND_TYPE, unknown.getErrorCode());
        assertEquals(ToolExecutionErrorCode.MISSING_CONFIGURATION, missing.getErrorCode());
    }

    @Test
    void normalizesHttpArgumentsWithoutDroppingTopLevelValues() throws Exception {
        GenericHttpGateway gateway = mock(GenericHttpGateway.class);
        Call<ResponseBody> call = mock(Call.class);
        when(gateway.get(eq("https://example.test/orders/42"), any(), any())).thenReturn(call);
        when(call.execute()).thenReturn(Response.success(ResponseBody.create("{}", null)));

        HttpToolExecutor executor = new HttpToolExecutor();
        ReflectionTestUtils.setField(executor, "gateway", gateway);
        ReflectionTestUtils.setField(executor, "objectMapper", new ObjectMapper());
        McpToolProtocolConfigVO.HTTPConfig httpConfig = new McpToolProtocolConfigVO.HTTPConfig();
        httpConfig.setHttpUrl("https://example.test/orders/{id}");
        httpConfig.setHttpMethod("GET");
        httpConfig.setHttpHeaders("{}");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("status", "PAID");
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("id", 42);
        arguments.put("filter", nested);

        ToolExecutionResult result = executor.execute(ToolExecutionContext.http(
                "req-1", "gateway", "orders", McpToolProtocolConfigVO.builder().httpConfig(httpConfig).build(), arguments));

        assertTrue(result.isSuccess());
        verify(gateway).get(eq("https://example.test/orders/42"), any(), any());
    }

    @Test
    void emitsBooleanIsErrorAndKeepsTextCompatibility() {
        ToolExecutionResult success = ToolExecutionResult.success("request-1", "ok");
        ToolExecutionResult failure = ToolExecutionResult.failure("request-2",
                ToolExecutionErrorCode.SQL_POLICY_REJECTED, "query rejected");

        assertFalse((Boolean) success.toMcpResult().get("isError"));
        assertTrue((Boolean) failure.toMcpResult().get("isError"));
        assertInstanceOf(List.class, success.toMcpResult().get("content"));
    }

    private static final class RecordingExecutor implements ToolExecutor {
        private final ToolBackendType backendType;
        private final ToolExecutionMode executionMode;
        private int invocations;

        private RecordingExecutor(ToolBackendType backendType, ToolExecutionMode executionMode) {
            this.backendType = backendType;
            this.executionMode = executionMode;
        }

        @Override
        public ToolBackendType backendType() {
            return backendType;
        }

        @Override
        public ToolExecutionMode executionMode() {
            return executionMode;
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionContext context) {
            invocations++;
            return ToolExecutionResult.success(backendType.name());
        }
    }
}
