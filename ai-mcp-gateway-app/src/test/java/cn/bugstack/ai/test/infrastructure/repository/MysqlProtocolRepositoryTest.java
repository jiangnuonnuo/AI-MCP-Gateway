package cn.bugstack.ai.test.infrastructure.repository;

import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.infrastructure.adapter.repository.MysqlProtocolRepository;
import cn.bugstack.ai.infrastructure.dao.IMcpDataSourceDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMappingDao;
import cn.bugstack.ai.infrastructure.dao.IMcpProtocolMysqlDao;
import cn.bugstack.ai.infrastructure.dao.po.McpDataSourcePO;
import cn.bugstack.ai.infrastructure.dao.po.McpProtocolMysqlPO;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlProtocolRepositoryTest {

    @Test
    void createsNewProtocolDisabledAndPersistsFlatMappings() {
        Fixture fixture = fixture();
        when(fixture.protocolDao.queryByProtocolId(900001L)).thenReturn(null);
        when(fixture.dataSourceDao.queryByDatasourceRef("data-warehouse"))
                .thenReturn(McpDataSourcePO.builder().id(41L).datasourceRef("data-warehouse").build());

        fixture.repository.save(protocol("SELECT * FROM fact_order WHERE order_id = :orderId",
                MysqlTemplateStatus.ENABLED));

        ArgumentCaptor<McpProtocolMysqlPO> protocol = ArgumentCaptor.forClass(McpProtocolMysqlPO.class);
        verify(fixture.protocolDao).insert(protocol.capture());
        assertEquals(0, protocol.getValue().getStatus());
        assertEquals(41L, protocol.getValue().getDatasourceId());
        verify(fixture.mappingDao).deleteByProtocolKey(any());
        verify(fixture.mappingDao).insert(any());
    }

    @Test
    void refusesToOverwriteEnabledProtocolContent() {
        Fixture fixture = fixture();
        when(fixture.protocolDao.queryByProtocolId(900001L)).thenReturn(McpProtocolMysqlPO.builder()
                .protocolId(900001L).datasourceId(41L).sqlText("SELECT 1")
                .maxRows(1000).maxResultBytes(4194304L).maxColumns(128).timeoutMs(30000)
                .status(1).build());
        when(fixture.dataSourceDao.queryByDatasourceRef("data-warehouse"))
                .thenReturn(McpDataSourcePO.builder().id(41L).datasourceRef("data-warehouse").build());

        MysqlDomainException error = assertThrows(MysqlDomainException.class,
                () -> fixture.repository.save(protocol("SELECT 2", MysqlTemplateStatus.ENABLED)));

        assertEquals("PROTOCOL_IMMUTABLE", error.getCode());
        verify(fixture.protocolDao, never()).updateByProtocolId(any());
    }

    private static Fixture fixture() {
        IMcpProtocolMysqlDao protocolDao = mock(IMcpProtocolMysqlDao.class);
        IMcpDataSourceDao dataSourceDao = mock(IMcpDataSourceDao.class);
        IMcpProtocolMappingDao mappingDao = mock(IMcpProtocolMappingDao.class);
        MysqlProtocolRepository repository = new MysqlProtocolRepository();
        ReflectionTestUtils.setField(repository, "protocolDao", protocolDao);
        ReflectionTestUtils.setField(repository, "dataSourceDao", dataSourceDao);
        ReflectionTestUtils.setField(repository, "mappingDao", mappingDao);
        return new Fixture(repository, protocolDao, dataSourceDao, mappingDao);
    }

    private static MysqlTemplate protocol(String sql, MysqlTemplateStatus status) {
        return MysqlTemplate.builder().id("900001").version("1.0.0").name("orderSummary")
                .description("Order summary").datasourceRef("data-warehouse").sql(sql)
                .parameters(List.of(MysqlTemplateParameter.builder().name("orderId")
                        .type(MysqlParameterType.LONG).required(true).description("Order identifier").build()))
                .status(status).policy(MysqlQueryPolicy.defaults()).build();
    }

    private record Fixture(MysqlProtocolRepository repository,
                           IMcpProtocolMysqlDao protocolDao,
                           IMcpDataSourceDao dataSourceDao,
                           IMcpProtocolMappingDao mappingDao) {
    }
}
