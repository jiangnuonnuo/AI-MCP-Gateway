package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpGatewayConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolProtocolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.infrastructure.dao.IMcpDataSourceDao;
import cn.bugstack.ai.infrastructure.dao.IMcpGatewayDao;
import cn.bugstack.ai.infrastructure.dao.IMcpGatewayToolDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolHttpDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMappingDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMysqlDao;
import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import cn.bugstack.ai.infrastructure.dao.po.McpGatewayPO;
import cn.bugstack.ai.infrastructure.dao.po.McpGatewayToolPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolHttpPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMappingPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMysqlPO;
import cn.bugstack.ai.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** MCP 会话读取仓储，发现和调用均从当前 Gateway 的持久化 Tool 绑定出发。 */
@Repository
public class SessionRepository implements ISessionRepository {

    @Resource private IMcpGatewayDao mcpGatewayDao;
    @Resource private IMcpGatewayToolDao mcpGatewayToolDao;
    @Resource private IMcpProtocolHttpDao mcpProtocolHttpDao;
    @Resource private IMcpProtocolMysqlDao mcpProtocolMysqlDao;
    @Resource private IMcpDataSourceDao mcpDataSourceDao;
    @Resource private IMcpProtocolMappingDao mcpProtocolMappingDao;

    @Override
    public McpGatewayConfigVO queryMcpGatewayConfigByGatewayId(String gatewayId) {
        try {
            McpGatewayPO po = mcpGatewayDao.queryMcpGatewayByGatewayId(gatewayId);
            if (po == null) return null;
            return McpGatewayConfigVO.builder().gatewayId(po.getGatewayId()).gatewayName(po.getGatewayName())
                    .gatewayDesc(po.getGatewayDesc()).version(po.getVersion()).build();
        } catch (DataAccessException e) {
            throw configurationUnavailable(e);
        }
    }

    @Override
    public List<McpToolConfigVO> queryMcpGatewayToolConfigListByGatewayId(String gatewayId) {
        try {
            List<McpToolConfigVO> result = new ArrayList<>();
            List<McpGatewayToolPO> tools = mcpGatewayToolDao.queryEnabledByGatewayId(gatewayId);
            if (tools == null) return result;
            for (McpGatewayToolPO tool : tools) {
                McpToolProtocolConfigVO protocol = resolveProtocol(tool);
                if (protocol == null || !isEnabled(protocol)) continue;
                result.add(toTool(tool, protocol));
            }
            return result;
        } catch (DataAccessException e) {
            throw configurationUnavailable(e);
        }
    }

    @Override
    public McpToolProtocolConfigVO queryMcpGatewayProtocolConfig(String gatewayId, String toolName) {
        try {
            McpGatewayToolPO query = McpGatewayToolPO.builder().gatewayId(gatewayId).toolName(toolName).build();
            McpGatewayToolPO tool = mcpGatewayToolDao.queryByGatewayIdAndToolName(query);
            if (tool == null) return null;
            return resolveProtocol(tool);
        } catch (DataAccessException e) {
            throw configurationUnavailable(e);
        }
    }

    private McpToolConfigVO toTool(McpGatewayToolPO tool, McpToolProtocolConfigVO protocol) {
        return McpToolConfigVO.builder().gatewayId(tool.getGatewayId()).toolId(tool.getToolId())
                .toolName(tool.getToolName()).toolDescription(tool.getToolDescription())
                .toolVersion(tool.getToolVersion()).status(tool.getStatus())
                .mcpToolProtocolConfigVO(protocol).build();
    }

