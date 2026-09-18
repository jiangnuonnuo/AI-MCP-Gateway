package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.infrastructure.dao.IMcpDataSourceDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMappingDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMysqlDao;
import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMappingPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMysqlPO;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** mcp_protocol_mysql 的领域仓储适配器。 */
@Repository("mysqlProtocolRepository")
public class MysqlProtocolRepository implements IMysqlProtocolRepository {

    @Resource
    private IMcpProtocolMysqlDao protocolDao;

    @Resource
    private IMcpDataSourceDao dataSourceDao;

    @Resource
    private IMcpProtocolMappingDao mappingDao;

    @Override
    public Optional<MysqlTemplate> find(String protocolRef, String version) {
        Long protocolId = parseProtocolId(protocolRef);
        if (protocolId == null) return Optional.empty();
        try {
            McpProtocolMysqlPO protocol = protocolDao.queryByProtocolId(protocolId);
            if (protocol == null) return Optional.empty();
            McpDataSourcePO source = dataSourceDao.queryById(protocol.getDatasourceId());
            if (source == null) return Optional.empty();
            return Optional.of(toDomain(protocol, source, version));
        } catch (DataAccessException e) {
            throw new MysqlDomainException("PROTOCOL_PERSISTENCE_ERROR", "MySQL protocol is unavailable");
        }
    }

    @Override
    @Transactional
    public void save(MysqlTemplate template) {
        if (template == null || template.getId() == null || template.getId().isBlank()) {
            throw new MysqlDomainException("PROTOCOL_INVALID", "MySQL protocol is invalid");
        }
        Long protocolId = parseProtocolId(template.getId());
        if (protocolId == null) throw new MysqlDomainException("PROTOCOL_INVALID", "MySQL protocol is invalid");
        try {
            McpProtocolMysqlPO current = protocolDao.queryByProtocolId(protocolId);
            McpDataSourcePO source = dataSourceDao.queryByDatasourceRef(template.getDatasourceRef());
            if (source == null) {
                throw new MysqlDomainException("DATASOURCE_NOT_FOUND", "data source is not found");
            }
            MysqlQueryPolicy policy = template.getPolicy() == null
                    ? MysqlQueryPolicy.defaults() : template.getPolicy();
            if (current == null) {
                McpProtocolMysqlPO created = toPersistence(template, protocolId, source.getId(), policy);
                created.setStatus(0);
                protocolDao.insert(created);
                replaceMappings(protocolId, template.getParameters());
                return;
            }
            if (Integer.valueOf(1).equals(current.getStatus())
                    && (contentChanged(current, source.getId(), template, policy)
                    || mappingsChanged(protocolId, template.getParameters()))) {
                throw new MysqlDomainException("PROTOCOL_IMMUTABLE", "enabled MySQL protocol content cannot be changed");
            }
            current.setDatasourceId(source.getId());
            current.setSqlText(template.getSql());
            current.setStatus(template.isEnabled() ? 1 : 0);
            applyPolicy(current, policy);
            protocolDao.updateByProtocolId(current);
            replaceMappings(protocolId, template.getParameters());
        } catch (MysqlDomainException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new MysqlDomainException("PROTOCOL_PERSISTENCE_ERROR", "MySQL protocol could not be saved");
        }
    }

    @Override
    public void delete(String protocolRef, String version) {
        Long protocolId = parseProtocolId(protocolRef);
        if (protocolId == null) return;
        try {
            protocolDao.deleteByProtocolId(protocolId);
        } catch (DataAccessException e) {
            throw new MysqlDomainException("PROTOCOL_PERSISTENCE_ERROR", "MySQL protocol could not be deleted");
        }
    }

    private MysqlTemplate toDomain(McpProtocolMysqlPO protocol, McpDataSourcePO source, String version) {
        MysqlQueryPolicy policy = new MysqlQueryPolicy(64 * 1024,
                positive(protocol.getMaxRows(), 1_000),
                positive(protocol.getMaxResultBytes(), 4 * 1024 * 1024L),
                positive(protocol.getMaxColumns(), 128),
                positive(protocol.getTimeoutMs(), 30_000), true);
        List<MysqlTemplateParameter> parameters = new ArrayList<>();
        McpProtocolMappingPO key = McpProtocolMappingPO.builder()
                .protocolType("mysql").protocolId(protocol.getProtocolId()).build();
        List<McpProtocolMappingPO> mappings = mappingDao.queryByProtocolKey(key);
        for (McpProtocolMappingPO mapping : mappings) {
            if (!"request".equalsIgnoreCase(mapping.getMappingType())) continue;
            parameters.add(MysqlTemplateParameter.builder()
                    .name(mapping.getFieldName())
                    .type(parameterType(mapping.getMcpType()))
                    .required(Integer.valueOf(1).equals(mapping.getIsRequired()))
                    .description(mapping.getMcpDesc()).build());
        }
        return MysqlTemplate.builder()
                .id(String.valueOf(protocol.getProtocolId()))
                .version(version == null || version.isBlank() ? "1" : version)
                .name("protocol-" + protocol.getProtocolId())
                .description("Persisted MySQL read-only protocol")
                .datasourceRef(source.getDatasourceRef())
                .sql(protocol.getSqlText())
                .parameters(parameters)
                .status(Integer.valueOf(1).equals(protocol.getStatus())
                        ? MysqlTemplateStatus.ENABLED : MysqlTemplateStatus.DISABLED)
                .policy(policy).build();
    }

