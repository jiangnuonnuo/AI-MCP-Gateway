package cn.bugstack.ai.domain.mysql.service;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlAdminRepository;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceHealthPort;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminPage;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDynamicBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminView;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MySQL 管理面领域编排。
 *
 * <p>跨资源校验、状态机和启用限制集中在此处，Repository 只负责控制库映射和原子持久化。</p>
 */
@Service("mysqlAdminManagementService")
public class MysqlAdminManagementService {

    @Resource
    private IMysqlAdminRepository repository;

    @Resource
    private ISqlSafetyPort safetyPort;

    @Resource
    private IMysqlDataSourceHealthPort healthPort;

    public MysqlAdminPage<MysqlDataSourceAdminView> pageDataSources(MysqlAdminQueries.DataSource query) {
        return repository.pageDataSources(query);
    }

    public MysqlDataSourceAdminView findDataSource(String datasourceRef) {
        return repository.findDataSource(requireText(datasourceRef, "DATASOURCE_INVALID"))
                .orElseThrow(() -> error("DATASOURCE_NOT_FOUND", "data source is not found"));
    }

    public MysqlDataSourceAdminView saveDataSource(MysqlDataSourceAdminCommand command) {
        if (command == null) throw error("DATASOURCE_INVALID", "data source is invalid");
        requireText(command.datasourceRef(), "DATASOURCE_INVALID");
        requireText(command.datasourceName(), "DATASOURCE_INVALID");
        requireText(command.jdbcUrl(), "DATASOURCE_INVALID");
        requireText(command.username(), "DATASOURCE_INVALID");
        if (command.datasourceType() == null || command.datasourceType().isBlank()) {
            throw error("DATASOURCE_INVALID", "data source type is invalid");
        }
        MysqlDataSourceAdminView current = command.id() == null ? null : repository.findDataSource(command.datasourceRef()).orElse(null);
        int status = command.id() == null ? 0 : normalizeStatus(command.status(),
                current == null || current.status() == null ? 0 : current.status(), "DATASOURCE_STATUS_INVALID");
        repository.saveDataSource(new MysqlDataSourceAdminCommand(command.id(), command.datasourceRef(),
                command.datasourceName(), command.datasourceType(), command.jdbcUrl(), command.username(),
                command.password(), command.encryptionKeyRef(), status));
        return findDataSource(command.datasourceRef());
    }

    public MysqlDataSourceAdminView testDataSource(String datasourceRef) {
        MysqlDataSourceAdminView dataSource = findDataSource(datasourceRef);
        if (!healthPort.isHealthy(dataSource.datasourceRef())) {
            throw error("DATASOURCE_CONNECTION_FAILED", "data source connection failed");
        }
        return dataSource;
    }

    public MysqlDataSourceAdminView changeDataSourceStatus(String datasourceRef, int status) {
        int normalized = normalizeStatus(status, 0, "DATASOURCE_STATUS_INVALID");
        findDataSource(datasourceRef);
        repository.changeDataSourceStatus(datasourceRef, normalized);
        return findDataSource(datasourceRef);
    }

    public void deleteDataSource(String datasourceRef) {
        findDataSource(datasourceRef);
        if (repository.countDatasourceReferences(datasourceRef) > 0) {
            throw error("DATASOURCE_IN_USE", "data source is referenced by a template or binding");
        }
        repository.deleteDataSource(datasourceRef);
    }

    public MysqlAdminPage<MysqlTemplateAdminView> pageTemplates(MysqlAdminQueries.Template query) {
        return repository.pageTemplates(query);
    }

    public MysqlTemplateAdminView findTemplate(Long protocolId, String version) {
        if (protocolId == null) throw error("TEMPLATE_INVALID", "template id is invalid");
        return repository.findTemplate(protocolId, version == null || version.isBlank() ? "1" : version)
                .orElseThrow(() -> error("TEMPLATE_NOT_FOUND", "template is not found"));
    }

