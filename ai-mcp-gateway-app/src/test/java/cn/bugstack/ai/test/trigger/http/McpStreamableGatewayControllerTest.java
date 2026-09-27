package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.cases.mcp.IMcpMessageService;
import cn.bugstack.ai.cases.mcp.IMcpSessionService;
import cn.bugstack.ai.types.exception.AppException;
import cn.bugstack.ai.trigger.http.McpStreamableGatewayController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class McpStreamableGatewayControllerTest {

    @Test
    void returnsBusinessErrorWhenInitializeAuthenticationFails() throws Exception {
        IMcpMessageService<String> messageService = mock(IMcpMessageService.class);
        doThrow(new AppException("-32006", "fail to auth apikey"))
                .when(messageService).handleMessage(any());

        McpStreamableGatewayController controller = new McpStreamableGatewayController();
        ReflectionTestUtils.setField(controller, "mcpStreamableMessageService", messageService);
        ReflectionTestUtils.setField(controller, "mcpStreamableSessionService", mock(IMcpSessionService.class));

        ResponseEntity<?> response = controller.handlePost(
                "gateway-a", "", null, null,
                "{\"jsonrpc\":\"2.0\",\"method\":\"initialize\",\"id\":1}",
                new HttpHeaders()).block();

        assertEquals(500, response.getStatusCodeValue());
        assertTrue(String.valueOf(response.getBody()).contains("fail to auth apikey"));
    }
}
