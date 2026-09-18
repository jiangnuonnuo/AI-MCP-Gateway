package cn.bugstack.ai.domain.session.model.valobj.gateway;

import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import lombok.*;

import java.util.List;

/**
 * 协议配置
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/30 20:24
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpToolProtocolConfigVO {

    /** 逻辑协议类型，用于隔离相同 protocol_id 的不同协议。 */
    private String protocolType;

    /** 持久化协议逻辑标识。 */
    private Long protocolId;

    /** 协议业务状态：1 表示 ENABLED，0 表示 DISABLED。 */
    private Integer status;

    /**
     * Tool 的后端执行类型。旧 HTTP 配置为空时由执行上下文兼容推断为 HTTP。
     */
    private ToolBackendType backendType;

    /**
     * Tool 的执行模式。后端类型和执行模式共同决定执行器路由。
     */
    private ToolExecutionMode executionMode;

    /** MySQL 只读协议的服务端执行配置；该内部对象不会直接序列化为 MCP discovery 响应。 */
    private MysqlTemplateConfig mysqlTemplateConfig;

    /**
     * 请求协议配置
     */
    private HTTPConfig httpConfig;

    /**
     * 请求协议映射
     */
    private List<ProtocolMapping> requestProtocolMappings;

    /**
     * 响应映射只用于后端结果转换，不参与 MCP 输入 Schema 构建。
     */
    private List<ProtocolMapping> responseProtocolMappings;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MysqlTemplateConfig {
        /** 协议引用的兼容别名。 */
        private String templateRef;

        /** Gateway Tool 版本。 */
        private String templateVersion;

        /** 持久化 MySQL 协议标识；模板引用仅保留为兼容旧执行器的别名。 */
        private Long protocolId;

        /** 固定绑定的数据源业务引用。 */
        private String datasourceRef;

        /** 绑定数据源状态：1 表示 ENABLED，0 表示 DISABLED。 */
        private Integer datasourceStatus;

        /** 服务端持久化的只读 SQL；不会进入 MCP discovery 响应。 */
        private String sql;

        /** 由 request mapping 转换的扁平参数契约。 */
        private List<MysqlTemplateParameter> parameters;

        /** 协议级查询上限。 */
        private MysqlQueryPolicy policy;
    }

    @Data
    public static class HTTPConfig {
        /** HTTP 后端地址。 */
        private String httpUrl;

        /** JSON 格式的固定请求头。 */
        private String httpHeaders;

        /** HTTP 方法。 */
        private String httpMethod;

        /** HTTP 调用超时时间。 */
        private Integer timeout;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProtocolMapping {
        /**
         * 映射类型：request-请求参数映射，response-响应数据映射
         */
        private String mappingType;
        /**
         * 父级路径（如：xxxRequest01，用于构建嵌套结构，根节点为NULL）
         */
        private String parentPath;
        /**
         * 字段名称（如：city、company、name）
         */
        private String fieldName;
        /**
         * MCP完整路径（如：xxxRequest01.city、xxxRequest01.company.name）
         */
        private String mcpPath;
        /**
         * MCP数据类型：string/number/boolean/object/array
         */
        private String mcpType;
        /**
         * MCP字段描述
         */
        private String mcpDesc;
        /**
         * 是否必填：0-否，1-是（用于生成required数组）
         */
        private Integer isRequired;
        /**
         * 排序顺序（同级字段排序）
         */
        private Integer sortOrder;
    }

}
