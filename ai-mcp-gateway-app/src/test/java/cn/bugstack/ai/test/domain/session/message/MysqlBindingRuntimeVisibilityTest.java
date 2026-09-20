package cn.bugstack.ai.test.domain.session.message;

import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsListHandler;
import cn.bugstack.ai.domain.tool.adapter.port.IToolAccessPolicyPort;
import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MysqlBindingRuntimeVisibilityTest {

    @Test
    void disabledBindingIsAbsentFromToolsList() {
        ISessionRepository repository = mock(ISessionRepository.class);
        when(repository.queryMcpGatewayToolConfigListByGatewayId("gateway-1")).thenReturn(List.of(
                McpToolConfigVO.builder().gatewayId("gateway-1").toolName("orders").status(0)
                        .mcpToolProtocolConfigVO(McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(7L).status(1).build()).build()));
        ToolsListHandler handler = new ToolsListHandler();
        ReflectionTestUtils.setField(handler, "repository", repository);

        McpSchemaVO.JSONRPCResponse response = handler.handle("gateway-1",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "req-1", null));

        assertTrue(((java.util.Map<?, ?>) response.result()).get("tools") instanceof List<?> tools && tools.isEmpty());
    }

    @Test
    void disabledBindingCannotBeCalled() throws Exception {
        ISessionRepository repository = mock(ISessionRepository.class);
        when(repository.queryMcpGatewayProtocolConfig("gateway-1", "orders"))
                .thenReturn(McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(7L).status(0).build());
        IToolAccessPolicyPort accessPolicy = mock(IToolAccessPolicyPort.class);
        when(accessPolicy.isAllowed("gateway-1", "orders")).thenReturn(true);
        IToolExecutionPort execution = mock(IToolExecutionPort.class);
        ToolsCallHandler handler = new ToolsCallHandler();
        ReflectionTestUtils.setField(handler, "repository", repository);
        ReflectionTestUtils.setField(handler, "accessPolicy", accessPolicy);
        ReflectionTestUtils.setField(handler, "toolExecutionPort", execution);

        McpSchemaVO.JSONRPCResponse response = handler.handle("gateway-1", new McpSchemaVO.JSONRPCRequest(
                "2.0", "tools/call", "req-1", java.util.Map.of("name", "orders", "arguments", java.util.Map.of())));

        String json = new ObjectMapper().writeValueAsString(response.result());
        assertTrue(json.contains("TOOL_DISABLED"));
        verifyNoInteractions(execution);
    }
}
