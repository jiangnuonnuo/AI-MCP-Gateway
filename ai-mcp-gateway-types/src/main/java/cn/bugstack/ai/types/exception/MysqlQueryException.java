package cn.bugstack.ai.types.exception;

/**
 * MySQL 查询执行的稳定技术错误。
 *
 * <p>异常类型统一放在 types 模块，具体技术故障通过错误码区分；不得向调用方暴露
 * JDBC URL、凭证或驱动堆栈。</p>
 */
public class MysqlQueryException extends AppException {

    /** Java 序列化版本标识。 */
    private static final long serialVersionUID = 1L;

    public MysqlQueryException(String code, String message) {
        super(code, message);
    }

    public MysqlQueryException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    /** 保持 RuntimeException 调用方可读取技术错误描述。 */
    @Override
    public String getMessage() {
        return getInfo();
    }
}
