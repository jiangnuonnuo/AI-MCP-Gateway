package cn.bugstack.ai.domain.tool.adapter.port;

import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionContext;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionResult;

/**
 * Tool 执行入口。Domain 用例只依赖此端口，不感知 HTTP、JDBC 或连接池实现。
 */
public interface IToolExecutionPort {

    ToolExecutionResult execute(ToolExecutionContext context);
}
