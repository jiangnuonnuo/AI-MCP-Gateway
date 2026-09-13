package cn.bugstack.ai.domain.mysql.adapter.port;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;

import java.util.List;
import java.util.Optional;

/**
 * 只读查询模板存取契约；仅已发布版本可交给执行器。
 *
 * <p>接口名称保持 Registry 以兼容当前 MVP，具体存取实现属于 Infrastructure
 * 的 {@code adapter.repository}，发布和状态迁移仍由 Domain 服务负责。</p>
 */
public interface IMysqlTemplateRegistry {
    Optional<MysqlTemplate> find(String templateRef, String version);

    void save(MysqlTemplate template);

    /** 删除指定模板版本。 */
    void delete(String templateRef, String version);

    default Optional<MysqlTemplate> findPublished(String templateRef, String version) {
        return find(templateRef, version).filter(MysqlTemplate::isPublished);
    }

    default List<MysqlTemplate> listPublished() { return List.of(); }
}
