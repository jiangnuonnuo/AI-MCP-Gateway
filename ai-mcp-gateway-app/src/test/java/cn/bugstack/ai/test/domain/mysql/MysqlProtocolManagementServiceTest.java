package cn.bugstack.ai.test.domain.mysql;

import cn.bugstack.ai.domain.mysql.adapter.port.IMysqlProtocolRepository;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlQueryPolicy;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplate;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateStatus;
import cn.bugstack.ai.domain.mysql.service.MysqlProtocolManagementService;
import cn.bugstack.ai.domain.mysql.service.safety.MysqlSqlSafetyChain;
import cn.bugstack.ai.infrastructure.adapter.port.MysqlSqlParser;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MysqlProtocolManagementServiceTest {

    @Test
    void validatesSafeSelectBeforePersistence() {
        IMysqlProtocolRepository repository = mock(IMysqlProtocolRepository.class);
        MysqlProtocolManagementService service = service(repository);
        MysqlTemplate protocol = protocol("SELECT * FROM fact_order WHERE order_id = :orderId",
                List.of(parameter("orderId")));

        service.save(protocol);

        verify(repository).save(protocol);
    }

    @Test
    void rejectsUnsafeSqlBeforePersistence() {
        IMysqlProtocolRepository repository = mock(IMysqlProtocolRepository.class);
        MysqlProtocolManagementService service = service(repository);
        MysqlTemplate protocol = protocol("DELETE FROM fact_order", List.of());

        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.save(protocol));

        assertEquals("SQL_POLICY_REJECTED", error.getCode());
        verify(repository, never()).save(protocol);
    }

    @Test
    void rejectsDuplicateFlatRequestMappings() {
        IMysqlProtocolRepository repository = mock(IMysqlProtocolRepository.class);
        MysqlProtocolManagementService service = service(repository);
        MysqlTemplate protocol = protocol("SELECT * FROM fact_order WHERE order_id = :orderId",
                List.of(parameter("orderId"), parameter("orderId")));

        MysqlDomainException error = assertThrows(MysqlDomainException.class, () -> service.save(protocol));

        assertEquals("SQL_PARAMETER_ERROR", error.getCode());
        verify(repository, never()).save(protocol);
    }

    private static MysqlProtocolManagementService service(IMysqlProtocolRepository repository) {
        MysqlSqlSafetyChain safetyChain = MysqlSqlSafetyChain.defaultChain();
        ReflectionTestUtils.setField(safetyChain, "analysisPort", new MysqlSqlParser());
        MysqlProtocolManagementService service = new MysqlProtocolManagementService();
        ReflectionTestUtils.setField(service, "protocolRepository", repository);
        ReflectionTestUtils.setField(service, "safetyPort", safetyChain);
        return service;
    }

    private static MysqlTemplate protocol(String sql, List<MysqlTemplateParameter> parameters) {
        return MysqlTemplate.builder()
                .id("900001")
                .version("1.0.0")
                .name("orderSummary")
                .description("Order summary")
                .datasourceRef("data-warehouse")
                .sql(sql)
                .parameters(parameters)
                .status(MysqlTemplateStatus.DISABLED)
                .policy(MysqlQueryPolicy.defaults())
                .build();
    }

    private static MysqlTemplateParameter parameter(String name) {
        return MysqlTemplateParameter.builder()
                .name(name)
                .type(MysqlParameterType.LONG)
                .required(true)
                .description("Order identifier")
                .build();
    }
}
