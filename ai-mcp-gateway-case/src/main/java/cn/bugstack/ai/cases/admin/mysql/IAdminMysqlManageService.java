package cn.bugstack.ai.cases.admin.mysql;

import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminPage;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminView;

/** MySQL 管理用例入口，供 REST、CLI 等 Trigger 复用。 */
public interface IAdminMysqlManageService {
    MysqlAdminPage<MysqlDataSourceAdminView> pageDataSources(MysqlAdminQueries.DataSource query);
    MysqlDataSourceAdminView findDataSource(String datasourceRef);
    MysqlDataSourceAdminView saveDataSource(MysqlDataSourceAdminCommand command);
    MysqlDataSourceAdminView changeDataSourceStatus(String datasourceRef, int status);
    void deleteDataSource(String datasourceRef);
    MysqlAdminPage<MysqlTemplateAdminView> pageTemplates(MysqlAdminQueries.Template query);
    MysqlTemplateAdminView findTemplate(Long protocolId, String version);
    MysqlTemplateAdminView saveTemplate(MysqlTemplateAdminCommand command);
    MysqlTemplateAdminView changeTemplateStatus(Long protocolId, String version, int status);
    void deleteTemplate(Long protocolId, String version);
    MysqlAdminPage<MysqlBindingAdminView> pageBindings(MysqlAdminQueries.Binding query);
    MysqlBindingAdminView findBinding(Long id);
    MysqlBindingAdminView saveBinding(MysqlBindingAdminCommand command);
    MysqlBindingAdminView changeBindingStatus(Long id, int status);
    void deleteBinding(Long id);
}
