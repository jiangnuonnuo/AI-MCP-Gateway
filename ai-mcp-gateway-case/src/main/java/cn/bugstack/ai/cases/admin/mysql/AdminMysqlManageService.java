package cn.bugstack.ai.cases.admin.mysql;

import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminPage;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminCommand;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlTemplateAdminView;
import cn.bugstack.ai.domain.mysql.service.MysqlAdminManagementService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/** MySQL 管理 Case；只做用例入口转发，跨资源规则由 Domain 服务决定。 */
@Service
public class AdminMysqlManageService implements IAdminMysqlManageService {
    @Resource
    private MysqlAdminManagementService managementService;

    @Override public MysqlAdminPage<MysqlDataSourceAdminView> pageDataSources(MysqlAdminQueries.DataSource query) { return managementService.pageDataSources(query); }
    @Override public MysqlDataSourceAdminView findDataSource(String ref) { return managementService.findDataSource(ref); }
    @Override public MysqlDataSourceAdminView saveDataSource(MysqlDataSourceAdminCommand command) { return managementService.saveDataSource(command); }
    @Override public MysqlDataSourceAdminView testDataSource(String ref) { return managementService.testDataSource(ref); }
    @Override public MysqlDataSourceAdminView changeDataSourceStatus(String ref, int status) { return managementService.changeDataSourceStatus(ref, status); }
    @Override public void deleteDataSource(String ref) { managementService.deleteDataSource(ref); }
    @Override public MysqlAdminPage<MysqlTemplateAdminView> pageTemplates(MysqlAdminQueries.Template query) { return managementService.pageTemplates(query); }
    @Override public MysqlTemplateAdminView findTemplate(Long id, String version) { return managementService.findTemplate(id, version); }
    @Override public MysqlTemplateAdminView saveTemplate(MysqlTemplateAdminCommand command) { return managementService.saveTemplate(command); }
    @Override public MysqlTemplateAdminView changeTemplateStatus(Long id, String version, int status) { return managementService.changeTemplateStatus(id, version, status); }
    @Override public void deleteTemplate(Long id, String version) { managementService.deleteTemplate(id, version); }
    @Override public MysqlAdminPage<MysqlBindingAdminView> pageBindings(MysqlAdminQueries.Binding query) { return managementService.pageBindings(query); }
    @Override public MysqlBindingAdminView findBinding(Long id) { return managementService.findBinding(id); }
    @Override public MysqlBindingAdminView saveBinding(MysqlBindingAdminCommand command) { return managementService.saveBinding(command); }
    @Override public MysqlBindingAdminView changeBindingStatus(Long id, int status) { return managementService.changeBindingStatus(id, status); }
    @Override public void deleteBinding(Long id) { managementService.deleteBinding(id); }
}
