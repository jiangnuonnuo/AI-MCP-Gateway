package cn.bugstack.ai.cases.admin.mysql;

import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolManualTestReport;

import java.util.List;
import java.util.Map;

/** 统一测试中心的 Tool 目录和手动调用用例入口。 */
public interface IAdminToolTestCase {
    List<McpSchemaVO.Tool> listTools(String gatewayId);

    ToolManualTestReport execute(String gatewayId, String toolName, Map<String, Object> arguments);
}
