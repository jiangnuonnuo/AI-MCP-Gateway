package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端模板测试的可观察执行阶段。
 *
 * <p>阶段名称是前后端契约，不能把 JDBC 异常或内部类名直接暴露给调用方。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlExecutionStage {

    /** 阶段稳定标识。 */
    private String name;

    /** 阶段展示名称。 */
    private String label;

    /** 阶段状态。 */
    private Status status;

    /** 阶段耗时毫秒。 */
    private long durationMs;

    /** 阶段错误码，仅失败阶段有值。 */
    private String errorCode;

    /** 阶段对外安全错误信息。 */
    private String errorMessage;

    public enum Status {
        PENDING, RUNNING, SUCCEEDED, FAILED
    }

    /** 将阶段转换为稳定 JSON 对象。 */
    public Map<String, Object> toMap() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", name);
        value.put("label", label);
        value.put("status", status == null ? null : status.name());
        value.put("durationMs", durationMs);
        if (errorCode != null) value.put("errorCode", errorCode);
        if (errorMessage != null) value.put("errorMessage", errorMessage);
        return value;
    }
}
