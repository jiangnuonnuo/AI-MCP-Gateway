package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.api.IAdminMysqlService;
import cn.bugstack.ai.api.IAdminService;
import cn.bugstack.ai.api.dto.MysqlAdminTestRequestDTO;
import cn.bugstack.ai.api.dto.MysqlDataSourceQueryDTO;
import cn.bugstack.ai.api.dto.MysqlDynamicBindingRequestDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlManageService;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlTemplateTestCase;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlBindingAdminView;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDynamicBindingAdminCommand;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;
import cn.bugstack.ai.trigger.http.AdminMysqlController;
import cn.bugstack.ai.trigger.http.AdminController;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MysqlAdminControllerContractTest {

    @Test
    void mapsStableDomainErrorWithoutReturningStackTrace() {
        IAdminMysqlManageService service = mock(IAdminMysqlManageService.class);
        doThrow(new MysqlDomainException("DATASOURCE_IN_USE", "data source is referenced by a template or binding"))
                .when(service).deleteDataSource("warehouse");
        AdminMysqlController controller = controller(service);

        Response<?> response = controller.deleteMysqlDataSource("warehouse");

        assertEquals("DATASOURCE_IN_USE", response.getCode());
        assertEquals("data source is referenced by a template or binding", response.getInfo());
    }

    @Test
    void normalizesPageBoundsBeforeCallingCase() {
        IAdminMysqlManageService service = mock(IAdminMysqlManageService.class);
        when(service.pageDataSources(any())).thenReturn(new cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminPage<>(java.util.List.of(), 0, 1, 20));
        AdminMysqlController controller = controller(service);

        controller.queryMysqlDataSourcePage(MysqlDataSourceQueryDTO.builder().page(0).rows(999).build());

        ArgumentCaptor<cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries.DataSource> captor = ArgumentCaptor.forClass(cn.bugstack.ai.domain.mysql.model.admin.MysqlAdminQueries.DataSource.class);
        verify(service).pageDataSources(captor.capture());
        assertEquals(1, captor.getValue().page());
        assertEquals(200, captor.getValue().rows());
    }

    @Test
    void isolatesMysqlHttpContractFromGenericAdminController() {
        assertTrue(IAdminMysqlService.class.isAssignableFrom(AdminMysqlController.class));
        assertFalse(Arrays.stream(IAdminService.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("Mysql")));
        assertFalse(Arrays.stream(AdminController.class.getDeclaredMethods()).anyMatch(method -> method.getName().contains("Mysql")));
    }

    @Test
    void datasourceTestDelegatesToConnectionHealthUseCase() {
        IAdminMysqlManageService service = mock(IAdminMysqlManageService.class);
        when(service.testDataSource("warehouse")).thenReturn(new MysqlDataSourceAdminView(
                1L, "warehouse", "Warehouse", "mysql", "jdbc:mysql://127.0.0.1:3306/warehouse",
                "reader", 0, true, null, null));
        AdminMysqlController controller = controller(service);

        Response<?> response = controller.testMysqlDataSource(new MysqlAdminTestRequestDTO(null, "warehouse", null));

        assertEquals("0000", response.getCode());
        verify(service).testDataSource("warehouse");
        verify(service, never()).findDataSource("warehouse");
    }

    @Test
    void templateTestReturnsExecutionReportAndForwardsOnlyParameters() {
        IAdminMysqlManageService service = mock(IAdminMysqlManageService.class);
        IAdminMysqlTemplateTestCase testCase = mock(IAdminMysqlTemplateTestCase.class);
        when(testCase.execute(eq("900002"), eq("1.0.0"), any())).thenReturn(MysqlTemplateTestReport.builder()
                .success(true).templateRef("900002").version("1.0.0").datasourceRef("warehouse")
                .queryId("q-1").requestParameters(java.util.Map.of("fromTime", "2026-09-01"))
                .stages(java.util.List.of()).durationMs(8).metrics(java.util.Map.of("rowCount", 1))
                .responseJson(java.util.Map.of("rows", java.util.List.of())).build());
        AdminMysqlController controller = controller(service);
        ReflectionTestUtils.setField(controller, "adminMysqlTemplateTestCase", testCase);

        MysqlAdminTestRequestDTO request = new MysqlAdminTestRequestDTO("900002", "client-override", "1.0.0",
                java.util.Map.of("fromTime", "2026-09-01"));
        Response<?> response = controller.testMysqlTemplate(request);

        assertEquals("0000", response.getCode());
        assertEquals("q-1", ((cn.bugstack.ai.api.dto.MysqlTemplateTestDTO) response.getData()).getQueryId());
        verify(testCase).execute(eq("900002"), eq("1.0.0"), eq(request.getParameters()));
    }

    @Test
    void dynamicBindingEndpointDoesNotRequireTemplateId() {
        IAdminMysqlManageService service = mock(IAdminMysqlManageService.class);
        when(service.saveDynamicBinding(any())).thenReturn(new MysqlBindingAdminView(
                9L, "gateway-1", 10L, "dynamicQuery", "function", "Dynamic query", "1.0.0",
                11L, "mysql", 0, null, null, "DYNAMIC_READONLY", "warehouse", 100, 1024L, 16, 3000));
        AdminMysqlController controller = controller(service);

        Response<?> response = controller.saveMysqlDynamicBinding(MysqlDynamicBindingRequestDTO.builder()
                .gatewayId("gateway-1").toolName("dynamicQuery").datasourceRef("warehouse")
                .maxRows(100).maxResultBytes(1024L).maxColumns(16).timeoutMs(3000).build());

        assertEquals("0000", response.getCode());
        ArgumentCaptor<MysqlDynamicBindingAdminCommand> captor = ArgumentCaptor.forClass(MysqlDynamicBindingAdminCommand.class);
        verify(service).saveDynamicBinding(captor.capture());
        assertEquals("warehouse", captor.getValue().datasourceRef());
    }

    private static AdminMysqlController controller(IAdminMysqlManageService service) {
        AdminMysqlController controller = new AdminMysqlController();
        ReflectionTestUtils.setField(controller, "adminMysqlManageService", service);
        return controller;
    }
}
