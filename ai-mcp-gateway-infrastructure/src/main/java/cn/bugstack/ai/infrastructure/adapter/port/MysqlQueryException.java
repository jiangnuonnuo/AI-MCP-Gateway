package cn.bugstack.ai.infrastructure.adapter.port;

/** 对外稳定的 MySQL 执行错误，不暴露 JDBC URL、凭证或驱动堆栈。 */
public class MysqlQueryException extends RuntimeException {
    private final String code;

    public MysqlQueryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MysqlQueryException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
