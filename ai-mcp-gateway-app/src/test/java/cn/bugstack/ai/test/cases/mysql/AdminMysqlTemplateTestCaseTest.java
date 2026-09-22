package cn.bugstack.ai.test.cases.mysql;

import cn.bugstack.ai.cases.admin.mysql.AdminMysqlTemplateTestCase;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;
import cn.bugstack.ai.domain.mysql.service.MysqlTemplateQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMysqlTemplateTestCaseTest {

    @Test
    void delegatesOnlyTemplateReferenceVersionAndParameters() {
        MysqlTemplateQueryService queryService = mock(MysqlTemplateQueryService.class);
        Map<String, Object> parameters = Map.of("fromTime", "2026-09-01");
        MysqlTemplateTestReport report = MysqlTemplateTestReport.builder().success(true).queryId("q-1").build();
        when(queryService.executeWithReport(eq("900002"), eq("1.0.0"), same(parameters), eq(null))).thenReturn(report);
        AdminMysqlTemplateTestCase testCase = new AdminMysqlTemplateTestCase();
        ReflectionTestUtils.setField(testCase, "queryService", queryService);

        assertTrue(testCase.execute("900002", "1.0.0", parameters).isSuccess());
        verify(queryService).executeWithReport(eq("900002"), eq("1.0.0"), same(parameters), eq(null));
    }

    @Test
    void preservesDomainFailureReport() {
        MysqlTemplateQueryService queryService = mock(MysqlTemplateQueryService.class);
        MysqlTemplateTestReport report = MysqlTemplateTestReport.builder().success(false)
                .errorCode("SQL_PARAMETER_ERROR").failedStage("PARAMETER_VALIDATION").build();
        when(queryService.executeWithReport(eq("900002"), eq("1.0.0"), eq(Map.of()), eq(null))).thenReturn(report);
        AdminMysqlTemplateTestCase testCase = new AdminMysqlTemplateTestCase();
        ReflectionTestUtils.setField(testCase, "queryService", queryService);

        assertFalse(testCase.execute("900002", "1.0.0", Map.of()).isSuccess());
    }
}
