package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlAdminRepository;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminPage;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDynamicBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminView;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.infrastructure.dao.IMcpDataSourceDao;
import cn.bugstack.ai.infrastructure.dao.IMcpGatewayDao;
import cn.bugstack.ai.infrastructure.dao.IMcpGatewayToolDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMappingDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMysqlDao;
import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import cn.bugstack.ai.infrastructure.dao.po.McpGatewayToolPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMappingPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMysqlPO;
import cn.bugstack.ai.infrastructure.security.DataSourceCredentialCipher;
import cn.bugstack.ai.types.security.SensitiveDataSanitizer;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 管理面控制库适配器。
 *
 * <p>管理响应只由 PO 的非敏感字段组装；凭证只在写入时经过 Cipher，查询路径永不解密。</p>
 */
@Repository("mysqlAdminRepository")
public class MysqlAdminRepository implements IMysqlAdminRepository {

    @Resource private IMcpDataSourceDao dataSourceDao;
    @Resource private IMcpProtocolMysqlDao protocolDao;
    @Resource private IMcpProtocolMappingDao mappingDao;
    @Resource private IMcpGatewayToolDao toolDao;
    @Resource private IMcpGatewayDao gatewayDao;
    @Resource private DataSourceCredentialCipher credentialCipher;

    @Override
    public MysqlAdminPage<MysqlDataSourceAdminView> pageDataSources(MysqlAdminQueries.DataSource query) {
        try {
            List<McpDataSourcePO> records = dataSourceDao.queryAll();
            Predicate<McpDataSourcePO> filter = source -> contains(source.getDatasourceRef(), query.datasourceRef())
                    && contains(source.getDatasourceName(), query.datasourceName())
                    && (query.status() == null || Objects.equals(source.getStatus(), query.status()));
            List<MysqlDataSourceAdminView> views = records.stream().filter(filter)
                    .sorted(Comparator.comparing(McpDataSourcePO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(this::toDataSourceView).toList();
            return page(views, query.page(), query.rows());
        } catch (DataAccessException e) {
            throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source configuration is unavailable", e);
        }
    }

    @Override
    public Optional<MysqlDataSourceAdminView> findDataSource(String datasourceRef) {
        try {
            return Optional.ofNullable(dataSourceDao.queryByDatasourceRef(datasourceRef)).map(this::toDataSourceView);
        } catch (DataAccessException e) {
            throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source configuration is unavailable", e);
        }
    }

    @Override
    @Transactional
    public void saveDataSource(MysqlDataSourceAdminCommand command) {
        try {
            if (SensitiveDataSanitizer.containsJdbcCredentials(command.jdbcUrl())) {
                throw new MysqlDomainException("DATASOURCE_JDBC_URL_SENSITIVE", "JDBC URL must not contain credentials");
            }
            McpDataSourcePO current = dataSourceDao.queryByDatasourceRef(command.datasourceRef());
            String keyRef = command.encryptionKeyRef();
            if (current == null) {
                if (command.password() == null || command.password().isBlank() || keyRef == null || keyRef.isBlank()) {
                    throw new MysqlDomainException("DATASOURCE_CREDENTIAL_REQUIRED", "new data source requires a password and key reference");
                }
                DataSourceCredentialCipher.EncryptedValue encrypted = credentialCipher.encrypt(command.password(), keyRef);
                McpDataSourcePO created = McpDataSourcePO.builder().datasourceRef(command.datasourceRef())
                        .datasourceName(command.datasourceName()).datasourceType(command.datasourceType())
                        .jdbcUrl(command.jdbcUrl()).username(command.username())
                        .passwordCiphertext(encrypted.ciphertext()).passwordNonce(encrypted.nonce())
                        .encryptionKeyRef(keyRef).status(0).build();
                dataSourceDao.insert(created);
                return;
            }
            if (keyRef == null || keyRef.isBlank()) keyRef = current.getEncryptionKeyRef();
            current.setDatasourceRef(command.datasourceRef());
            current.setDatasourceName(command.datasourceName());
            current.setDatasourceType(command.datasourceType());
            current.setJdbcUrl(command.jdbcUrl());
            current.setUsername(command.username());
            current.setStatus(command.status() == null ? current.getStatus() : command.status());
            current.setEncryptionKeyRef(keyRef);
            if (command.password() != null && !command.password().isBlank()) {
                DataSourceCredentialCipher.EncryptedValue encrypted = credentialCipher.encrypt(command.password(), keyRef);
                current.setPasswordCiphertext(encrypted.ciphertext());
                current.setPasswordNonce(encrypted.nonce());
            }
            dataSourceDao.updateById(current);
        } catch (MysqlDomainException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new MysqlDomainException("DATASOURCE_CREDENTIAL_KEY_UNAVAILABLE",
                    "data source credential key is unavailable");
        } catch (DataAccessException e) {
            throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source configuration could not be saved", e);
        }
    }

    @Override
    @Transactional
    public void changeDataSourceStatus(String datasourceRef, int status) {
        try {
            if (dataSourceDao.updateStatusByDatasourceRef(datasourceRef, status) == 0) {
                throw new MysqlDomainException("DATASOURCE_NOT_FOUND", "data source is not found");
            }
        } catch (MysqlDomainException e) { throw e; }
        catch (DataAccessException e) { throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source status could not be changed", e); }
    }

    @Override
    public void deleteDataSource(String datasourceRef) {
        try { dataSourceDao.deleteByDatasourceRef(datasourceRef); }
        catch (DataAccessException e) { throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source could not be deleted", e); }
    }

    @Override
    public long countDatasourceReferences(String datasourceRef) {
        try {
            McpDataSourcePO source = dataSourceDao.queryByDatasourceRef(datasourceRef);
            if (source == null) return 0;
            return protocolDao.queryListByDatasourceId(source.getId()).size();
        } catch (DataAccessException e) { throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source references are unavailable", e); }
    }

    @Override
    public MysqlAdminPage<MysqlTemplateAdminView> pageTemplates(MysqlAdminQueries.Template query) {
        try {
            List<MysqlTemplateAdminView> views = protocolDao.queryAll().stream()
                    .filter(MysqlAdminRepository::isTemplateMode)
                    .map(this::toTemplateView)
                    .filter(view -> (query.protocolId() == null || Objects.equals(view.protocolId(), query.protocolId()))
                            && contains(view.name(), query.name()) && contains(view.datasourceRef(), query.datasourceRef())
                            && (query.status() == null || Objects.equals(view.status(), query.status())))
                    .sorted(Comparator.comparing(MysqlTemplateAdminView::protocolId, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            return page(views, query.page(), query.rows());
        } catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "MySQL templates are unavailable", e); }
    }

    @Override
    public Optional<MysqlTemplateAdminView> findTemplate(Long protocolId, String version) {
        try {
            McpProtocolMysqlPO po = protocolDao.queryByProtocolId(protocolId);
            return Optional.ofNullable(po).filter(MysqlAdminRepository::isTemplateMode).map(this::toTemplateView);
        } catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "MySQL template is unavailable", e); }
    }

    @Override
    @Transactional
    public Long saveTemplate(MysqlTemplateAdminCommand command) {
        try {
            Long protocolId = command.protocolId() == null ? nextProtocolId() : command.protocolId();
            McpDataSourcePO source = dataSourceDao.queryByDatasourceRef(command.datasourceRef());
            if (source == null) throw new MysqlDomainException("DATASOURCE_NOT_FOUND", "data source is not found");
            McpProtocolMysqlPO current = protocolDao.queryByProtocolId(protocolId);
            if (current != null && !isTemplateMode(current)) {
                throw new MysqlDomainException("PROTOCOL_MODE_MISMATCH",
                        "Dynamic MySQL protocol must be managed as a dynamic Tool binding");
            }
            McpProtocolMysqlPO po = current == null ? new McpProtocolMysqlPO() : current;
            po.setProtocolId(protocolId);
            po.setExecutionMode("TEMPLATE");
            po.setDatasourceId(source.getId());
            po.setSqlText(command.sql());
            po.setMaxRows(command.maxRows());
            po.setMaxResultBytes(command.maxResultBytes());
            po.setMaxColumns(command.maxColumns());
            po.setTimeoutMs(command.timeoutMs());
            po.setStatus(command.status() == null ? 0 : command.status());
            if (current == null) protocolDao.insert(po); else protocolDao.updateByProtocolId(po);
            mappingDao.deleteByProtocolKey(McpProtocolMappingPO.builder().protocolType("mysql").protocolId(protocolId).build());
            int order = 0;
            for (MysqlTemplateParameter parameter : command.parameters()) {
                mappingDao.insert(McpProtocolMappingPO.builder().protocolType("mysql").protocolId(protocolId)
                        .mappingType("request").fieldName(parameter.getName()).mcpPath(parameter.getName())
                        .mcpType(mappingType(parameter.getType())).mcpDesc(parameter.getDescription())
                        .isRequired(parameter.isRequired() ? 1 : 0).sortOrder(++order).build());
            }
            return protocolId;
        } catch (MysqlDomainException e) { throw e; }
        catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "MySQL template could not be saved", e); }
    }

    @Override
    @Transactional
    public void changeTemplateStatus(Long protocolId, String version, int status) {
        try {
            if (protocolDao.updateStatusByProtocolId(protocolId, status) == 0) {
                throw new MysqlDomainException("TEMPLATE_NOT_FOUND", "template is not found");
            }
        } catch (MysqlDomainException e) { throw e; }
        catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "template status could not be changed", e); }
    }

    @Override
    @Transactional
    public void deleteTemplate(Long protocolId, String version) {
        try {
            protocolDao.deleteByProtocolId(protocolId);
            mappingDao.deleteByProtocolKey(McpProtocolMappingPO.builder().protocolType("mysql").protocolId(protocolId).build());
        } catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "MySQL template could not be deleted", e); }
    }

    @Override
    public long countTemplateBindings(Long protocolId) {
        try { return toolDao.queryAll().stream().filter(t -> Objects.equals(t.getProtocolId(), protocolId)
                && "mysql".equalsIgnoreCase(t.getProtocolType())).count(); }
        catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "template bindings are unavailable", e); }
    }

    @Override
    public MysqlAdminPage<MysqlBindingAdminView> pageBindings(MysqlAdminQueries.Binding query) {
        try {
            List<MysqlBindingAdminView> views = toolDao.queryAll().stream().filter(t -> "mysql".equalsIgnoreCase(t.getProtocolType()))
                    .map(this::toBindingView)
                    .filter(view -> contains(view.gatewayId(), query.gatewayId()) && contains(view.toolName(), query.toolName())
                            && (query.protocolId() == null || Objects.equals(view.protocolId(), query.protocolId()))
                            && (query.status() == null || Objects.equals(view.status(), query.status())))
                    .sorted(Comparator.comparing(MysqlBindingAdminView::id, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            return page(views, query.page(), query.rows());
        } catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "MySQL bindings are unavailable", e); }
    }

    @Override
    public Optional<MysqlBindingAdminView> findBinding(Long id) {
        try { return toolDao.queryAll().stream().filter(t -> Objects.equals(t.getId(), id)
                && "mysql".equalsIgnoreCase(t.getProtocolType())).findFirst().map(this::toBindingView); }
        catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "binding is unavailable", e); }
    }

    @Override
    @Transactional
    public Long saveBinding(MysqlBindingAdminCommand command) {
        try {
            McpGatewayToolPO current = command.id() == null ? null : toolDao.queryAll().stream()
                    .filter(t -> Objects.equals(t.getId(), command.id())).findFirst().orElse(null);
            Long toolId = command.toolId() != null ? command.toolId() : current != null ? current.getToolId() : nextToolId();
            if (current != null && !Objects.equals(current.getToolId(), toolId)) {
                toolDao.deleteById(current.getId());
            }
            McpGatewayToolPO po = McpGatewayToolPO.builder().id(current == null ? null : current.getId())
                    .gatewayId(command.gatewayId()).toolId(toolId).toolName(command.toolName()).toolType(command.toolType())
                    .toolDescription(command.toolDescription() == null ? "" : command.toolDescription())
                    .toolVersion(command.toolVersion()).protocolId(command.protocolId()).protocolType("mysql")
                    .status(command.status() == null ? 0 : command.status()).build();
            toolDao.insert(po);
            return toolDao.queryAll().stream().filter(t -> Objects.equals(t.getGatewayId(), command.gatewayId())
                    && Objects.equals(t.getToolId(), toolId)).map(McpGatewayToolPO::getId).findFirst().orElse(po.getId());
        } catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "binding could not be saved", e); }
    }

    @Override
    @Transactional
    public Long saveDynamicBinding(MysqlDynamicBindingAdminCommand command) {
        try {
            McpDataSourcePO source = dataSourceDao.queryByDatasourceRef(command.datasourceRef());
            if (source == null) throw new MysqlDomainException("DATASOURCE_NOT_FOUND", "data source is not found");
            McpGatewayToolPO currentTool = command.id() == null ? null : toolDao.queryAll().stream()
                    .filter(t -> Objects.equals(t.getId(), command.id())).findFirst().orElse(null);
            Long protocolId = currentTool == null ? nextProtocolId() : currentTool.getProtocolId();
            McpProtocolMysqlPO currentProtocol = protocolDao.queryByProtocolId(protocolId);
            if (currentProtocol != null && !isDynamicMode(currentProtocol)) {
                throw new MysqlDomainException("PROTOCOL_MODE_MISMATCH", "Template MySQL protocol cannot be used by a dynamic binding");
            }
            McpProtocolMysqlPO protocol = currentProtocol == null ? new McpProtocolMysqlPO() : currentProtocol;
            protocol.setProtocolId(protocolId);
            protocol.setExecutionMode("DYNAMIC_READONLY");
            protocol.setDatasourceId(source.getId());
            protocol.setSqlText(null);
            protocol.setMaxRows(command.maxRows());
            protocol.setMaxResultBytes(command.maxResultBytes());
            protocol.setMaxColumns(command.maxColumns());
            protocol.setTimeoutMs(command.timeoutMs());
            protocol.setStatus(command.status() == null ? 0 : command.status());
            if (currentProtocol == null) protocolDao.insert(protocol); else protocolDao.updateByProtocolId(protocol);
            mappingDao.deleteByProtocolKey(McpProtocolMappingPO.builder().protocolType("mysql").protocolId(protocolId).build());

            Long toolId = command.toolId() != null ? command.toolId() : currentTool != null ? currentTool.getToolId() : nextToolId();
            McpGatewayToolPO tool = McpGatewayToolPO.builder().id(currentTool == null ? null : currentTool.getId())
                    .gatewayId(command.gatewayId()).toolId(toolId).toolName(command.toolName()).toolType(command.toolType())
                    .toolDescription(command.toolDescription() == null ? "" : command.toolDescription())
                    .toolVersion(command.toolVersion()).protocolId(protocolId).protocolType("mysql")
                    .status(command.status() == null ? 0 : command.status()).build();
            toolDao.insert(tool);
            return toolDao.queryAll().stream().filter(t -> Objects.equals(t.getGatewayId(), command.gatewayId())
                    && Objects.equals(t.getToolId(), toolId)).map(McpGatewayToolPO::getId).findFirst().orElse(tool.getId());
        } catch (MysqlDomainException e) { throw e;
        } catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "dynamic binding could not be saved", e); }
    }

    @Override
    @Transactional
    public void changeBindingStatus(Long id, int status) {
        try {
            McpGatewayToolPO current = toolDao.queryAll().stream().filter(t -> Objects.equals(t.getId(), id)).findFirst().orElse(null);
            if (current == null || toolDao.updateStatusById(McpGatewayToolPO.builder().id(id).status(status).build()) == 0) {
                throw new MysqlDomainException("BINDING_NOT_FOUND", "binding is not found");
            }
            if (isDynamicMode(protocolDao.queryByProtocolId(current.getProtocolId()))) {
                protocolDao.updateStatusByProtocolId(current.getProtocolId(), status);
            }
        } catch (MysqlDomainException e) { throw e; }
        catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "binding status could not be changed", e); }
    }

    @Override
    public void deleteBinding(Long id) {
        try {
            McpGatewayToolPO current = toolDao.queryAll().stream().filter(t -> Objects.equals(t.getId(), id)).findFirst().orElse(null);
            toolDao.deleteById(id);
            if (current != null && isDynamicMode(protocolDao.queryByProtocolId(current.getProtocolId()))
                    && toolDao.queryAll().stream().noneMatch(t -> Objects.equals(t.getProtocolId(), current.getProtocolId()))) {
                protocolDao.deleteByProtocolId(current.getProtocolId());
                mappingDao.deleteByProtocolKey(McpProtocolMappingPO.builder().protocolType("mysql").protocolId(current.getProtocolId()).build());
            }
        }
        catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "binding could not be deleted", e); }
    }

    @Override
    public boolean bindingNameExists(String gatewayId, String toolName, Long excludingId) {
        try { return toolDao.queryAll().stream().anyMatch(t -> "mysql".equalsIgnoreCase(t.getProtocolType())
                && Objects.equals(t.getGatewayId(), gatewayId) && Objects.equals(t.getToolName(), toolName)
                && !Objects.equals(t.getId(), excludingId)); }
        catch (DataAccessException e) { throw persistence("BINDING_PERSISTENCE_ERROR", "binding uniqueness is unavailable", e); }
    }

    @Override public boolean gatewayExists(String gatewayId) {
        try { return gatewayDao.queryMcpGatewayByGatewayId(gatewayId) != null; }
        catch (DataAccessException e) { throw persistence("GATEWAY_PERSISTENCE_ERROR", "gateway is unavailable", e); }
    }

    @Override public boolean templateExists(Long protocolId) {
        try { return isTemplateMode(protocolDao.queryByProtocolId(protocolId)); }
        catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "template is unavailable", e); }
    }

    @Override public boolean templateEnabled(Long protocolId) {
        try { McpProtocolMysqlPO po = protocolDao.queryEnabledByProtocolId(protocolId); return isTemplateMode(po); }
        catch (DataAccessException e) { throw persistence("PROTOCOL_PERSISTENCE_ERROR", "template status is unavailable", e); }
    }

    @Override public boolean templateDatasourceEnabled(Long protocolId) {
        try {
            McpProtocolMysqlPO template = protocolDao.queryByProtocolId(protocolId);
            if (!isTemplateMode(template)) return false;
            McpDataSourcePO source = dataSourceDao.queryById(template.getDatasourceId());
            return source != null && Integer.valueOf(1).equals(source.getStatus());
        } catch (DataAccessException e) { throw persistence("DATASOURCE_PERSISTENCE_ERROR", "template data source status is unavailable", e); }
    }

    @Override public boolean datasourceEnabled(String datasourceRef) {
        try {
            McpDataSourcePO source = dataSourceDao.queryByDatasourceRef(datasourceRef);
            return source != null && Integer.valueOf(1).equals(source.getStatus());
        } catch (DataAccessException e) { throw persistence("DATASOURCE_PERSISTENCE_ERROR", "data source status is unavailable", e); }
    }

    private MysqlDataSourceAdminView toDataSourceView(McpDataSourcePO po) {
        return new MysqlDataSourceAdminView(po.getId(), po.getDatasourceRef(), po.getDatasourceName(), po.getDatasourceType(),
                SensitiveDataSanitizer.maskJdbcUrl(po.getJdbcUrl()), po.getUsername(), po.getStatus(),
                po.getPasswordCiphertext() != null && !po.getPasswordCiphertext().isBlank(), po.getCreateTime(), po.getUpdateTime());
    }

    private MysqlTemplateAdminView toTemplateView(McpProtocolMysqlPO po) {
        McpDataSourcePO source = dataSourceDao.queryById(po.getDatasourceId());
        List<MysqlTemplateParameter> parameters = new ArrayList<>();
        List<McpProtocolMappingPO> mappings = mappingDao.queryByProtocolKey(McpProtocolMappingPO.builder()
                .protocolType("mysql").protocolId(po.getProtocolId()).build());
        for (McpProtocolMappingPO mapping : mappings) if ("request".equalsIgnoreCase(mapping.getMappingType())) {
            parameters.add(new MysqlTemplateParameter(mapping.getFieldName(), parameterType(mapping.getMcpType()),
                    Integer.valueOf(1).equals(mapping.getIsRequired()), mapping.getMcpDesc()));
        }
        return new MysqlTemplateAdminView(po.getProtocolId(), "1", "protocol-" + po.getProtocolId(),
                "Persisted MySQL read-only protocol", source == null ? null : source.getDatasourceRef(), po.getSqlText(),
                parameters, po.getMaxRows(), po.getMaxResultBytes(), po.getMaxColumns(), po.getTimeoutMs(), po.getStatus(),
                po.getCreateTime(), po.getUpdateTime());
    }

    private MysqlBindingAdminView toBindingView(McpGatewayToolPO po) {
        McpProtocolMysqlPO protocol = protocolDao.queryByProtocolId(po.getProtocolId());
        McpDataSourcePO source = protocol == null ? null : dataSourceDao.queryById(protocol.getDatasourceId());
        return new MysqlBindingAdminView(po.getId(), po.getGatewayId(), po.getToolId(), po.getToolName(), po.getToolType(),
                po.getToolDescription(), po.getToolVersion(), po.getProtocolId(), po.getProtocolType(), po.getStatus(),
                po.getCreateTime(), po.getUpdateTime(), protocol == null ? "UNKNOWN" : protocol.getExecutionMode(),
                source == null ? null : source.getDatasourceRef(), protocol == null ? null : protocol.getMaxRows(),
                protocol == null ? null : protocol.getMaxResultBytes(), protocol == null ? null : protocol.getMaxColumns(),
                protocol == null ? null : protocol.getTimeoutMs());
    }

    private static <T> MysqlAdminPage<T> page(List<T> values, int page, int rows) {
        int normalizedPage = page < 1 ? 1 : page;
        int normalizedRows = rows < 1 ? 20 : Math.min(rows, 200);
        int from = Math.min((normalizedPage - 1) * normalizedRows, values.size());
        int to = Math.min(from + normalizedRows, values.size());
        return new MysqlAdminPage<>(values.subList(from, to), values.size(), normalizedPage, normalizedRows);
    }

    private static boolean contains(String value, String expected) {
        return expected == null || expected.isBlank() || value != null && value.toLowerCase().contains(expected.toLowerCase());
    }

    private Long nextProtocolId() {
        return protocolDao.queryAll().stream().map(McpProtocolMysqlPO::getProtocolId).filter(Objects::nonNull)
                .max(Long::compareTo).orElse(0L) + 1;
    }

    private Long nextToolId() {
        return toolDao.queryAll().stream().map(McpGatewayToolPO::getToolId).filter(Objects::nonNull)
                .max(Long::compareTo).orElse(0L) + 1;
    }

    private static boolean isDynamicMode(McpProtocolMysqlPO po) {
        return po != null && "DYNAMIC_READONLY".equalsIgnoreCase(po.getExecutionMode());
    }

    private static String mappingType(MysqlParameterType type) {
        if (type == null) return "string";
        return switch (type) {
            case INTEGER, LONG -> "integer";
            case DECIMAL -> "number";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    private static boolean isTemplateMode(McpProtocolMysqlPO protocol) {
        if (protocol == null) return false;
        String executionMode = protocol.getExecutionMode();
        return executionMode == null || executionMode.isBlank()
                || ToolExecutionMode.from(executionMode) == ToolExecutionMode.MYSQL_TEMPLATE;
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

    private static MysqlDomainException persistence(String code, String message, Throwable cause) {
        return new MysqlDomainException(code, message);
    }
}
