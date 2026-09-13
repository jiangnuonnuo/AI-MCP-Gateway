package cn.bugstack.ai.types.exception;

/**
 * MySQL 模板参数绑定的稳定错误。
 *
 * <p>参数绑定实现位于 Infrastructure，但错误码属于跨模块契约，因此异常类型统一
 * 放在 types 模块。</p>
 */
public class MysqlParameterException extends IllegalArgumentException {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 1L;

    /** 参数绑定错误码，供边界适配器映射稳定响应。 */
    private String code;

    public MysqlParameterException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 保持 RuntimeException 调用方可读取参数错误描述。 */
    @Override
    public String getMessage() {
        return super.getMessage();
    }

    /** 返回参数绑定错误码。 */
    public String getCode() {
        return code;
    }
}
