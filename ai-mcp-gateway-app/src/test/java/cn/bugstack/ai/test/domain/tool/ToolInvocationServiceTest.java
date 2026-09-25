package cn.bugstack.ai.test.domain.tool;

import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.adapter.port.IToolAccessPolicyPort;
import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionPort;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.domain.tool.service.ToolArgumentValidator;
import cn.bugstack.ai.domain.tool.service.ToolInvocationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ToolInvocationServiceTest {

    @Test
    void executesTheSameResolvedBindingUsedByMcpToolCall() {
        ISessionRepository repository = mock(ISessionRepository.class);
        IToolAccessPolicyPort policy = mock(IToolAccessPolicyPort.class);
        IToolExecutionPort executor = mock(IToolExecutionPort.class);
        when(policy.isAllowed("gateway-a", "orders")).thenReturn(true);
        when(repository.queryMcpGatewayProtocolConfig("gateway-a", "orders"))
                .thenReturn(McpToolProtocolConfigVO.builder().protocolType("mysql").status(1)
                        .requestProtocolMappings(java.util.List.of(
                                McpToolProtocolConfigVO.ProtocolMapping.builder().fieldName("fromTime")
                                        .mcpPath("fromTime").mcpType("string").isRequired(1).build()))
                        .build());
        when(executor.execute(any())).thenReturn(ToolExecutionResult.structured("request-1", "query-1",
                java.util.List.of(Map.of("name", "orders")), java.util.List.of(java.util.List.of(1)), false));

        ToolInvocationService service = service(repository, policy, executor);
        ToolExecutionResult result = service.execute("gateway-a", "orders", Map.of("fromTime", "2026-01-01"), "request-1");

        assertTrue(result.isSuccess());
        assertEquals("query-1", result.getQueryId());
        verify(executor).execute(any());
    }

    @Test
    void disabledToolFailsBeforeExecution() {
        ISessionRepository repository = mock(ISessionRepository.class);
        IToolAccessPolicyPort policy = mock(IToolAccessPolicyPort.class);
        IToolExecutionPort executor = mock(IToolExecutionPort.class);
        when(policy.isAllowed("gateway-a", "orders")).thenReturn(true);
        when(repository.queryMcpGatewayProtocolConfig("gateway-a", "orders"))
                .thenReturn(McpToolProtocolConfigVO.builder().protocolType("mysql").status(0).build());

        ToolExecutionResult result = service(repository, policy, executor)
                .execute("gateway-a", "orders", Map.of(), "request-1");

        assertEquals(ToolExecutionErrorCode.TOOL_DISABLED, result.getErrorCode());
        verifyNoInteractions(executor);
    }

    @Test
    void accessDeniedDoesNotReadProtocolConfiguration() {
        ISessionRepository repository = mock(ISessionRepository.class);
        IToolAccessPolicyPort policy = mock(IToolAccessPolicyPort.class);
        IToolExecutionPort executor = mock(IToolExecutionPort.class);
        when(policy.isAllowed("gateway-a", "orders")).thenReturn(false);

        ToolExecutionResult result = service(repository, policy, executor)
                .execute("gateway-a", "orders", Map.of(), "request-1");

        assertEquals(ToolExecutionErrorCode.ACCESS_DENIED, result.getErrorCode());
        verify(repository, never()).queryMcpGatewayProtocolConfig(any(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void invalidArgumentsFailBeforeBackendExecution() {
        ISessionRepository repository = mock(ISessionRepository.class);
        IToolAccessPolicyPort policy = mock(IToolAccessPolicyPort.class);
        IToolExecutionPort executor = mock(IToolExecutionPort.class);
        when(policy.isAllowed("gateway-a", "orders")).thenReturn(true);
        when(repository.queryMcpGatewayProtocolConfig("gateway-a", "orders"))
                .thenReturn(McpToolProtocolConfigVO.builder().protocolType("mysql").status(1)
                        .requestProtocolMappings(java.util.List.of(
                                McpToolProtocolConfigVO.ProtocolMapping.builder().fieldName("orderId")
                                        .mcpPath("orderId").mcpType("integer").isRequired(1).build()))
                        .build());

        ToolExecutionResult result = service(repository, policy, executor)
                .execute("gateway-a", "orders", Map.of("orderId", "not-a-number"), "request-1");

        assertEquals(ToolExecutionErrorCode.INVALID_ARGUMENT, result.getErrorCode());
        verifyNoInteractions(executor);
    }

    @Test
    void scalarArgumentsAreRejectedBeforeBackendExecution() {
        ISessionRepository repository = mock(ISessionRepository.class);
        IToolAccessPolicyPort policy = mock(IToolAccessPolicyPort.class);
        IToolExecutionPort executor = mock(IToolExecutionPort.class);
        when(policy.isAllowed("gateway-a", "orders")).thenReturn(true);
        when(repository.queryMcpGatewayProtocolConfig("gateway-a", "orders"))
                .thenReturn(McpToolProtocolConfigVO.builder().protocolType("mysql").status(1).build());

        ToolExecutionResult result = service(repository, policy, executor)
                .execute("gateway-a", "orders", "not-an-object", "request-1");

        assertEquals(ToolExecutionErrorCode.INVALID_ARGUMENT, result.getErrorCode());
        verifyNoInteractions(executor);
    }

    private static ToolInvocationService service(ISessionRepository repository,
                                                   IToolAccessPolicyPort policy,
                                                   IToolExecutionPort executor) {
        ToolInvocationService service = new ToolInvocationService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "accessPolicy", policy);
        ReflectionTestUtils.setField(service, "toolExecutionPort", executor);
        ReflectionTestUtils.setField(service, "argumentValidator", new ToolArgumentValidator());
        return service;
    }
}
