package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Tool 手动测试执行阶段 DTO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionStageDTO implements Serializable {
    private String name;
    private String label;
    private String status;
    private long durationMs;
    private String errorCode;
    private String errorMessage;
}