    public MysqlTemplateAdminView saveTemplate(MysqlTemplateAdminCommand command) {
        validateTemplateCommand(command);
        MysqlTemplateAdminView current = command.protocolId() == null ? null
                : repository.findTemplate(command.protocolId(), command.version()).orElse(null);
        int status = command.protocolId() == null ? 0 : normalizeStatus(command.status(),
                current == null || current.status() == null ? 0 : current.status(), "TEMPLATE_STATUS_INVALID");
        if (current != null && Integer.valueOf(1).equals(current.status()) && coreChanged(current, command)) {
            throw error("ENABLED_TEMPLATE_IMMUTABLE", "enabled template execution fields are immutable");
        }
        MysqlQueryPolicy policy = policy(command);
        MysqlTemplate template = MysqlTemplate.builder()
                .id(command.protocolId() == null ? "new" : String.valueOf(command.protocolId()))
                .version(command.version()).name(command.name()).description(command.description())
                .datasourceRef(command.datasourceRef()).sql(command.sql()).parameters(command.parameters())
                .status(status == 1 ? cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus.ENABLED
                        : cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus.DISABLED)
                .policy(policy).build();
        try {
            template.validate();
            Map<String, Object> parameters = new LinkedHashMap<>();
            command.parameters().forEach(parameter -> {
                parameter.validate();
                if (parameters.put(parameter.getName(), null) != null) {
                    throw error("SQL_PARAMETER_ERROR", "duplicate template parameter");
                }
            });
            SqlSafetyDecision decision = safetyPort.validate(command.sql(), parameters, policy);
            if (decision == null || !decision.isAllowed()) {
                throw error(decision == null ? "SQL_POLICY_REJECTED" : decision.getCode(),
                        decision == null ? "SQL policy rejected" : decision.getReason());
            }
        } catch (MysqlDomainException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw error("TEMPLATE_INVALID", "template configuration is invalid");
        }
        if (status == 1 && !repository.datasourceEnabled(command.datasourceRef())) {
            throw error("DATASOURCE_UNAVAILABLE", "data source must be enabled before template activation");
        }
        Long protocolId = repository.saveTemplate(new MysqlTemplateAdminCommand(command.protocolId(), command.version(), command.name(),
                command.description(), command.datasourceRef(), command.sql(), command.parameters(),
                policy.getMaxRows(), policy.getMaxResultBytes(), policy.getMaxColumns(), Math.toIntExact(policy.getTimeoutMs()), status));
        return findTemplate(protocolId, command.version());
    }

    public MysqlTemplateAdminView changeTemplateStatus(Long protocolId, String version, int status) {
        int normalized = normalizeStatus(status, 0, "TEMPLATE_STATUS_INVALID");
        MysqlTemplateAdminView template = findTemplate(protocolId, version);
        if (normalized == 1 && !repository.datasourceEnabled(template.datasourceRef())) {
            throw error("DATASOURCE_UNAVAILABLE", "data source must be enabled before template activation");
        }
        repository.changeTemplateStatus(protocolId, template.version(), normalized);
        return findTemplate(protocolId, template.version());
    }

    public void deleteTemplate(Long protocolId, String version) {
        MysqlTemplateAdminView template = findTemplate(protocolId, version);
        if (repository.countTemplateBindings(template.protocolId()) > 0) {
            throw error("TEMPLATE_IN_USE", "template is referenced by a gateway tool binding");
        }
        repository.deleteTemplate(template.protocolId(), template.version());
    }

    public MysqlAdminPage<MysqlBindingAdminView> pageBindings(MysqlAdminQueries.Binding query) {
        return repository.pageBindings(query);
    }

    public MysqlBindingAdminView findBinding(Long id) {
        if (id == null) throw error("BINDING_INVALID", "binding id is invalid");
        return repository.findBinding(id).orElseThrow(() -> error("BINDING_NOT_FOUND", "binding is not found"));
    }