    private McpProtocolMysqlPO toPersistence(MysqlTemplate template, Long protocolId, Long datasourceId,
                                             MysqlQueryPolicy policy) {
        McpProtocolMysqlPO po = McpProtocolMysqlPO.builder()
                .protocolId(protocolId)
                .datasourceId(datasourceId)
                .sqlText(template.getSql())
                .build();
        applyPolicy(po, policy);
        return po;
    }

    private void replaceMappings(Long protocolId, List<MysqlTemplateParameter> parameters) {
        McpProtocolMappingPO key = McpProtocolMappingPO.builder()
                .protocolType("mysql")
                .protocolId(protocolId)
                .build();
        mappingDao.deleteByProtocolKey(key);
        int sortOrder = 0;
        for (MysqlTemplateParameter parameter : parameters) {
            mappingDao.insert(McpProtocolMappingPO.builder()
                    .protocolType("mysql")
                    .protocolId(protocolId)
                    .mappingType("request")
                    .fieldName(parameter.getName())
                    .mcpPath(parameter.getName())
                    .mcpType(mappingType(parameter.getType()))
                    .mcpDesc(parameter.getDescription())
                    .isRequired(parameter.isRequired() ? 1 : 0)
                    .sortOrder(++sortOrder)
                    .build());
        }
    }

    private boolean mappingsChanged(Long protocolId, List<MysqlTemplateParameter> parameters) {
        List<McpProtocolMappingPO> current = mappingDao.queryByProtocolKey(McpProtocolMappingPO.builder()
                .protocolType("mysql")
                .protocolId(protocolId)
                .build()).stream()
                .filter(mapping -> "request".equalsIgnoreCase(mapping.getMappingType()))
                .toList();
        if (current.size() != parameters.size()) return true;
        for (int index = 0; index < parameters.size(); index++) {
            MysqlTemplateParameter expected = parameters.get(index);
            McpProtocolMappingPO actual = current.get(index);
            if (!java.util.Objects.equals(actual.getFieldName(), expected.getName())
                    || !java.util.Objects.equals(actual.getMcpPath(), expected.getName())
                    || !java.util.Objects.equals(actual.getMcpType(), mappingType(expected.getType()))
                    || !java.util.Objects.equals(actual.getMcpDesc(), expected.getDescription())
                    || !java.util.Objects.equals(actual.getIsRequired(), expected.isRequired() ? 1 : 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contentChanged(McpProtocolMysqlPO current, Long datasourceId, MysqlTemplate template,
                                          MysqlQueryPolicy policy) {
        return !java.util.Objects.equals(current.getDatasourceId(), datasourceId)
                || !java.util.Objects.equals(current.getSqlText(), template.getSql())
                || !java.util.Objects.equals(current.getMaxRows(), policy.getMaxRows())
                || !java.util.Objects.equals(current.getMaxResultBytes(), policy.getMaxResultBytes())
                || !java.util.Objects.equals(current.getMaxColumns(), policy.getMaxColumns())
                || !java.util.Objects.equals(current.getTimeoutMs(), Math.toIntExact(policy.getTimeoutMs()));
    }

    private static void applyPolicy(McpProtocolMysqlPO po, MysqlQueryPolicy policy) {
        po.setMaxRows(policy.getMaxRows());
        po.setMaxResultBytes(policy.getMaxResultBytes());
        po.setMaxColumns(policy.getMaxColumns());
        po.setTimeoutMs(Math.toIntExact(policy.getTimeoutMs()));
    }

    private static MysqlParameterType parameterType(String type) {
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

    private static String mappingType(MysqlParameterType type) {
        if (type == null) return "string";
        return switch (type) {
            case INTEGER, LONG -> "integer";
            case DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            case DATE, TIME, DATETIME, TIMESTAMP, STRING -> "string";
        };
    }

    private static Long parseProtocolId(String protocolRef) {
        try {
            return protocolRef == null ? null : Long.valueOf(protocolRef);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static long positive(Long value, long fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
