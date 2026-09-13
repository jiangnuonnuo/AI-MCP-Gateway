package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.tool.adapter.port.IToolExecutionAuditPort;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionAuditRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** MVP 审计适配器；正式环境可替换为日志或审计仓储，不保存 SQL 参数和值。 */
@Component
public class InMemoryToolExecutionAudit implements IToolExecutionAuditPort {
    private CopyOnWriteArrayList<ToolExecutionAuditRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void record(ToolExecutionAuditRecord record) {
        if (record != null) records.add(record);
    }

    public List<ToolExecutionAuditRecord> records() {
        return List.copyOf(records);
    }
}
