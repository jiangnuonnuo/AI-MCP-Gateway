package cn.bugstack.ai.cases.admin.mysql;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;
import cn.bugstack.ai.domain.tool.model.valobj.ToolManualTestReport;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionErrorCode;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionStage;
import cn.bugstack.ai.domain.tool.service.ToolInvocationService;
import cn.bugstack.ai.types.security.SensitiveDataSanitizer;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理端 Tool 手动测试 Case；只读调用当前 Gateway 已有绑定，不提供任何绑定写操作。
 */
@Service
public class AdminToolTestCase implements IAdminToolTestCase {

    @Resource
    private ToolInvocationService toolInvocationService;

    @Override
    public List<McpSchemaVO.Tool> listTools(String gatewayId) {
        if (gatewayId == null || gatewayId.isBlank()) return List.of();
        return toolInvocationService.listTools(gatewayId);
    }

    @Override
    public ToolManualTestReport execute(String gatewayId, String toolName, Map<String, Object> arguments) {
        String testId = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        Map<String, Object> safeArguments = SensitiveDataSanitizer.sanitizeMap(arguments);
        ToolExecutionResult result = toolInvocationService.execute(gatewayId, toolName, arguments, requestId);
        Map<String, Object> safeResult = SensitiveDataSanitizer.sanitizeMap(result.toMcpResult());
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        String errorCode = result.getErrorCode() == null || result.isSuccess() ? null : result.getErrorCode().name();
        String errorMessage = result.isSuccess() ? null : result.getErrorMessage();
        return ToolManualTestReport.builder()
                .success(result.isSuccess())
                .testId(testId)
                .gatewayId(gatewayId)
                .toolName(toolName)
                .requestId(requestId)
                .queryId(result.getQueryId())
                .requestArguments(safeArguments)
                .result(safeResult)
                .stages(buildStages(result, durationMs))
                .durationMs(durationMs)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private List<ToolExecutionStage> buildStages(ToolExecutionResult result, long durationMs) {
        List<String> names = List.of("TOOL_RESOLUTION", "PARAMETER_VALIDATION", "POLICY_VALIDATION", "TOOL_EXECUTION", "RESPONSE_ASSEMBLY");
        List<String> labels = List.of("Tool 解析", "参数校验", "策略校验", "Tool 执行", "结果组装");
        ToolExecutionErrorCode error = result.getErrorCode() == null
                ? ToolExecutionErrorCode.INTERNAL_ERROR : result.getErrorCode();
        int failedIndex = -1;
        if (!result.isSuccess()) {
            failedIndex = switch (error) {
                case INVALID_ARGUMENT -> 1;
                case ACCESS_DENIED -> 2;
                case TOOL_NOT_FOUND, TOOL_DISABLED, MISSING_CONFIGURATION, CONTROL_PLANE_UNAVAILABLE,
                        DATASOURCE_UNAVAILABLE -> 0;
                default -> 3;
            };
        }
        List<ToolExecutionStage> stages = new java.util.ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            ToolExecutionStage.Status status = failedIndex < 0 ? ToolExecutionStage.Status.SUCCEEDED
                    : i < failedIndex ? ToolExecutionStage.Status.SUCCEEDED
                    : i == failedIndex ? ToolExecutionStage.Status.FAILED : ToolExecutionStage.Status.PENDING;
            stages.add(ToolExecutionStage.builder().name(names.get(i)).label(labels.get(i)).status(status)
                    .durationMs(i == failedIndex || (failedIndex < 0 && i == names.size() - 1) ? durationMs : 0L)
                    .errorCode(i == failedIndex ? error.name() : null)
                    .errorMessage(i == failedIndex ? result.getErrorMessage() : null).build());
        }
        return stages;
    }
}
