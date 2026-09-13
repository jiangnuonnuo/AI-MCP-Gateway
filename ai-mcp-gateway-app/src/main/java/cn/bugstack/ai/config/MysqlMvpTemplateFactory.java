package cn.bugstack.ai.config;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;

import java.util.List;

/**
 * 经过评审的 MVP 查询模板工厂。
 *
 * <p>模板定义属于应用装配内容，SQL 仍由服务端模板持有，不通过 tools/list 暴露。</p>
 */
public final class MysqlMvpTemplateFactory {

    /** MVP 模板唯一标识。 */
    public static final String TEMPLATE_ID = "warehouse_channel_sales";

    /** MVP 模板版本。 */
    public static final String TEMPLATE_VERSION = "1";

    /** MVP 数据源引用。 */
    public static final String DATASOURCE_REF = "warehouse-test";

    private MysqlMvpTemplateFactory() {
    }

    /** 构造已发布的 MVP 模板。 */
    public static MysqlTemplate publishedTemplate() {
        String sql = "SELECT c.channel_id AS channelId, c.channel_name AS channelName, "
                + "COUNT(*) AS orderCount, COALESCE(SUM(o.pay_amount), 0) AS totalAmount "
                + "FROM fact_order o JOIN dim_channel c ON c.channel_id = o.channel_id "
                + "WHERE o.order_time >= :fromTime AND o.order_time < :toTime "
                + "AND o.order_status = :orderStatus "
                + "GROUP BY c.channel_id, c.channel_name ORDER BY totalAmount DESC";
        return new MysqlTemplate(TEMPLATE_ID, TEMPLATE_VERSION, TEMPLATE_ID,
                "按时间范围和订单状态统计渠道订单数及支付金额", DATASOURCE_REF, sql,
                List.of(new MysqlTemplateParameter("fromTime", MysqlParameterType.DATETIME, true, "起始时间（含）"),
                        new MysqlTemplateParameter("toTime", MysqlParameterType.DATETIME, true, "结束时间（不含）"),
                        new MysqlTemplateParameter("orderStatus", MysqlParameterType.STRING, true, "订单状态")),
                MysqlTemplateStatus.PUBLISHED,
                new MysqlQueryPolicy(64 * 1024, 1_000, 4 * 1024 * 1024, 32, 30_000, true));
    }
}
