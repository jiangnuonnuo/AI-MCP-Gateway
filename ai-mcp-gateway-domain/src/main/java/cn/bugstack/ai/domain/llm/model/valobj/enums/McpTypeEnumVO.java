package cn.bugstack.ai.domain.llm.model.valobj.enums;

/**
 * MCP 连接类型枚举（对接方式）
 * 支持 SSE（默认）和 Streamable
 *
 * @author xiaofuge
 */
public enum McpTypeEnumVO {
    /** SSE 方式（默认）*/
    SSE("sse"),
    /** Streamable 方式 */
    STREAMABLE("streamable");

    private final String code;

    McpTypeEnumVO(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
