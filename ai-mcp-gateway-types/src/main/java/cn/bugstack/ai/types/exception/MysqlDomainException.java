package cn.bugstack.ai.types.exception;

/**
 * MySQL 领域流程对外暴露的稳定错误。
 *
 * <p>异常码是跨模块契约，异常类型放在 types 模块，避免 Domain 服务包被
 * Infrastructure、Case 或 Trigger 作为公共异常入口依赖。</p>
 */
public class MysqlDomainException extends AppException {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 1L;

    public MysqlDomainException(String code, String message) {
        super(code, message);
    }

    /** 保持 RuntimeException 调用方可读取领域错误描述。 */
    @Override
    public String getMessage() {
        return getInfo();
    }
}
