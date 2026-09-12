package cn.bugstack.ai.infrastructure.adapter.port;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlTemplateRegistry;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** MVP 模板 Registry；后续可替换为控制库适配器而不改变执行契约。 */
@Component("mysqlTemplateRegistry")
public class InMemoryMysqlTemplateRegistry implements IMysqlTemplateRegistry {
    private final Map<String, MysqlTemplate> templates = new ConcurrentHashMap<>();

    public void register(MysqlTemplate template) {
        if (template == null) throw new IllegalArgumentException("template must not be null");
        templates.put(key(template.getId(), template.getVersion()), template);
    }

    public void publish(String templateRef, String version) {
        replaceStatus(templateRef, version, MysqlTemplateStatus.PUBLISHED);
    }

    public void disable(String templateRef, String version) {
        replaceStatus(templateRef, version, MysqlTemplateStatus.DISABLED);
    }

    public void deprecate(String templateRef, String version) {
        replaceStatus(templateRef, version, MysqlTemplateStatus.DEPRECATED);
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

    public void remove(String templateRef, String version) {
        templates.remove(key(templateRef, version));
    }

    private void replaceStatus(String templateRef, String version, MysqlTemplateStatus status) {
        String key = key(templateRef, version);
        MysqlTemplate current = templates.get(key);
        if (current == null) throw new IllegalArgumentException("template not found");
        templates.put(key, new MysqlTemplate(current.getId(), current.getVersion(), current.getName(),
                current.getDescription(), current.getDatasourceRef(), current.getSql(), current.getParameters(),
                status, current.getPolicy()));
    }

    private static String key(String id, String version) {
        if (id == null || id.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("template id and version must not be blank");
        }
        return id + "@" + version;
    }
}
