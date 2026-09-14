package cn.bugstack.ai.test.infrastructure.adapter.port;

import cn.bugstack.ai.config.MysqlConnectionProperties;
import cn.bugstack.ai.config.MysqlDataSourceConfig;
import cn.bugstack.ai.config.MysqlMvpDataSourceInitializer;
import cn.bugstack.ai.config.MysqlMvpRegistryInitializer;
import cn.bugstack.ai.config.MysqlMvpTemplateFactory;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import cn.bugstack.ai.domain.mysql.service.safety.MysqlSqlSafetyChain;
import cn.bugstack.ai.domain.session.adapter.repository.ISessionRepository;
import cn.bugstack.ai.domain.session.model.valobj.McpSchemaVO;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsCallHandler;
import cn.bugstack.ai.domain.session.service.message.handler.impl.ToolsListHandler;
import cn.bugstack.ai.domain.tool.executor.ToolExecutorRouter;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolAccessPolicy;
import cn.bugstack.ai.infrastructure.adapter.port.InMemoryToolExecutionAudit;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlJdbcGateway;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlTemplateExecutor;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlDataSourceRegistry;
import cn.bugstack.ai.infrastructure.adapter.repository.InMemoryMysqlTemplateRegistry;
import cn.bugstack.ai.infrastructure.mysql.MysqlTemplateParameterBinder;
import cn.bugstack.ai.infrastructure.observability.MysqlExecutionMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 使用运行时凭证执行真实的 MCP 到 MySQL/JDBC 纵向链路。
 *
 * <p>该测试不写入目标数仓；未注入凭证时跳过，避免测试环境误连数据库。</p>
 */
class MysqlMcpRealIntegrationTest {

