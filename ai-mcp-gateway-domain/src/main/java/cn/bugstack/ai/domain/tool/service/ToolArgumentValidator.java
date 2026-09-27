package cn.bugstack.ai.domain.tool.service;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验管理端或 MCP Client 提交的 Tool 参数是否符合当前绑定的输入契约。
 */
@Component
public class ToolArgumentValidator {

    /** 动态 SQL Tool 的顶层契约校验；SQL 语义由 MySQL 治理责任链继续校验。 */
    public boolean isDynamicValid(Map<String, ?> arguments) {
        if (arguments == null || !arguments.containsKey("sql") || !arguments.containsKey("parameters")) return false;
        if (arguments.size() != 2 || !(arguments.get("sql") instanceof CharSequence)
                || !(arguments.get("parameters") instanceof Map<?, ?>)) return false;
        Map<?, ?> parameters = (Map<?, ?>) arguments.get("parameters");
        for (Map.Entry<?, ?> entry : parameters.entrySet()) {
            if (entry.getKey() == null || String.valueOf(entry.getKey()).isBlank()
                    || !isScalar(entry.getValue())) return false;
        }
        return !String.valueOf(arguments.get("sql")).isBlank();
    }

    private boolean isScalar(Object value) {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean;
    }

    public boolean isValid(Map<String, ?> arguments,
                           List<McpToolProtocolConfigVO.ProtocolMapping> mappings) {
        Map<String, Object> values = arguments == null ? Map.of() : new HashMap<>(arguments);
        List<McpToolProtocolConfigVO.ProtocolMapping> definitions = mappings == null ? List.of() : mappings;
        Map<String, McpToolProtocolConfigVO.ProtocolMapping> roots = new HashMap<>();
        Map<String, List<McpToolProtocolConfigVO.ProtocolMapping>> children = new HashMap<>();
        for (McpToolProtocolConfigVO.ProtocolMapping mapping : definitions) {
            if (mapping == null || mapping.getFieldName() == null) return false;
            if (mapping.getParentPath() == null) roots.put(mapping.getFieldName(), mapping);
            else children.computeIfAbsent(mapping.getParentPath(), ignored -> new java.util.ArrayList<>()).add(mapping);
        }
        if (!values.keySet().stream().allMatch(roots::containsKey)) return false;
        for (Map.Entry<String, McpToolProtocolConfigVO.ProtocolMapping> entry : roots.entrySet()) {
            Object value = values.get(entry.getKey());
            if (Integer.valueOf(1).equals(entry.getValue().getIsRequired()) && value == null) return false;
            if (value != null && !validateValue(value, entry.getValue(), children)) return false;
        }
        return true;
    }

    private boolean validateValue(Object value,
                                  McpToolProtocolConfigVO.ProtocolMapping definition,
                                  Map<String, List<McpToolProtocolConfigVO.ProtocolMapping>> children) {
        if (!matchesType(value, definition.getMcpType())) return false;
        List<McpToolProtocolConfigVO.ProtocolMapping> nested = children.get(definition.getMcpPath());
        if (nested == null || nested.isEmpty()) return true;
        if (!(value instanceof Map<?, ?> raw)) return false;
        Map<String, Object> map = new HashMap<>();
        raw.forEach((key, item) -> map.put(String.valueOf(key), item));
        Map<String, McpToolProtocolConfigVO.ProtocolMapping> definitions = new HashMap<>();
        for (McpToolProtocolConfigVO.ProtocolMapping child : nested) definitions.put(child.getFieldName(), child);
        if (!map.keySet().stream().allMatch(definitions::containsKey)) return false;
        for (Map.Entry<String, McpToolProtocolConfigVO.ProtocolMapping> child : definitions.entrySet()) {
            Object childValue = map.get(child.getKey());
            if (Integer.valueOf(1).equals(child.getValue().getIsRequired()) && childValue == null) return false;
            if (childValue != null && !validateValue(childValue, child.getValue(), children)) return false;
        }
        return true;
    }

    private boolean matchesType(Object value, String type) {
        if (value == null || type == null || type.isBlank()) return true;
        return switch (type.toLowerCase()) {
            case "string" -> value instanceof CharSequence;
            case "integer", "int", "long", "bigint" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long || value instanceof BigInteger;
            case "number", "decimal" -> value instanceof Number;
            case "boolean", "bool" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            default -> true;
        };
    }
}
