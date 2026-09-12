package cn.bugstack.ai.domain.tool.adapter.port;

import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionAuditRecord;

/** Tool 调用审计端口；实现不得记录密码、Token、Authorization 或完整结果。 */
public interface IToolExecutionAuditPort {
    void record(ToolExecutionAuditRecord record);
}
