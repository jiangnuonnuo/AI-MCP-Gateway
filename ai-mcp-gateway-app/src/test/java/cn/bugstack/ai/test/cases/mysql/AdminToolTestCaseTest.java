package cn.bugstack.ai.test.cases.mysql;

import cn.bugstack.ai.cases.admin.mysql.AdminToolTestCase;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.domain.tool.service.ToolInvocationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminToolTestCaseTest {

    @Test
    void returnsActualToolResultAndMasksSensitiveRequestArguments() {
        ToolInvocationService invocation = mock(ToolInvocationService.class);
        when(invocation.execute(eq("gateway-a"), eq("orders"), any(), any()))
                .thenReturn(ToolExecutionResult.structured("request-1", "query-1",
                        java.util.List.of(Map.of("name", "order_count")), java.util.List.of(java.util.List.of(2)), false));
        AdminToolTestCase testCase = new AdminToolTestCase();
        ReflectionTestUtils.setField(testCase, "toolInvocationService", invocation);

        var report = testCase.execute("gateway-a", "orders", Map.of("fromTime", "2026-01-01", "apiKey", "secret-value"));

        assertTrue(report.isSuccess());
        assertEquals("query-1", report.getQueryId());
        assertEquals("***", report.getRequestArguments().get("apiKey"));
        assertEquals("SUCCEEDED", report.getStages().get(4).getStatus().name());
        assertFalse(report.toStructuredMap().toString().contains("secret-value"));
    }

    @Test
    void preservesStableToolErrorCode() {
        ToolInvocationService invocation = mock(ToolInvocationService.class);
        when(invocation.execute(eq("gateway-a"), eq("orders"), any(), any()))
                .thenReturn(ToolExecutionResult.failure("request-1", ToolExecutionErrorCode.SQL_PARAMETER_ERROR,
                        "SQL_PARAMETER_ERROR"));
        AdminToolTestCase testCase = new AdminToolTestCase();
        ReflectionTestUtils.setField(testCase, "toolInvocationService", invocation);

        var report = testCase.execute("gateway-a", "orders", Map.of());

        assertFalse(report.isSuccess());
        assertEquals("SQL_PARAMETER_ERROR", report.getErrorCode());
        assertEquals("FAILED", report.getStages().get(3).getStatus().name());
        assertEquals("PENDING", report.getStages().get(4).getStatus().name());
    }
}
