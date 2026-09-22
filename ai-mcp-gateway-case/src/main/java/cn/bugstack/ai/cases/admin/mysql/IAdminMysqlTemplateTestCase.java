package cn.bugstack.ai.cases.admin.mysql;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;

import java.util.Map;

/** 管理端 SQL 模板真实执行用例。 */
public interface IAdminMysqlTemplateTestCase {
    MysqlTemplateTestReport execute(String templateRef, String version, Map<String, ?> parameters);
}