    public MysqlBindingAdminView saveBinding(MysqlBindingAdminCommand command) {
        if (command == null || command.gatewayId() == null || command.gatewayId().isBlank()
                || command.toolName() == null || command.toolName().isBlank() || command.protocolId() == null) {
            throw error("BINDING_INVALID", "binding is invalid");
        }
        if (!"mysql".equalsIgnoreCase(command.protocolType())) {
            throw error("BINDING_PROTOCOL_INVALID", "binding protocol type must be mysql");
        }
        if (!repository.gatewayExists(command.gatewayId())) throw error("GATEWAY_NOT_FOUND", "gateway is not found");
        if (!repository.templateExists(command.protocolId())) {
            throw error("TEMPLATE_NOT_FOUND", "MySQL template is not available");
        }
        if (repository.bindingNameExists(command.gatewayId(), command.toolName(), command.id())) {
            throw error("TOOL_NAME_CONFLICT", "tool name already exists in gateway");
        }
        int status = command.id() == null ? 0 : normalizeStatus(command.status(), 0, "BINDING_STATUS_INVALID");
        if (status == 1 && (!repository.templateEnabled(command.protocolId())
                || !repository.templateDatasourceEnabled(command.protocolId()))) {
            throw error("BINDING_RESOURCE_UNAVAILABLE", "enabled binding requires an enabled template");
        }
        Long bindingId = repository.saveBinding(new MysqlBindingAdminCommand(command.id(), command.gatewayId(), command.toolId(),
                command.toolName(), command.toolType(), command.toolDescription(), command.toolVersion(),
                command.protocolId(), "mysql", status));
        return findBinding(bindingId);
    }

    /**
     * 动态 SQL 绑定不依赖模板；网关只保存固定数据源和资源护栏，调用时再由 Tool 参数提供 SQL。
     */
    public MysqlBindingAdminView saveDynamicBinding(MysqlDynamicBindingAdminCommand command) {
        if (command == null || command.gatewayId() == null || command.gatewayId().isBlank()
                || command.toolName() == null || command.toolName().isBlank()
                || command.datasourceRef() == null || command.datasourceRef().isBlank()) {
            throw error("BINDING_INVALID", "dynamic binding is invalid");
        }
        if (!repository.gatewayExists(command.gatewayId())) throw error("GATEWAY_NOT_FOUND", "gateway is not found");
        if (repository.bindingNameExists(command.gatewayId(), command.toolName(), command.id())) {
            throw error("TOOL_NAME_CONFLICT", "tool name already exists in gateway");
        }
        MysqlBindingAdminView current = command.id() == null ? null : findBinding(command.id());
        if (current != null && !"DYNAMIC_READONLY".equalsIgnoreCase(current.executionMode())) {
            throw error("PROTOCOL_MODE_MISMATCH", "template binding must be edited from the template binding path");
        }
        MysqlQueryPolicy policy = dynamicPolicy(command);
        if (current != null && Integer.valueOf(1).equals(current.status()) && dynamicCoreChanged(current, command, policy)) {
            throw error("ENABLED_DYNAMIC_IMMUTABLE", "enabled dynamic binding execution fields are immutable");
        }
        int status = normalizeStatus(command.status(), current == null || current.status() == null ? 0 : current.status(),
                "BINDING_STATUS_INVALID");
        if (status == 1 && !repository.datasourceEnabled(command.datasourceRef())) {
            throw error("DATASOURCE_UNAVAILABLE", "data source must be enabled before dynamic binding activation");
        }
        Long bindingId = repository.saveDynamicBinding(new MysqlDynamicBindingAdminCommand(command.id(), command.gatewayId(), command.toolId(),
                command.toolName(), command.toolType(), command.toolDescription(), command.toolVersion(), command.datasourceRef(),
                policy.getMaxRows(), policy.getMaxResultBytes(), policy.getMaxColumns(), Math.toIntExact(policy.getTimeoutMs()), status));
        return findBinding(bindingId);
    }

    public MysqlBindingAdminView changeBindingStatus(Long id, int status) {
        int normalized = normalizeStatus(status, 0, "BINDING_STATUS_INVALID");
        MysqlBindingAdminView binding = findBinding(id);
        if (normalized == 1) {
            if ("DYNAMIC_READONLY".equalsIgnoreCase(binding.executionMode())) {
                if (binding.datasourceRef() == null || !repository.datasourceEnabled(binding.datasourceRef())) {
                    throw error("BINDING_RESOURCE_UNAVAILABLE", "enabled dynamic binding requires an enabled data source");
                }
            } else if (!repository.templateEnabled(binding.protocolId()) || !repository.templateDatasourceEnabled(binding.protocolId())) {
                throw error("BINDING_RESOURCE_UNAVAILABLE", "enabled binding requires an enabled template");
            }
        }
        repository.changeBindingStatus(id, normalized);
        return findBinding(id);
    }

    public void deleteBinding(Long id) {
        findBinding(id);
        repository.deleteBinding(id);
    }

