package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.api.dto.AgentTestDTO;
import cn.bugstack.ai.api.dto.AgentTestRequestDTO;
import cn.bugstack.ai.api.dto.ToolManualTestDTO;
import cn.bugstack.ai.api.dto.ToolManualTestRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.cases.admin.IAdminLLMService;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlManageService;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlTemplateTestCase;
import cn.bugstack.ai.cases.admin.mysql.IAdminToolTestCase;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolManualTestReport;
import cn.bugstack.ai.trigger.http.AdminController;
import cn.bugstack.ai.trigger.http.AdminMysqlController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestCenterControllerContractTest {

    @Test
    void toolCallForwardsOnlyGatewayToolAndArgumentsAndReturnsQueryId() {
        IAdminToolTestCase toolCase = mock(IAdminToolTestCase.class);
        when(toolCase.execute(eq("gateway-a"), eq("orders"), eq(Map.of("fromTime", "2026-09-01"))))
                .thenReturn(ToolManualTestReport.builder().success(true).testId("test-1").gatewayId("gateway-a")
                        .toolName("orders").requestId("request-1").queryId("query-1")
                        .requestArguments(Map.of("fromTime", "2026-09-01"))
                        .result(Map.of("isError", false, "queryId", "query-1")).durationMs(12).build());
        AdminMysqlController controller = new AdminMysqlController();
        ReflectionTestUtils.setField(controller, "adminMysqlManageService", mock(IAdminMysqlManageService.class));
        ReflectionTestUtils.setField(controller, "adminMysqlTemplateTestCase", mock(IAdminMysqlTemplateTestCase.class));
        ReflectionTestUtils.setField(controller, "adminToolTestCase", toolCase);

        Response<ToolManualTestDTO> response = controller.testCenterToolCall(ToolManualTestRequestDTO.builder()
                .gatewayId("gateway-a").toolName("orders").arguments(Map.of("fromTime", "2026-09-01")).build());

        assertEquals("0000", response.getCode());
        assertEquals("query-1", response.getData().getQueryId());
        verify(toolCase).execute("gateway-a", "orders", Map.of("fromTime", "2026-09-01"));
    }

    @Test
    void toolDirectoryReturnsSchemaWithoutBindingOperations() {
        IAdminToolTestCase toolCase = mock(IAdminToolTestCase.class);
        when(toolCase.listTools("gateway-a")).thenReturn(List.of(new McpSchemaVO.Tool("orders", "read orders",
                new McpSchemaVO.JsonSchema("object", Map.of("fromTime", Map.of("type", "string")),
                        List.of("fromTime"), false, null, null))));
        AdminMysqlController controller = new AdminMysqlController();
        ReflectionTestUtils.setField(controller, "adminMysqlManageService", mock(IAdminMysqlManageService.class));
        ReflectionTestUtils.setField(controller, "adminMysqlTemplateTestCase", mock(IAdminMysqlTemplateTestCase.class));
        ReflectionTestUtils.setField(controller, "adminToolTestCase", toolCase);

        var response = controller.queryTestCenterTools("gateway-a");

        assertEquals("0000", response.getCode());
        assertEquals("orders", response.getData().get(0).getName());
        assertTrue(response.getData().get(0).getInputSchema().containsKey("properties"));
    }

    @Test
    void agentEndpointPreservesTraceReportAndStableFailureCode() {
        IAdminLLMService llmService = mock(IAdminLLMService.class);
        when(llmService.testAgentGateway(org.mockito.ArgumentMatchers.any())).thenReturn(AgentTestDTO.builder()
                .success(false).agentTestId("agent-1").errorCode("AGENT_EXECUTION_FAILED")
                .errorMessage("Agent test failed").events(List.of()).build());
        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "adminLLMService", llmService);

        Response<AgentTestDTO> response = controller.testAgentGateway(AgentTestRequestDTO.builder()
                .gatewayId("gateway-a").message("query orders").build());

        assertEquals("AGENT_EXECUTION_FAILED", response.getCode());
        assertEquals("agent-1", response.getData().getAgentTestId());
    }
}