    private McpToolProtocolConfigVO resolveProtocol(McpGatewayToolPO tool) {
        String protocolType = normalizeType(tool.getProtocolType());
        List<McpProtocolMappingPO> mappingRows = mcpProtocolMappingDao.queryByProtocolKey(
                McpProtocolMappingPO.builder().protocolType(protocolType).protocolId(tool.getProtocolId()).build());
        if (mappingRows == null) mappingRows = List.of();
        List<McpToolProtocolConfigVO.ProtocolMapping> request = new ArrayList<>();
        List<McpToolProtocolConfigVO.ProtocolMapping> response = new ArrayList<>();
        for (McpProtocolMappingPO row : mappingRows) {
            McpToolProtocolConfigVO.ProtocolMapping mapping = toMapping(row);
            if ("request".equalsIgnoreCase(row.getMappingType())) request.add(mapping);
            else if ("response".equalsIgnoreCase(row.getMappingType())) response.add(mapping);
        }
        if ("mysql".equals(protocolType)) {
            McpProtocolMysqlPO mysql = mcpProtocolMysqlDao.queryByProtocolId(tool.getProtocolId());
            if (mysql == null) return null;
            McpDataSourcePO dataSource = mcpDataSourceDao.queryById(mysql.getDatasourceId());
            if (dataSource == null) return null;
            // 迁移前的历史行没有 execution_mode；在会话读取边界按既有 MySQL 模板兼容处理。
            ToolExecutionMode executionMode = mysql.getExecutionMode() == null
                    || mysql.getExecutionMode().isBlank()
                    ? ToolExecutionMode.MYSQL_TEMPLATE
                    : ToolExecutionMode.from(mysql.getExecutionMode());
            if (executionMode == ToolExecutionMode.UNKNOWN) return null;
            List<cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter> parameters = new ArrayList<>();
            if (executionMode == ToolExecutionMode.MYSQL_TEMPLATE) {
                for (McpToolProtocolConfigVO.ProtocolMapping row : request) {
                    parameters.add(cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter.builder()
                            .name(row.getFieldName()).type(mysqlType(row.getMcpType()))
                            .required(Integer.valueOf(1).equals(row.getIsRequired())).description(row.getMcpDesc()).build());
                }
            }
            MysqlQueryPolicy policy = new MysqlQueryPolicy(64 * 1024,
                    positive(mysql.getMaxRows(), 1_000), positive(mysql.getMaxResultBytes(), 4 * 1024 * 1024L),
                    positive(mysql.getMaxColumns(), 128), positive(mysql.getTimeoutMs(), 30_000), true);
            return McpToolProtocolConfigVO.builder().protocolType("mysql").protocolId(tool.getProtocolId())
                    .status(effectiveStatus(tool.getStatus(), mysql.getStatus())).backendType(ToolBackendType.MYSQL)
                    .executionMode(executionMode).requestProtocolMappings(request)
                    .responseProtocolMappings(response)
                    .mysqlTemplateConfig(McpToolProtocolConfigVO.MysqlTemplateConfig.builder()
                            .protocolId(tool.getProtocolId()).templateRef(String.valueOf(tool.getProtocolId()))
                            .templateVersion(tool.getToolVersion()).datasourceRef(dataSource.getDatasourceRef())
                            .datasourceStatus(dataSource.getStatus()).sql(mysql.getSqlText())
                            .parameters(parameters).policy(policy).build()).build();
        }
        if (!"http".equals(protocolType)) return null;
        McpProtocolHttpPO http = mcpProtocolHttpDao.queryMcpProtocolHttpByProtocolId(tool.getProtocolId());
        if (http == null) return null;
        McpToolProtocolConfigVO.HTTPConfig config = new McpToolProtocolConfigVO.HTTPConfig();
        config.setHttpUrl(http.getHttpUrl());
        config.setHttpHeaders(http.getHttpHeaders());
        config.setHttpMethod(http.getHttpMethod());
        config.setTimeout(http.getTimeout());
        return McpToolProtocolConfigVO.builder().protocolType("http").protocolId(tool.getProtocolId())
                .status(effectiveStatus(tool.getStatus(), http.getStatus())).backendType(ToolBackendType.HTTP).executionMode(ToolExecutionMode.HTTP_REQUEST)
                .httpConfig(config).requestProtocolMappings(request).responseProtocolMappings(response).build();
    }

    private static McpToolProtocolConfigVO.ProtocolMapping toMapping(McpProtocolMappingPO row) {
        return McpToolProtocolConfigVO.ProtocolMapping.builder().mappingType(row.getMappingType())
                .parentPath(row.getParentPath()).fieldName(row.getFieldName()).mcpPath(row.getMcpPath())
                .mcpType(row.getMcpType()).mcpDesc(row.getMcpDesc()).isRequired(row.getIsRequired())
                .sortOrder(row.getSortOrder()).build();
    }

    private static boolean isEnabled(McpToolProtocolConfigVO protocol) {
        if (protocol.getStatus() != null && protocol.getStatus() != 1) return false;
        McpToolProtocolConfigVO.MysqlTemplateConfig mysql = protocol.getMysqlTemplateConfig();
        return mysql == null || mysql.getDatasourceStatus() == null || mysql.getDatasourceStatus() == 1;
    }

    private static Integer effectiveStatus(Integer toolStatus, Integer protocolStatus) {
        return (toolStatus == null || Integer.valueOf(1).equals(toolStatus))
                && (protocolStatus == null || Integer.valueOf(1).equals(protocolStatus)) ? 1 : 0;
    }

    private static String normalizeType(String type) { return type == null || type.isBlank() ? "http" : type.toLowerCase(); }

    private static MysqlParameterType mysqlType(String type) {
        if (type == null) return MysqlParameterType.STRING;
        return switch (type.toLowerCase()) {
            case "integer", "int" -> MysqlParameterType.INTEGER;
            case "long", "bigint" -> MysqlParameterType.LONG;
            case "number", "decimal" -> MysqlParameterType.DECIMAL;
            case "boolean", "bool" -> MysqlParameterType.BOOLEAN;
            case "date" -> MysqlParameterType.DATE;
            case "time" -> MysqlParameterType.TIME;
            case "datetime" -> MysqlParameterType.DATETIME;
            case "timestamp" -> MysqlParameterType.TIMESTAMP;
            default -> MysqlParameterType.STRING;
        };
    }

    private static int positive(Integer value, int fallback) { return value == null || value <= 0 ? fallback : value; }
    private static long positive(Long value, long fallback) { return value == null || value <= 0 ? fallback : value; }

    private static AppException configurationUnavailable(DataAccessException cause) {
        return new AppException("CONTROL_PLANE_UNAVAILABLE", "Gateway configuration is unavailable", cause);
    }
}
