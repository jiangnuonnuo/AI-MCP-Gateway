package cn.bugstack.ai.domain.tool.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tool 手动测试的稳定阶段，不暴露底层执行器或连接实现。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionStage {
    private String name;
    private String label;
    private Status status;
    private long durationMs;
    private String errorCode;
    private String errorMessage;

    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

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
