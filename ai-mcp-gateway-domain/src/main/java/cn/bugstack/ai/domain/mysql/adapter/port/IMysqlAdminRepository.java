package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminPage;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDynamicBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminView;

import java.util.Optional;

/** MySQL 管理面的最小持久化端口；不承载状态机和安全策略。 */
public interface IMysqlAdminRepository {
    MysqlAdminPage<MysqlDataSourceAdminView> pageDataSources(MysqlAdminQueries.DataSource query);
    Optional<MysqlDataSourceAdminView> findDataSource(String datasourceRef);
    void saveDataSource(MysqlDataSourceAdminCommand command);
    void changeDataSourceStatus(String datasourceRef, int status);
    void deleteDataSource(String datasourceRef);
    long countDatasourceReferences(String datasourceRef);

    MysqlAdminPage<MysqlTemplateAdminView> pageTemplates(MysqlAdminQueries.Template query);
    Optional<MysqlTemplateAdminView> findTemplate(Long protocolId, String version);
    Long saveTemplate(MysqlTemplateAdminCommand command);
    void changeTemplateStatus(Long protocolId, String version, int status);
    void deleteTemplate(Long protocolId, String version);
    long countTemplateBindings(Long protocolId);

    MysqlAdminPage<MysqlBindingAdminView> pageBindings(MysqlAdminQueries.Binding query);
    Optional<MysqlBindingAdminView> findBinding(Long id);
    Long saveBinding(MysqlBindingAdminCommand command);
    Long saveDynamicBinding(MysqlDynamicBindingAdminCommand command);
    void changeBindingStatus(Long id, int status);
    void deleteBinding(Long id);
    boolean bindingNameExists(String gatewayId, String toolName, Long excludingId);
    boolean gatewayExists(String gatewayId);
    boolean templateExists(Long protocolId);
    boolean templateEnabled(Long protocolId);
    boolean templateDatasourceEnabled(Long protocolId);
    boolean datasourceEnabled(String datasourceRef);
}
