package cn.bugstack.ai.domain.mysql.model.valobj;

/** SQL 安全责任链的稳定决策结果。 */
public final class SqlSafetyDecision {
    public enum Status { ALLOWED, REJECTED, PARSE_ERROR, POLICY_NOT_CONFIGURED }

    private final Status status;
    private final String code;
    private final String reason;

    private SqlSafetyDecision(Status status, String code, String reason) {
        this.status = status;
        this.code = code;
        this.reason = reason;
    }

    public static SqlSafetyDecision allowed() { return new SqlSafetyDecision(Status.ALLOWED, "", ""); }
    public static SqlSafetyDecision rejected(String reason) { return new SqlSafetyDecision(Status.REJECTED, "SQL_POLICY_REJECTED", reason); }
    public static SqlSafetyDecision parseError(String reason) { return new SqlSafetyDecision(Status.PARSE_ERROR, "SQL_SYNTAX_ERROR", reason); }
    public static SqlSafetyDecision policyNotConfigured(String reason) { return new SqlSafetyDecision(Status.POLICY_NOT_CONFIGURED, "SQL_POLICY_NOT_CONFIGURED", reason); }
    public boolean isAllowed() { return status == Status.ALLOWED; }
    public Status getStatus() { return status; }
    public String getCode() { return code; }
    public String getReason() { return reason; }
}
