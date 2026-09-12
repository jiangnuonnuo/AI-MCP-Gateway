package cn.bugstack.ai.domain.session.model.valobj.gateway;

import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.domain.tool.model.valobj.ToolExecutionMode;
import lombok.*;

import java.util.List;

/**
 * 协议配置
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/1/30 20:24
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class McpToolProtocolConfigVO {

    /**
     * Tool 的后端执行类型。旧 HTTP 配置为空时由执行上下文兼容推断为 HTTP。
     */
    private ToolBackendType backendType;

    /**
     * Tool 的执行模式。后端类型和执行模式共同决定执行器路由。
     */
    private ToolExecutionMode executionMode;

    /**
     * MySQL 只读模板引用。只暴露引用和版本，SQL 正文及数据源凭证始终保留在服务端 Registry。
     */
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
        private String templateRef;
        private String templateVersion;
    }

    @Data
    public static class HTTPConfig {
        private String httpUrl;
        private String httpHeaders;
        private String httpMethod;
        private Integer timeout;
    }

    @Getter
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
