package cn.bugstack.ai.test.infrastructure.repository;

import cn.bugstack.ai.domain.session.model.valobj.gateway.McpToolConfigVO;
import cn.bugstack.ai.domain.tool.model.valobj.ToolBackendType;
import cn.bugstack.ai.infrastructure.adapter.repository.SessionRepository;
import cn.bugstack.ai.infrastructure.dao.IMcpDataSourceDao;
import cn.bugstack.ai.infrastructure.dao.IMcpGatewayDao;
import cn.bugstack.ai.infrastructure.dao.IMcpGatewayToolDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolHttpDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMappingDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMysqlDao;
import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import cn.bugstack.ai.infrastructure.dao.po.McpGatewayToolPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolHttpPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMappingPO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMysqlPO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionRepositoryTest {

    @Test
    void keepsHttpAndMysqlBindingsIsolatedWhenProtocolIdsMatch() {
        Fixture fixture = fixture();
        McpGatewayToolPO httpTool = tool(1L, "httpTool", "http", 77L);
        McpGatewayToolPO mysqlTool = tool(2L, "mysqlTool", "mysql", 77L);
        when(fixture.toolDao.queryEnabledByGatewayId("gateway-a")).thenReturn(List.of(httpTool, mysqlTool));
        when(fixture.mappingDao.queryByProtocolKey(any())).thenAnswer(invocation -> {
            McpProtocolMappingPO key = invocation.getArgument(0);
            return List.of(McpProtocolMappingPO.builder().protocolType(key.getProtocolType())
                    .protocolId(77L).mappingType("request").fieldName("id").mcpPath("id")
                    .mcpType("integer").isRequired(1).sortOrder(1).build());
        });
        when(fixture.httpDao.queryMcpProtocolHttpByProtocolId(77L)).thenReturn(McpProtocolHttpPO.builder()
                .protocolId(77L).httpUrl("http://localhost/internal").httpMethod("GET").status(1).build());
        when(fixture.mysqlDao.queryByProtocolId(77L)).thenReturn(McpProtocolMysqlPO.builder()
                .protocolId(77L).datasourceId(41L).sqlText("SELECT * FROM fact_order WHERE order_id = :id")
                .maxRows(100).maxResultBytes(4096L).maxColumns(8).timeoutMs(1000).status(1).build());
        when(fixture.dataSourceDao.queryById(41L)).thenReturn(McpDataSourcePO.builder()
                .id(41L).datasourceRef("data-warehouse").datasourceType("mysql").status(1).build());

        List<McpToolConfigVO> tools = fixture.repository.queryMcpGatewayToolConfigListByGatewayId("gateway-a");

        assertEquals(2, tools.size());
        assertEquals(ToolBackendType.HTTP, tools.get(0).getMcpToolProtocolConfigVO().getBackendType());
        assertEquals(ToolBackendType.MYSQL, tools.get(1).getMcpToolProtocolConfigVO().getBackendType());
        assertEquals("data-warehouse", tools.get(1).getMcpToolProtocolConfigVO()
                .getMysqlTemplateConfig().getDatasourceRef());
    }

    @Test
    void omitsMysqlToolWhenDatasourceIsDisabled() {
        Fixture fixture = fixture();
        when(fixture.toolDao.queryEnabledByGatewayId("gateway-a"))
                .thenReturn(List.of(tool(2L, "mysqlTool", "mysql", 77L)));
        when(fixture.mappingDao.queryByProtocolKey(any())).thenReturn(List.of());
        when(fixture.mysqlDao.queryByProtocolId(77L)).thenReturn(McpProtocolMysqlPO.builder()
                .protocolId(77L).datasourceId(41L).sqlText("SELECT 1").status(1).build());
        when(fixture.dataSourceDao.queryById(41L)).thenReturn(McpDataSourcePO.builder()
                .id(41L).datasourceRef("data-warehouse").status(0).build());

        assertEquals(List.of(), fixture.repository.queryMcpGatewayToolConfigListByGatewayId("gateway-a"));
    }

    private static Fixture fixture() {
        SessionRepository repository = new SessionRepository();
        IMcpGatewayDao gatewayDao = mock(IMcpGatewayDao.class);
        IMcpGatewayToolDao toolDao = mock(IMcpGatewayToolDao.class);
        IMcpProtocolHttpDao httpDao = mock(IMcpProtocolHttpDao.class);
        IMcpProtocolMysqlDao mysqlDao = mock(IMcpProtocolMysqlDao.class);
        IMcpDataSourceDao dataSourceDao = mock(IMcpDataSourceDao.class);
        IMcpProtocolMappingDao mappingDao = mock(IMcpProtocolMappingDao.class);
        ReflectionTestUtils.setField(repository, "mcpGatewayDao", gatewayDao);
        ReflectionTestUtils.setField(repository, "mcpGatewayToolDao", toolDao);
        ReflectionTestUtils.setField(repository, "mcpProtocolHttpDao", httpDao);
        ReflectionTestUtils.setField(repository, "mcpProtocolMysqlDao", mysqlDao);
        ReflectionTestUtils.setField(repository, "mcpDataSourceDao", dataSourceDao);
        ReflectionTestUtils.setField(repository, "mcpProtocolMappingDao", mappingDao);
        return new Fixture(repository, toolDao, httpDao, mysqlDao, dataSourceDao, mappingDao);
    }

    private static McpGatewayToolPO tool(long toolId, String name, String type, long protocolId) {
        return McpGatewayToolPO.builder().gatewayId("gateway-a").toolId(toolId).toolName(name)
                .toolDescription(name).toolVersion("1.0.0").protocolType(type).protocolId(protocolId).status(1).build();
    }

    private record Fixture(SessionRepository repository,
                           IMcpGatewayToolDao toolDao,
                           IMcpProtocolHttpDao httpDao,
                           IMcpProtocolMysqlDao mysqlDao,
                           IMcpDataSourceDao dataSourceDao,
                           IMcpProtocolMappingDao mappingDao) {
    }
}
