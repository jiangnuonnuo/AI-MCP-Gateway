package cn.bugstack.ai.cases.admin.mysql;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 管理端模板测试 Case。
 *
 * <p>仅接收模板引用、版本和参数，执行边界由 Domain 查询服务从已发布模板解析。</p>
 */
@Service
public class AdminMysqlTemplateTestCase implements IAdminMysqlTemplateTestCase {

    @Resource
    private MysqlTemplateQueryService queryService;

    @Override
    public MysqlTemplateTestReport execute(String templateRef, String version, Map<String, ?> parameters) {
        return queryService.executeWithReport(templateRef, version, parameters, null);
    }
}
