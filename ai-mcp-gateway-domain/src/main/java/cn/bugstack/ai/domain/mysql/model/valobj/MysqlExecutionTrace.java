package cn.bugstack.ai.domain.mysql.model.valobj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次模板测试的阶段追踪器。
 *
 * <p>追踪器只保存稳定的阶段状态、耗时和安全错误信息，不保存 SQL 参数值或 JDBC 异常堆栈。</p>
 */
public final class MysqlExecutionTrace {

    /** 固定阶段定义，顺序也是管理端时间线顺序。 */
    private static final List<StageDefinition> DEFINITIONS = List.of(
            new StageDefinition("PARAMETER_VALIDATION", "参数校验"),
            new StageDefinition("POLICY_VALIDATION", "只读策略校验"),
            new StageDefinition("DATASOURCE_CONNECTION", "数据源连接"),
            new StageDefinition("SQL_EXECUTION", "SQL 执行"),
            new StageDefinition("RESPONSE_ASSEMBLY", "结果组装")
    );

    private final Map<String, MysqlExecutionStage> stages = new LinkedHashMap<>();
    private final Map<String, Long> startedAt = new LinkedHashMap<>();

    public MysqlExecutionTrace() {
        DEFINITIONS.forEach(definition -> stages.put(definition.name(), MysqlExecutionStage.builder()
                .name(definition.name())
                .label(definition.label())
                .status(MysqlExecutionStage.Status.PENDING)
                .build()));
    }

    public void start(String name) {
        MysqlExecutionStage stage = stage(name);
        stage.setStatus(MysqlExecutionStage.Status.RUNNING);
        startedAt.put(name, System.nanoTime());
    }

    public void succeed(String name) {
        MysqlExecutionStage stage = stage(name);
        stage.setStatus(MysqlExecutionStage.Status.SUCCEEDED);
        stage.setDurationMs(duration(name));
    }

    public void fail(String name, String code, String message) {
        MysqlExecutionStage stage = stage(name);
        stage.setStatus(MysqlExecutionStage.Status.FAILED);
        stage.setDurationMs(duration(name));
        stage.setErrorCode(code);
        stage.setErrorMessage(message);
    }

    /** 把尚未开始的后续阶段保持为 PENDING，返回不可变快照。 */
    public List<MysqlExecutionStage> snapshot() {
        List<MysqlExecutionStage> copy = new ArrayList<>();
        stages.values().forEach(value -> copy.add(MysqlExecutionStage.builder()
                .name(value.getName()).label(value.getLabel()).status(value.getStatus())
                .durationMs(value.getDurationMs()).errorCode(value.getErrorCode())
                .errorMessage(value.getErrorMessage()).build()));
        return Collections.unmodifiableList(copy);
    }

    public String currentStage() {
        return stages.values().stream()
                .filter(value -> value.getStatus() == MysqlExecutionStage.Status.RUNNING)
                .map(MysqlExecutionStage::getName).findFirst().orElse(null);
    }

    private MysqlExecutionStage stage(String name) {
        MysqlExecutionStage stage = stages.get(name);
        if (stage == null) throw new IllegalArgumentException("unsupported mysql execution stage");
        return stage;
    }

    private long duration(String name) {
        Long start = startedAt.get(name);
        return start == null ? 0L : Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }

    private record StageDefinition(String name, String label) {
    }
}