    private static void validateTemplateCommand(MysqlTemplateAdminCommand command) {
        if (command == null || command.version() == null || command.version().isBlank()
                || command.name() == null || command.name().isBlank() || command.datasourceRef() == null
                || command.datasourceRef().isBlank() || command.sql() == null || command.sql().isBlank()) {
            throw error("TEMPLATE_INVALID", "template is invalid");
        }
        if (command.parameters() == null) throw error("SQL_PARAMETER_ERROR", "template parameters are invalid");
    }

    private static boolean coreChanged(MysqlTemplateAdminView current, MysqlTemplateAdminCommand command) {
        MysqlQueryPolicy defaults = MysqlQueryPolicy.defaults();
        int maxRows = command.maxRows() == null ? defaults.getMaxRows() : command.maxRows();
        long maxBytes = command.maxResultBytes() == null ? defaults.getMaxResultBytes() : command.maxResultBytes();
        int maxColumns = command.maxColumns() == null ? defaults.getMaxColumns() : command.maxColumns();
        int timeoutMs = command.timeoutMs() == null ? Math.toIntExact(defaults.getTimeoutMs()) : command.timeoutMs();
        return !java.util.Objects.equals(current.datasourceRef(), command.datasourceRef())
                || !java.util.Objects.equals(current.sql(), command.sql())
                || !java.util.Objects.equals(current.parameters(), command.parameters())
                || !java.util.Objects.equals(current.maxRows(), maxRows)
                || !java.util.Objects.equals(current.maxResultBytes(), maxBytes)
                || !java.util.Objects.equals(current.maxColumns(), maxColumns)
                || !java.util.Objects.equals(current.timeoutMs(), timeoutMs);
    }

    private static MysqlQueryPolicy policy(MysqlTemplateAdminCommand command) {
        MysqlQueryPolicy defaults = MysqlQueryPolicy.defaults();
        return new MysqlQueryPolicy(defaults.getMaxSqlLength(),
                command.maxRows() == null ? defaults.getMaxRows() : command.maxRows(),
                command.maxResultBytes() == null ? defaults.getMaxResultBytes() : command.maxResultBytes(),
                command.maxColumns() == null ? defaults.getMaxColumns() : command.maxColumns(),
                command.timeoutMs() == null ? defaults.getTimeoutMs() : command.timeoutMs(), true);
    }

    private static MysqlQueryPolicy dynamicPolicy(MysqlDynamicBindingAdminCommand command) {
        MysqlQueryPolicy defaults = MysqlQueryPolicy.defaults();
        MysqlQueryPolicy policy = new MysqlQueryPolicy(defaults.getMaxSqlLength(),
                command.maxRows() == null ? defaults.getMaxRows() : command.maxRows(),
                command.maxResultBytes() == null ? defaults.getMaxResultBytes() : command.maxResultBytes(),
                command.maxColumns() == null ? defaults.getMaxColumns() : command.maxColumns(),
                command.timeoutMs() == null ? defaults.getTimeoutMs() : command.timeoutMs(), true);
        try { policy.validate(); } catch (IllegalArgumentException e) { throw error("BINDING_INVALID", "dynamic query policy is invalid"); }
        return policy;
    }

    private static boolean dynamicCoreChanged(MysqlBindingAdminView current, MysqlDynamicBindingAdminCommand command,
                                               MysqlQueryPolicy policy) {
        return !java.util.Objects.equals(current.datasourceRef(), command.datasourceRef())
                || !java.util.Objects.equals(current.maxRows(), policy.getMaxRows())
                || !java.util.Objects.equals(current.maxResultBytes(), policy.getMaxResultBytes())
                || !java.util.Objects.equals(current.maxColumns(), policy.getMaxColumns())
                || !java.util.Objects.equals(current.timeoutMs(), Math.toIntExact(policy.getTimeoutMs()));
    }

    private static int normalizeStatus(Integer value, int fallback, String code) {
        int status = value == null ? fallback : value;
        if (status != 0 && status != 1) throw error(code, "status must be 0 or 1");
        return status;
    }

    private static String requireText(String value, String code) {
        if (value == null || value.isBlank()) throw error(code, "required value is missing");
        return value;
    }

    private static MysqlDomainException error(String code, String message) {
        return new MysqlDomainException(code, message == null || message.isBlank() ? "request rejected" : message);
    }
}