    @Test
    void executesMcpToolAgainstRealDataWarehouse() {
        String username = System.getenv("WAREHOUSE_MYSQL_USERNAME");
        String password = System.getenv("WAREHOUSE_MYSQL_PASSWORD");
        assumeTrue(username != null && !username.isBlank());
        assumeTrue(password != null && !password.isBlank());

        String jdbcUrl = System.getenv().getOrDefault("WAREHOUSE_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/data_warehouse?useUnicode=true&characterEncoding=utf8"
                        + "&serverTimezone=Asia/Shanghai&useSSL=false");
        MysqlConnectionProperties properties = MysqlConnectionProperties.builder()
                .datasource(Map.of(MysqlMvpTemplateFactory.DATASOURCE_REF,
                        MysqlDataSourceConfig.builder()
                                .id(MysqlMvpTemplateFactory.DATASOURCE_REF)
                                .jdbcUrl(jdbcUrl)
                                .username(username)
                                .runtimePassword(password)
                                .build()))
                .build();

        InMemoryMysqlDataSourceRegistry dataSources = new InMemoryMysqlDataSourceRegistry();
        MysqlMvpDataSourceInitializer dataSourceInitializer = new MysqlMvpDataSourceInitializer();
        ReflectionTestUtils.setField(dataSourceInitializer, "properties", properties);
        ReflectionTestUtils.setField(dataSourceInitializer, "registry", dataSources);
        dataSourceInitializer.registerRuntimeDataSource();

        InMemoryMysqlTemplateRegistry templates = new InMemoryMysqlTemplateRegistry();
        MysqlMvpRegistryInitializer templateInitializer = new MysqlMvpRegistryInitializer();
        ReflectionTestUtils.setField(templateInitializer, "properties", properties);
        ReflectionTestUtils.setField(templateInitializer, "templateRegistry", templates);
        templateInitializer.registerMvpTemplate();

        MysqlJdbcGateway jdbcGateway = new MysqlJdbcGateway();
        ReflectionTestUtils.setField(jdbcGateway, "connectionSettingsRegistry", properties);
        ReflectionTestUtils.setField(jdbcGateway, "parameterBinder", new MysqlTemplateParameterBinder());

        MysqlSqlSafetyChain safetyChain = MysqlSqlSafetyChain.defaultChain();
        ReflectionTestUtils.setField(safetyChain, "analysisPort",
                new cn.bugstack.ai.infrastructure.adapter.port.MysqlSqlParser());

        MysqlTemplateQueryService queryService = new MysqlTemplateQueryService();
        ReflectionTestUtils.setField(queryService, "templateRegistry", templates);
        ReflectionTestUtils.setField(queryService, "dataSourceRegistry", dataSources);
        ReflectionTestUtils.setField(queryService, "safetyPort", safetyChain);
        ReflectionTestUtils.setField(queryService, "queryPort", jdbcGateway);

        MysqlTemplateExecutor mysqlExecutor = new MysqlTemplateExecutor();
        ReflectionTestUtils.setField(mysqlExecutor, "queryService", queryService);
        ReflectionTestUtils.setField(mysqlExecutor, "metricsPort", new MysqlExecutionMetrics());

        ToolExecutorRouter router = new ToolExecutorRouter();
        ReflectionTestUtils.setField(router, "executors", List.of(mysqlExecutor));
        ReflectionTestUtils.setField(router, "auditPort", new InMemoryToolExecutionAudit());

        ISessionRepository sessionRepository = mock(ISessionRepository.class);
        when(sessionRepository.queryMcpGatewayToolConfigListByGatewayId("real-integration"))
                .thenReturn(List.of());
        when(sessionRepository.queryMcpGatewayProtocolConfig("real-integration",
                MysqlMvpTemplateFactory.TEMPLATE_ID)).thenReturn(null);
        InMemoryToolAccessPolicy accessPolicy = new InMemoryToolAccessPolicy();

        ToolsListHandler listHandler = new ToolsListHandler();
        ReflectionTestUtils.setField(listHandler, "repository", sessionRepository);
        ReflectionTestUtils.setField(listHandler, "mysqlTemplateRegistry", templates);
        ReflectionTestUtils.setField(listHandler, "accessPolicy", accessPolicy);

        ToolsCallHandler callHandler = new ToolsCallHandler();
        ReflectionTestUtils.setField(callHandler, "repository", sessionRepository);
        ReflectionTestUtils.setField(callHandler, "toolExecutionPort", router);
        ReflectionTestUtils.setField(callHandler, "mysqlTemplateRegistry", templates);
        ReflectionTestUtils.setField(callHandler, "accessPolicy", accessPolicy);

        try {
            McpSchemaVO.JSONRPCResponse listed = listHandler.handle("real-integration",
                    new McpSchemaVO.JSONRPCRequest("2.0", "tools/list", "list-real", Map.of()));
            Map<?, ?> listResult = assertInstanceOf(Map.class, listed.result());
            List<?> tools = assertInstanceOf(List.class, listResult.get("tools"));
            assertEquals(1, tools.size());
            assertTrue(tools.get(0).toString().contains(MysqlMvpTemplateFactory.TEMPLATE_ID));

            McpSchemaVO.JSONRPCResponse called = callHandler.handle("real-integration",
                    new McpSchemaVO.JSONRPCRequest("2.0", "tools/call", "call-real",
                            Map.of("name", MysqlMvpTemplateFactory.TEMPLATE_ID,
                                    "arguments", Map.of(
                                            "fromTime", "2024-01-01 00:00:00",
                                            "toTime", "2025-01-01 00:00:00",
                                            "orderStatus", "1"))));
            Map<?, ?> callResult = assertInstanceOf(Map.class, called.result());
            assertEquals(Boolean.FALSE, callResult.get("isError"));
            assertNotNull(callResult.get("columns"));
            assertNotNull(callResult.get("rows"));
            assertTrue(((Number) callResult.get("rowCount")).intValue() > 0);
            assertFalse(callResult.get("queryId").toString().isBlank());
        } finally {
            jdbcGateway.close();
        }
    }
}
