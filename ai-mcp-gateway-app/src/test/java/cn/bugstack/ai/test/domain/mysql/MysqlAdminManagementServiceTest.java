package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlAdminRepository;
import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlDataSourceHealthPort;
import cn.bugstack.ai.domain.mysql.adapter.port.ISqlSafetyPort;
import cn.bugstack.ai.domain.mysql.model.admin.*;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.SqlSafetyDecision;
import cn.bugstack.ai.domain.mysql.service.MysqlAdminManagementService;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MysqlAdminManagementServiceTest {

    @Test
    void newDataSourceIsDisabledAndReferencedDataSourceCannotBeDeleted() {
        FakeRepository repository = new FakeRepository();
        MysqlAdminManagementService service = service(repository, (sql, params, policy) -> SqlSafetyDecision.allowed());

        MysqlDataSourceAdminView created = service.saveDataSource(new MysqlDataSourceAdminCommand(null, "warehouse", "Warehouse", "mysql",
                "jdbc:mysql://db/warehouse", "reader", "secret", "key-v1", 1));

        assertEquals(0, created.status());
        repository.datasourceReferences = 1;
        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.deleteDataSource("warehouse"));
        assertEquals("DATASOURCE_IN_USE", error.getCode());
    }

    @Test
    void enabledTemplateRejectsExecutionFieldChanges() {
        FakeRepository repository = new FakeRepository();
        repository.template = new MysqlTemplateAdminView(7L, "1", "orders", "display", "warehouse", "SELECT 1",
                List.of(), 1000, 4_194_304L, 128, 30_000, 1, null, null);
        MysqlAdminManagementService service = service(repository, (sql, params, policy) -> SqlSafetyDecision.allowed());

        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.saveTemplate(new MysqlTemplateAdminCommand(
                7L, "1", "orders renamed", "display", "warehouse", "SELECT 2", List.of(), 1000, 4_194_304L, 128, 30_000, 1)));

        assertEquals("ENABLED_TEMPLATE_IMMUTABLE", error.getCode());
    }

    @Test
    void bindingNameMustBeUniqueWithinGateway() {
        FakeRepository repository = new FakeRepository();
        repository.gatewayExists = true;
        repository.templateExists = true;
        repository.templateEnabled = true;
        repository.bindingNameExists = true;
        MysqlAdminManagementService service = service(repository, (sql, params, policy) -> SqlSafetyDecision.allowed());

        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.saveBinding(new MysqlBindingAdminCommand(
                null, "gateway-1", null, "orders", "function", "orders", "1.0.0", 7L, "mysql", 1)));

        assertEquals("TOOL_NAME_CONFLICT", error.getCode());
    }

    @Test
    void dynamicBindingRequiresDatasourceButDoesNotRequireTemplate() {
        FakeRepository repository = new FakeRepository();
        repository.gatewayExists = true;
        MysqlAdminManagementService service = service(repository, (sql, params, policy) -> SqlSafetyDecision.allowed());

        MysqlBindingAdminView created = service.saveDynamicBinding(new MysqlDynamicBindingAdminCommand(
                null, "gateway-1", null, "dynamicQuery", "function", "Dynamic query", "1.0.0",
                "warehouse", 100, 1024L, 16, 3000, 1));

        assertEquals("DYNAMIC_READONLY", created.executionMode());
        assertEquals("warehouse", created.datasourceRef());
        assertFalse(repository.templateExists);
    }

    @Test
    void datasourceConnectionTestUsesHealthPortAndReturnsStableFailure() {
        FakeRepository repository = new FakeRepository();
        repository.dataSource = new MysqlDataSourceAdminView(1L, "warehouse", "Warehouse", "mysql",
                "jdbc:mysql://127.0.0.1:3306/warehouse", "reader", 0, true, null, null);
        AtomicReference<String> testedRef = new AtomicReference<>();
        MysqlAdminManagementService service = service(repository, (sql, params, policy) -> SqlSafetyDecision.allowed(),
                datasourceRef -> {
                    testedRef.set(datasourceRef);
                    return false;
                });

        MysqlDomainException error = assertThrows(MysqlDomainException.class,
                () -> service.testDataSource("warehouse"));

        assertEquals("warehouse", testedRef.get());
        assertEquals("DATASOURCE_CONNECTION_FAILED", error.getCode());
    }

    private static MysqlAdminManagementService service(FakeRepository repository, ISqlSafetyPort safetyPort) {
        return service(repository, safetyPort, datasourceRef -> true);
    }

    private static MysqlAdminManagementService service(FakeRepository repository, ISqlSafetyPort safetyPort,
                                                       IMysqlDataSourceHealthPort healthPort) {
        MysqlAdminManagementService service = new MysqlAdminManagementService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "safetyPort", safetyPort);
        ReflectionTestUtils.setField(service, "healthPort", healthPort);
        return service;
    }

    private static final class FakeRepository implements IMysqlAdminRepository {
        private MysqlDataSourceAdminView dataSource;
        private MysqlTemplateAdminView template;
        private MysqlBindingAdminView binding;
        private long datasourceReferences;
        private boolean gatewayExists;
        private boolean templateExists;
        private boolean templateEnabled;
        private boolean bindingNameExists;

        @Override public MysqlAdminPage<MysqlDataSourceAdminView> pageDataSources(MysqlAdminQueries.DataSource query) { return new MysqlAdminPage<>(List.of(), 0, 1, 20); }
        @Override public Optional<MysqlDataSourceAdminView> findDataSource(String ref) { return Optional.ofNullable(dataSource); }
        @Override public void saveDataSource(MysqlDataSourceAdminCommand command) { dataSource = new MysqlDataSourceAdminView(1L, command.datasourceRef(), command.datasourceName(), command.datasourceType(), command.jdbcUrl(), command.username(), command.status(), true, null, null); }
        @Override public void changeDataSourceStatus(String ref, int status) { }
        @Override public void deleteDataSource(String ref) { dataSource = null; }
        @Override public long countDatasourceReferences(String ref) { return datasourceReferences; }
        @Override public MysqlAdminPage<MysqlTemplateAdminView> pageTemplates(MysqlAdminQueries.Template query) { return new MysqlAdminPage<>(List.of(), 0, 1, 20); }
        @Override public Optional<MysqlTemplateAdminView> findTemplate(Long id, String version) { return Optional.ofNullable(template); }
        @Override public Long saveTemplate(MysqlTemplateAdminCommand command) { return command.protocolId() == null ? 7L : command.protocolId(); }
        @Override public void changeTemplateStatus(Long id, String version, int status) { }
        @Override public void deleteTemplate(Long id, String version) { template = null; }
        @Override public long countTemplateBindings(Long id) { return 0; }
        @Override public MysqlAdminPage<MysqlBindingAdminView> pageBindings(MysqlAdminQueries.Binding query) { return new MysqlAdminPage<>(List.of(), 0, 1, 20); }
        @Override public Optional<MysqlBindingAdminView> findBinding(Long id) { return Optional.ofNullable(binding); }
        @Override public Long saveBinding(MysqlBindingAdminCommand command) { return 1L; }
        @Override public Long saveDynamicBinding(MysqlDynamicBindingAdminCommand command) {
            binding = new MysqlBindingAdminView(1L, command.gatewayId(), 1L, command.toolName(), command.toolType(),
                    command.toolDescription(), command.toolVersion(), 88L, "mysql", command.status(), null, null,
                    "DYNAMIC_READONLY", command.datasourceRef(), command.maxRows(), command.maxResultBytes(),
                    command.maxColumns(), command.timeoutMs());
            return 1L;
        }
        @Override public void changeBindingStatus(Long id, int status) { }
        @Override public void deleteBinding(Long id) { binding = null; }
        @Override public boolean bindingNameExists(String gatewayId, String toolName, Long excludingId) { return bindingNameExists; }
        @Override public boolean gatewayExists(String gatewayId) { return gatewayExists; }
        @Override public boolean templateExists(Long protocolId) { return templateExists; }
        @Override public boolean templateEnabled(Long protocolId) { return templateEnabled; }
        @Override public boolean templateDatasourceEnabled(Long protocolId) { return true; }
        @Override public boolean datasourceEnabled(String datasourceRef) { return true; }
    }
}
