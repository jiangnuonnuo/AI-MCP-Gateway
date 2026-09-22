package cn.bugstack.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/** 管理端 SQL 模板真实执行报告；结果保持原始 JSON 结构。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlTemplateTestDTO implements Serializable {
    private boolean success;
    private String templateRef;
    private String version;
    private String datasourceRef;
    private String queryId;
    private Map<String, Object> requestParameters;
    private Map<String, Object> requestJson;
    private List<MysqlExecutionStageDTO> stages;
    private long durationMs;
    private Map<String, Object> metrics;
    private Map<String, Object> responseJson;
    private String errorCode;
    private String errorMessage;
    private String failedStage;
}
