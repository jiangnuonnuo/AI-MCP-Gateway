package cn.bugstack.ai.domain.tool.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端手动 Tool 测试报告。
 *
 * <p>报告只保存请求生命周期内的安全快照和正式 Tool 结果，不承担绑定配置持久化。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolManualTestReport {

    /** 是否执行成功。 */
    private boolean success;

    /** 本次管理测试标识。 */
    private String testId;

    /** Gateway 标识。 */
    private String gatewayId;

    /** Tool 名称。 */
    private String toolName;

    /** 下游请求标识。 */
    private String requestId;

    /** MySQL Tool 产生的查询标识。 */
    private String queryId;

    /** 脱敏后的参数快照。 */
    private Map<String, Object> requestArguments;

    /** 正式 MCP tools/call 结果。 */
    private Map<String, Object> result;

    /** Tool 调用过程中的稳定阶段。 */
    private List<ToolExecutionStage> stages;

    /** 执行耗时。 */
    private long durationMs;

    /** 稳定错误码。 */
    private String errorCode;

    /** 非敏感错误提示。 */
    private String errorMessage;

    /** 转为前端原始 JSON 视图的稳定结构。 */
    public Map<String, Object> toStructuredMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("success", success);
        value.put("testId", testId);
        value.put("gatewayId", gatewayId);
        value.put("toolName", toolName);
        value.put("requestId", requestId);
        value.put("queryId", queryId);
        value.put("requestArguments", requestArguments == null ? Map.of() : requestArguments);
        value.put("requestJson", requestArguments == null ? Map.of() : requestArguments);
        value.put("result", result == null ? Map.of() : result);
        value.put("responseJson", result == null ? Map.of() : result);
        value.put("stages", stages == null ? List.of() : stages.stream().map(ToolExecutionStage::toMap).toList());
        value.put("durationMs", durationMs);
        if (errorCode != null) value.put("errorCode", errorCode);
        if (errorMessage != null) value.put("errorMessage", errorMessage);
        return value;
    }
}
