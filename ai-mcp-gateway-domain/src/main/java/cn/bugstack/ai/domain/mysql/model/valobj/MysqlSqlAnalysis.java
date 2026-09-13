package cn.bugstack.ai.domain.mysql.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SQL Parser 提供给领域安全规则的逻辑分析结果，不暴露具体 Parser 或 JDBC 类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MysqlSqlAnalysis {

    /** 词法分析后的逻辑 Token。 */
    private List<Token> tokens;

    /** 语句分隔符数量。 */
    private int semicolonCount;

    /** 命名参数数量。 */
    private int namedParameterCount;

    /** 位置参数数量。 */
    private int positionalParameterCount;

    /** 首个语句关键字。 */
    private String firstWord;

    /** 是否为可识别的查询语句。 */
    private boolean selectStatement;

    /** 是否包含行锁。 */
    private boolean rowLock;

    /** 逻辑 SQL Token 类型。 */
    public enum Kind {
        WORD,
        STRING,
        SYMBOL,
        NUMBER
    }

    /** 单个逻辑 SQL Token。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Token {

        /** Token 类型。 */
        private Kind kind;

        /** Token 文本。 */
        private String text;

        /** 返回不受区域设置影响的大写文本。 */
        public String upper() {
            return text == null ? "" : text.toUpperCase(java.util.Locale.ROOT);
        }
    }
}
