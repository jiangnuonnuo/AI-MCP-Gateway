package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SQL 安全责任链的稳定决策结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlSafetyDecision {

    /** SQL 安全判断状态。 */
    private Status status;

    /** 对外稳定错误码。 */
    private String code;

    /** 供日志和审计使用的非敏感原因。 */
    private String reason;

    /** SQL 安全判断状态枚举。 */
    public enum Status {
        CONTINUE,
        ALLOWED,
        REJECTED,
        FAILED,
        PARSE_ERROR,
        POLICY_NOT_CONFIGURED
    }

    public static SqlSafetyDecision allowed() {
        return new SqlSafetyDecision(Status.ALLOWED, "", "");
    }

    /** 返回责任链继续执行的中间决策。 */
    public static SqlSafetyDecision continueDecision() {
        return new SqlSafetyDecision(Status.CONTINUE, "", "");
    }

    public static SqlSafetyDecision rejected(String reason) {
        return new SqlSafetyDecision(Status.REJECTED, "SQL_POLICY_REJECTED", reason);
    }

    /** 返回无法可靠判断时的失败关闭决策。 */
    public static SqlSafetyDecision failed(String reason) {
        return new SqlSafetyDecision(Status.FAILED, "SQL_SAFETY_FAILED", reason);
    }

    public static SqlSafetyDecision parseError(String reason) {
        return new SqlSafetyDecision(Status.PARSE_ERROR, "SQL_SYNTAX_ERROR", reason);
    }

    public static SqlSafetyDecision policyNotConfigured(String reason) {
        return new SqlSafetyDecision(Status.POLICY_NOT_CONFIGURED, "SQL_POLICY_NOT_CONFIGURED", reason);
    }

    public boolean isAllowed() {
        return status == Status.ALLOWED;
    }
}
