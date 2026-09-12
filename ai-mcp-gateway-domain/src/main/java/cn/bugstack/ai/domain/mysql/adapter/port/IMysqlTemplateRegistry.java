package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;

import java.util.List;
import java.util.Optional;

/** 只读查询模板 Registry；仅已发布版本可交给执行器。 */
public interface IMysqlTemplateRegistry {
    Optional<MysqlTemplate> find(String templateRef, String version);

    default Optional<MysqlTemplate> findPublished(String templateRef, String version) {
        return find(templateRef, version).filter(MysqlTemplate::isPublished);
    }

    default List<MysqlTemplate> listPublished() { return List.of(); }
}
