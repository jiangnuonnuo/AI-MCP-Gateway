package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 管理端模板测试执行阶段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlExecutionStageDTO implements Serializable {
    private String name;
    private String label;
    private String status;
    private long durationMs;
    private String errorCode;
    private String errorMessage;
}
