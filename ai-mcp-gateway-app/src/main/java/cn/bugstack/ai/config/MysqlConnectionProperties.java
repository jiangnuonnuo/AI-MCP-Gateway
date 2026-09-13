package cn.bugstack.ai.config;

import cn.bugstack.ai.types.config.MysqlConnectionSettings;
import cn.bugstack.ai.types.config.MysqlConnectionSettingsRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MySQL 启动配置集合。
 *
 * <p>配置对象位于 App Config，由应用负责绑定和装配；Infrastructure 仅依赖
 * {@link MysqlConnectionSettingsRegistry} 技术契约，避免反向依赖启动层。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "gateway.mysql")
public class MysqlConnectionProperties implements MysqlConnectionSettingsRegistry {

    /** 按数据源引用保存的连接池配置。 */
    @Builder.Default
    private Map<String, MysqlDataSourceConfig> datasource = new LinkedHashMap<>();

    /** 是否启用 MVP 数据源和模板初始化。 */
    @Builder.Default
    private boolean mvpEnabled = true;

    @Override
    public Optional<MysqlConnectionSettings> find(String datasourceRef) {
        if (datasourceRef == null || datasourceRef.isBlank() || datasource == null) {
            return Optional.empty();
        }
        MysqlDataSourceConfig config = datasource.get(datasourceRef);
        if (config == null) {
            return Optional.empty();
        }
        if (config.getId() == null || config.getId().isBlank()) {
            config.setId(datasourceRef);
        }
        return Optional.of(config);
    }
}
