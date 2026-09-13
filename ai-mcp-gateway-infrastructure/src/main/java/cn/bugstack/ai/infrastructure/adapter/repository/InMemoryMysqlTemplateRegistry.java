package cn.bugstack.ai.infrastructure.adapter.repository;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlTemplateRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** MVP 模板 Registry；后续可替换为控制库适配器而不改变执行契约。 */
@Repository("mysqlTemplateRegistry")
public class InMemoryMysqlTemplateRegistry implements IMysqlTemplateRegistry {
    private Map<String, MysqlTemplate> templates = new ConcurrentHashMap<>();

    public void register(MysqlTemplate template) {
        save(template);
    }

    @Override
    public void save(MysqlTemplate template) {
        if (template == null) throw new IllegalArgumentException("template must not be null");
        template.validate();
        template.getParameters().forEach(MysqlTemplateParameter::validate);
        templates.put(key(template.getId(), template.getVersion()), template);
    }

    @Override
    public Optional<MysqlTemplate> find(String templateRef, String version) {
        return Optional.ofNullable(templates.get(key(templateRef, version)));
    }

    @Override
    public Optional<MysqlTemplate> findPublished(String templateRef, String version) {
        return find(templateRef, version).filter(MysqlTemplate::isPublished);
    }

    @Override
    public List<MysqlTemplate> listPublished() {
        List<MysqlTemplate> result = new ArrayList<>();
        templates.values().stream().filter(MysqlTemplate::isPublished)
                .sorted(Comparator.comparing(MysqlTemplate::getName))
                .forEach(result::add);
        return List.copyOf(result);
    }

    @Override
    public void delete(String templateRef, String version) {
        templates.remove(key(templateRef, version));
    }

    private static String key(String id, String version) {
        if (id == null || id.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("template id and version must not be blank");
        }
        return id + "@" + version;
    }
}
