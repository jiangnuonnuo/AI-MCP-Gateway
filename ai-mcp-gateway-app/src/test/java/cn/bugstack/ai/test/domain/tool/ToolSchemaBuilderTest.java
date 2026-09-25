package cn.bugstack.ai.test.domain.tool;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.session.service.message.ToolSchemaBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSchemaBuilderTest {

    @Test
    void alwaysBuildsObjectRootAndPreservesNestedRequiredFields() {
        McpToolProtocolConfigVO protocol = McpToolProtocolConfigVO.builder().status(1)
                .requestProtocolMappings(List.of(
                        mapping(null, "request", "request", "object", 1, 1),
                        mapping("request", "limit", "request.limit", "integer", 0, 2)))
                .build();
        McpToolConfigVO tool = McpToolConfigVO.builder().toolName("orders").status(1)
                .mcpToolProtocolConfigVO(protocol).build();

        var schema = ToolSchemaBuilder.build(List.of(tool)).get(0).inputSchema();

        assertEquals("object", schema.type());
        assertEquals(List.of("request"), schema.required());
        @SuppressWarnings("unchecked")
        var request = (java.util.Map<String, Object>) schema.properties().get("request");
        assertEquals("object", request.get("type"));
        assertTrue(((java.util.Map<?, ?>) request.get("properties")).containsKey("limit"));
    }

    @Test
    void hidesDisabledToolsAndUnavailableDataSources() {
        McpToolProtocolConfigVO.MysqlTemplateConfig unavailable = new McpToolProtocolConfigVO.MysqlTemplateConfig();
        unavailable.setDatasourceStatus(0);
        McpToolConfigVO disabled = McpToolConfigVO.builder().toolName("disabled").status(0)
                .mcpToolProtocolConfigVO(McpToolProtocolConfigVO.builder().status(1).build()).build();
        McpToolConfigVO unavailableTool = McpToolConfigVO.builder().toolName("unavailable").status(1)
                .mcpToolProtocolConfigVO(McpToolProtocolConfigVO.builder().status(1)
                        .mysqlTemplateConfig(unavailable).build()).build();

        var tools = ToolSchemaBuilder.build(List.of(disabled, unavailableTool));

        assertTrue(tools.isEmpty());
        assertFalse(tools.stream().anyMatch(tool -> "disabled".equals(tool.name())));
    }

    private static McpToolProtocolConfigVO.ProtocolMapping mapping(String parentPath, String fieldName,
                                                                    String mcpPath, String type,
                                                                    int required, int order) {
        return McpToolProtocolConfigVO.ProtocolMapping.builder().parentPath(parentPath).fieldName(fieldName)
                .mcpPath(mcpPath).mcpType(type).isRequired(required).sortOrder(order).build();
    }
}
