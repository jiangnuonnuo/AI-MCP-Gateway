package cn.bugstack.ai.test.domain.tool;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.service.ToolArgumentValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolArgumentValidatorTest {

    @Test
    void acceptsDeclaredNestedArguments() {
        ToolArgumentValidator validator = new ToolArgumentValidator();
        assertTrue(validator.isValid(Map.of("query", Map.of("id", 7, "active", true)), mappings()));
    }

    @Test
    void rejectsMissingExtraAndWrongTypeBeforeExecution() {
        ToolArgumentValidator validator = new ToolArgumentValidator();
        assertFalse(validator.isValid(Map.of("query", Map.of("active", true)), mappings()));
        assertFalse(validator.isValid(Map.of("query", Map.of("id", 7, "active", true, "token", "secret")), mappings()));
        assertFalse(validator.isValid(Map.of("query", Map.of("id", "7", "active", true)), mappings()));
    }

    private static List<McpToolProtocolConfigVO.ProtocolMapping> mappings() {
        return List.of(
                McpToolProtocolConfigVO.ProtocolMapping.builder().fieldName("query").mcpPath("query")
                        .mcpType("object").isRequired(1).build(),
                McpToolProtocolConfigVO.ProtocolMapping.builder().fieldName("id").parentPath("query")
                        .mcpPath("query.id").mcpType("integer").isRequired(1).build(),
                McpToolProtocolConfigVO.ProtocolMapping.builder().fieldName("active").parentPath("query")
                        .mcpPath("query.active").mcpType("boolean").isRequired(0).build());
    }
}
