package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 SQL 模板测试报告。
 *
 * <p>报告承载真实执行链路的阶段、指标、请求快照和原始结构化结果，不把结果渲染成业务表格。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateTestReport {

    /** 是否执行成功。 */
    private boolean success;

    /** 模板引用。 */
    private String templateRef;

    /** 模板版本。 */
    private String version;

    /** 模板固定绑定的数据源引用。 */
    private String datasourceRef;

    /** 查询关联标识。 */
    private String queryId;

    /** 脱敏后的请求参数快照。 */
    private Map<String, Object> requestParameters;

    /** 执行阶段。 */
    private List<MysqlExecutionStage> stages;

    /** 请求总耗时毫秒。 */
    private long durationMs;

    /** 结果指标。 */
    private Map<String, Object> metrics;

    /** 原始结构化结果。 */
    private Map<String, Object> responseJson;

    /** 稳定错误码。 */
    private String errorCode;

    /** 安全错误信息。 */
    private String errorMessage;

    /** 失败阶段。 */
    private String failedStage;

    /** 转换为管理端返回所需的稳定结构。 */
    public Map<String, Object> toStructuredMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("success", success);
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("id", templateRef);
        template.put("version", version);
        value.put("template", template);
        Map<String, Object> datasource = new LinkedHashMap<>();
        if (datasourceRef != null) datasource.put("ref", datasourceRef);
        value.put("datasource", datasource);
        value.put("queryId", queryId);
        value.put("requestParameters", requestParameters == null ? Map.of() : requestParameters);
        value.put("requestJson", requestParameters == null ? Map.of() : requestParameters);
        value.put("stages", stages == null ? List.of() : stages.stream().map(MysqlExecutionStage::toMap).toList());
        value.put("durationMs", durationMs);
        value.put("metrics", metrics == null ? Map.of() : metrics);
        value.put("responseJson", responseJson == null ? Map.of() : responseJson);
        if (errorCode != null) value.put("errorCode", errorCode);
        if (errorMessage != null) value.put("errorMessage", errorMessage);
        if (failedStage != null) value.put("failedStage", failedStage);
        return value;
    }
}
