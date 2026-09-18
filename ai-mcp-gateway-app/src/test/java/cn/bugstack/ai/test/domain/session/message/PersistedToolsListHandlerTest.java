package cn.bugstack.ai.test.domain.session.message;

import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsListHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersistedToolsListHandlerTest {

    @Test
    void exposesOnlyStandardMcpToolFieldsForMysqlBindings() throws Exception {
        ISessionRepository repository = mock(ISessionRepository.class);
        McpToolProtocolConfigVO protocol = McpToolProtocolConfigVO.builder()
                .protocolType("mysql")
                .protocolId(900001L)
                .status(1)
                .requestProtocolMappings(List.of(McpToolProtocolConfigVO.ProtocolMapping.builder()
                        .mappingType("request").fieldName("fromTime").mcpPath("fromTime")
                        .mcpType("string").mcpDesc("Start time").isRequired(1).sortOrder(1).build()))
                .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                        .protocolId(900001L)
                        .datasourceRef("data-warehouse")
                        .datasourceStatus(1)
                        .sql("SELECT secret_column FROM fact_order WHERE order_time >= :fromTime")
                        .build())
                .build();
        when(repository.queryMcpGatewayToolConfigListByGatewayId("gateway-a"))
                .thenReturn(List.of(McpToolConfigVO.builder().gatewayId("gateway-a").toolId(1L)
                        .toolName("orderSummary").toolDescription("Order summary").toolVersion("1.0.0")
                        .status(1).mcpToolProtocolConfigVO(protocol).build()));
        ToolsListHandler handler = new ToolsListHandler();
        ReflectionTestUtils.setField(handler, "repository", repository);

        McpSchemaVO.JSONRPCResponse response = handler.handle("gateway-a",
                new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "request-1", null));
        String json = new ObjectMapper().writeValueAsString(response.result());

        assertTrue(json.contains("orderSummary"));
        assertTrue(json.contains("inputSchema"));
        assertFalse(json.contains("secret_column"));
        assertFalse(json.contains("data-warehouse"));
        assertFalse(json.contains("protocolId"));
        assertFalse(json.contains("jdbc"));
        assertFalse(json.contains("ciphertext"));
    }
}
