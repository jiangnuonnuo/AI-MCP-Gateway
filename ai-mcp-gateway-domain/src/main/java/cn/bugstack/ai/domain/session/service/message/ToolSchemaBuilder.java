package cn.bugstack.ai.domain.session.service.message;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将持久化 Tool 输入映射转换为 MCP 输入 Schema。
 *
 * <p>Schema 只暴露工具名称、描述和参数结构，不把协议、数据源、SQL 或凭证信息带到客户端。</p>
 */
public final class ToolSchemaBuilder {

    private ToolSchemaBuilder() {
    }

    /**
     * 构建当前 Gateway 可见的 MCP Tool 列表。
     */
    public static List<McpSchemaVO.Tool> build(List<McpToolConfigVO> toolConfigs) {
        List<McpSchemaVO.Tool> tools = new ArrayList<>();
        if (toolConfigs == null) return tools;
        for (McpToolConfigVO toolConfig : toolConfigs) {
            if (toolConfig == null || !toolConfig.isEnabled() || !hasEnabledProtocol(toolConfig)) continue;
            McpToolProtocolConfigVO protocol = toolConfig.getMcpToolProtocolConfigVO();
            List<McpToolProtocolConfigVO.ProtocolMapping> mappings = protocol == null
                    || protocol.getRequestProtocolMappings() == null
                    ? new ArrayList<>() : new ArrayList<>(protocol.getRequestProtocolMappings());
            mappings.sort(ToolSchemaBuilder::sortMapping);

            Map<String, List<McpToolProtocolConfigVO.ProtocolMapping>> children = new HashMap<>();
            List<McpToolProtocolConfigVO.ProtocolMapping> roots = new ArrayList<>();
            for (McpToolProtocolConfigVO.ProtocolMapping mapping : mappings) {
                if (mapping == null) continue;
                if (mapping.getParentPath() == null) roots.add(mapping);
                else children.computeIfAbsent(mapping.getParentPath(), ignored -> new ArrayList<>()).add(mapping);
            }
            roots.sort(ToolSchemaBuilder::sortMapping);

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (McpToolProtocolConfigVO.ProtocolMapping root : roots) {
                properties.put(root.getFieldName(), buildProperty(root, children));
                if (Integer.valueOf(1).equals(root.getIsRequired())) required.add(root.getFieldName());
            }

            McpSchemaVO.JsonSchema schema = new McpSchemaVO.JsonSchema("object", properties,
                    required.isEmpty() ? null : required, false, null, null);
            tools.add(new McpSchemaVO.Tool(toolConfig.getToolName(), toolConfig.getToolDescription(), schema));
        }
        return tools;
    }

    private static boolean hasEnabledProtocol(McpToolConfigVO toolConfig) {
        McpToolProtocolConfigVO protocol = toolConfig.getMcpToolProtocolConfigVO();
        if (protocol == null) return false;
        if (protocol.getStatus() != null && protocol.getStatus() != 1) return false;
        McpToolProtocolConfigVO.MysqlTemplateConfig mysql = protocol.getMysqlTemplateConfig();
        return mysql == null || mysql.getDatasourceStatus() == null || mysql.getDatasourceStatus() == 1;
    }

    private static int sortMapping(McpToolProtocolConfigVO.ProtocolMapping left,
                                   McpToolProtocolConfigVO.ProtocolMapping right) {
        int leftOrder = left == null || left.getSortOrder() == null ? 0 : left.getSortOrder();
        int rightOrder = right == null || right.getSortOrder() == null ? 0 : right.getSortOrder();
        return Integer.compare(leftOrder, rightOrder);
    }

    private static Map<String, Object> buildProperty(McpToolProtocolConfigVO.ProtocolMapping current,
                                                       Map<String, List<McpToolProtocolConfigVO.ProtocolMapping>> childrenMap) {
        Map<String, Object> property = new LinkedHashMap<>();
        if (current == null) return property;
        property.put("type", current.getMcpType());
        if (current.getMcpDesc() != null) property.put("description", current.getMcpDesc());

        List<McpToolProtocolConfigVO.ProtocolMapping> children = childrenMap.get(current.getMcpPath());
        if (children == null || children.isEmpty()) return property;
        children.sort(ToolSchemaBuilder::sortMapping);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (McpToolProtocolConfigVO.ProtocolMapping child : children) {
            properties.put(child.getFieldName(), buildProperty(child, childrenMap));
            if (Integer.valueOf(1).equals(child.getIsRequired())) required.add(child.getFieldName());
        }
        property.put("properties", properties);
        if (!required.isEmpty()) property.put("required", required);
        return property;
    }
}
