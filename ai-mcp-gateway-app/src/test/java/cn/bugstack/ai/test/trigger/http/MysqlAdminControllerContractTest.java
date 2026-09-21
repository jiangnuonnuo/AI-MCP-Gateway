package cn.bugstack.ai.test.trigger.http;

import cn.bugstack.ai.api.IAdminMysqlService;
import cn.bugstack.ai.api.IAdminService;
import cn.bugstack.ai.api.dto.MysqlAdminTestRequestDTO;
import cn.bugstack.ai.api.dto.MysqlDataSourceQueryDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlManageService;
import cn.bugstack.ai.domain.mysql.model.admin.MysqlDataSourceAdminView;
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

    private static AdminMysqlController controller(IAdminMysqlManageService service) {
        AdminMysqlController controller = new AdminMysqlController();
        ReflectionTestUtils.setField(controller, "adminMysqlManageService", service);
        return controller;
    }
}
