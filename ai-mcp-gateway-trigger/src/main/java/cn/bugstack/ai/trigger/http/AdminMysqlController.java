package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAdminMysqlService;
import cn.bugstack.ai.api.dto.*;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.api.response.ResponsePage;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlManageService;
import cn.bugstack.ai.cases.admin.mysql.IAdminMysqlTemplateTestCase;
import cn.bugstack.ai.domain.mysql.model.admin.*;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlParameterType;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlExecutionStage;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateTestReport;
import cn.bugstack.ai.domain.mysql.model.valobj.MysqlTemplateParameter;
import cn.bugstack.ai.types.enums.ResponseCode;
import cn.bugstack.ai.types.exception.MysqlDomainException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL 管理控制器
 *
 * 负责数据源、SQL 模板和 Tool 绑定的 HTTP 请求适配、DTO 转换与安全错误响应。
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/admin/")
public class AdminMysqlController implements IAdminMysqlService {

    @Resource
    private IAdminMysqlManageService adminMysqlManageService;

    @Resource
    private IAdminMysqlTemplateTestCase adminMysqlTemplateTestCase;

    @RequestMapping(value = "query_mysql_datasource_page", method = RequestMethod.GET)
    @Override
    public ResponsePage<List<MysqlDataSourceDTO>> queryMysqlDataSourcePage(@ModelAttribute MysqlDataSourceQueryDTO queryDTO) {
        try {
            MysqlAdminPage<MysqlDataSourceAdminView> page = adminMysqlManageService.pageDataSources(
                    new MysqlAdminQueries.DataSource(queryDTO.getDatasourceRef(), queryDTO.getDatasourceName(), queryDTO.getStatus(),
                            queryDTO.getPage() == null ? 1 : queryDTO.getPage(), queryDTO.getRows() == null ? 20 : queryDTO.getRows()));
            return ResponsePage.<List<MysqlDataSourceDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(page.items().stream().map(this::toDTO).collect(Collectors.toList()))
                    .total(page.total())
                    .build();
        } catch (Exception e) {
            return mysqlPageError(e);
        }
    }

    @RequestMapping(value = "query_mysql_datasource_detail", method = RequestMethod.GET)
    @Override
    public Response<MysqlDataSourceDTO> queryMysqlDataSourceDetail(@RequestParam String datasourceRef) {
        try {
            return mysqlSuccess(adminMysqlManageService.findDataSource(datasourceRef));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "save_mysql_datasource", method = RequestMethod.POST)
    @Override
    public Response<MysqlDataSourceDTO> saveMysqlDataSource(@RequestBody MysqlDataSourceRequestDTO requestDTO) {
        try {
            return mysqlSuccess(adminMysqlManageService.saveDataSource(new MysqlDataSourceAdminCommand(
                    requestDTO.getId(), requestDTO.getDatasourceRef(), requestDTO.getDatasourceName(), requestDTO.getDatasourceType(),
                    requestDTO.getJdbcUrl(), requestDTO.getUsername(), requestDTO.getPassword(), requestDTO.getEncryptionKeyRef(), requestDTO.getStatus())));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "change_mysql_datasource_status", method = RequestMethod.POST)
    @Override
    public Response<MysqlDataSourceDTO> changeMysqlDataSourceStatus(@RequestParam String datasourceRef, @RequestParam Integer status) {
        try {
            return mysqlSuccess(adminMysqlManageService.changeDataSourceStatus(datasourceRef, status));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "delete_mysql_datasource", method = RequestMethod.POST)
    @Override
    public Response<MysqlDataSourceDTO> deleteMysqlDataSource(@RequestParam String datasourceRef) {
        try {
            adminMysqlManageService.deleteDataSource(datasourceRef);
            return mysqlSuccess(null);
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "test_mysql_datasource", method = RequestMethod.POST)
    @Override
    public Response<MysqlDataSourceDTO> testMysqlDataSource(@RequestBody MysqlAdminTestRequestDTO requestDTO) {
        try {
            String ref = requestDTO.getDatasourceRef() == null || requestDTO.getDatasourceRef().isBlank()
                    ? requestDTO.getId() : requestDTO.getDatasourceRef();
            return mysqlSuccess(adminMysqlManageService.testDataSource(ref));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "query_mysql_template_page", method = RequestMethod.GET)
    @Override
    public ResponsePage<List<MysqlTemplateDTO>> queryMysqlTemplatePage(@ModelAttribute MysqlTemplateQueryDTO queryDTO) {
        try {
            MysqlAdminPage<MysqlTemplateAdminView> page = adminMysqlManageService.pageTemplates(
                    new MysqlAdminQueries.Template(queryDTO.getProtocolId(), queryDTO.getName(), queryDTO.getDatasourceRef(), queryDTO.getStatus(),
                            queryDTO.getPage() == null ? 1 : queryDTO.getPage(), queryDTO.getRows() == null ? 20 : queryDTO.getRows()));
            return ResponsePage.<List<MysqlTemplateDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(page.items().stream().map(this::toDTO).collect(Collectors.toList()))
                    .total(page.total())
                    .build();
        } catch (Exception e) {
            return mysqlPageError(e);
        }
    }

    @RequestMapping(value = "query_mysql_template_detail", method = RequestMethod.GET)
    @Override
    public Response<MysqlTemplateDTO> queryMysqlTemplateDetail(@RequestParam Long protocolId, @RequestParam(required = false) String version) {
        try {
            return mysqlSuccess(adminMysqlManageService.findTemplate(protocolId, version));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "save_mysql_template", method = RequestMethod.POST)
    @Override
    public Response<MysqlTemplateDTO> saveMysqlTemplate(@RequestBody MysqlTemplateRequestDTO requestDTO) {
        try {
            List<MysqlTemplateParameter> parameters = requestDTO.getParameters() == null ? List.of()
                    : requestDTO.getParameters().stream()
                    .map(parameter -> new MysqlTemplateParameter(parameter.getName(), MysqlParameterType.valueOf(parameter.getType().toUpperCase()),
                            parameter.isRequired(), parameter.getDescription()))
                    .collect(Collectors.toList());
            return mysqlSuccess(adminMysqlManageService.saveTemplate(new MysqlTemplateAdminCommand(
                    requestDTO.getProtocolId(), requestDTO.getVersion(), requestDTO.getName(), requestDTO.getDescription(),
                    requestDTO.getDatasourceRef(), requestDTO.getSql(), parameters, requestDTO.getMaxRows(), requestDTO.getMaxResultBytes(),
                    requestDTO.getMaxColumns(), requestDTO.getTimeoutMs(), requestDTO.getStatus())));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "change_mysql_template_status", method = RequestMethod.POST)
    @Override
    public Response<MysqlTemplateDTO> changeMysqlTemplateStatus(@RequestParam Long protocolId,
                                                                 @RequestParam(required = false) String version,
                                                                 @RequestParam Integer status) {
        try {
            return mysqlSuccess(adminMysqlManageService.changeTemplateStatus(protocolId, version, status));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "delete_mysql_template", method = RequestMethod.POST)
    @Override
    public Response<MysqlTemplateDTO> deleteMysqlTemplate(@RequestParam Long protocolId, @RequestParam(required = false) String version) {
        try {
            adminMysqlManageService.deleteTemplate(protocolId, version);
            return mysqlSuccess(null);
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "test_mysql_template", method = RequestMethod.POST)
    @Override
    public Response<MysqlTemplateTestDTO> testMysqlTemplate(@RequestBody MysqlAdminTestRequestDTO requestDTO) {
        try {
            MysqlTemplateTestReport report = adminMysqlTemplateTestCase.execute(
                    requestDTO == null ? null : requestDTO.getId(),
                    requestDTO == null ? null : requestDTO.getVersion(),
                    requestDTO == null ? java.util.Map.of() : requestDTO.getParameters());
            MysqlTemplateTestDTO data = toDTO(report);
            if (!report.isSuccess()) {
                return Response.<MysqlTemplateTestDTO>builder()
                        .code(report.getErrorCode())
                        .info(report.getErrorMessage())
                        .data(data)
                        .build();
            }
            return mysqlSuccess(data);
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "query_mysql_binding_page", method = RequestMethod.GET)
    @Override
    public ResponsePage<List<MysqlBindingDTO>> queryMysqlBindingPage(@ModelAttribute MysqlBindingQueryDTO queryDTO) {
        try {
            MysqlAdminPage<MysqlBindingAdminView> page = adminMysqlManageService.pageBindings(
                    new MysqlAdminQueries.Binding(queryDTO.getGatewayId(), queryDTO.getToolName(), queryDTO.getProtocolId(), queryDTO.getStatus(),
                            queryDTO.getPage() == null ? 1 : queryDTO.getPage(), queryDTO.getRows() == null ? 20 : queryDTO.getRows()));
            return ResponsePage.<List<MysqlBindingDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(page.items().stream().map(this::toDTO).collect(Collectors.toList()))
                    .total(page.total())
                    .build();
        } catch (Exception e) {
            return mysqlPageError(e);
        }
    }

    @RequestMapping(value = "query_mysql_binding_detail", method = RequestMethod.GET)
    @Override
    public Response<MysqlBindingDTO> queryMysqlBindingDetail(@RequestParam Long id) {
        try {
            return mysqlSuccess(adminMysqlManageService.findBinding(id));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "save_mysql_binding", method = RequestMethod.POST)
    @Override
    public Response<MysqlBindingDTO> saveMysqlBinding(@RequestBody MysqlBindingRequestDTO requestDTO) {
        try {
            return mysqlSuccess(adminMysqlManageService.saveBinding(new MysqlBindingAdminCommand(
                    requestDTO.getId(), requestDTO.getGatewayId(), requestDTO.getToolId(), requestDTO.getToolName(),
                    requestDTO.getToolType(), requestDTO.getToolDescription(), requestDTO.getToolVersion(), requestDTO.getProtocolId(),
                    requestDTO.getProtocolType(), requestDTO.getStatus())));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "change_mysql_binding_status", method = RequestMethod.POST)
    @Override
    public Response<MysqlBindingDTO> changeMysqlBindingStatus(@RequestParam Long id, @RequestParam Integer status) {
        try {
            return mysqlSuccess(adminMysqlManageService.changeBindingStatus(id, status));
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    @RequestMapping(value = "delete_mysql_binding", method = RequestMethod.POST)
    @Override
    public Response<MysqlBindingDTO> deleteMysqlBinding(@RequestParam Long id) {
        try {
            adminMysqlManageService.deleteBinding(id);
            return mysqlSuccess(null);
        } catch (Exception e) {
            return mysqlError(e);
        }
    }

    private MysqlDataSourceDTO toDTO(MysqlDataSourceAdminView value) {
        return MysqlDataSourceDTO.builder()
                .id(value.id())
                .datasourceRef(value.datasourceRef())
                .datasourceName(value.datasourceName())
                .datasourceType(value.datasourceType())
                .jdbcUrlMasked(value.jdbcUrlMasked())
                .username(value.username())
                .status(value.status())
                .passwordConfigured(value.passwordConfigured())
                .createTime(value.createTime())
                .updateTime(value.updateTime())
                .build();
    }

    private MysqlTemplateDTO toDTO(MysqlTemplateAdminView value) {
        return MysqlTemplateDTO.builder()
                .protocolId(value.protocolId())
                .version(value.version())
                .name(value.name())
                .description(value.description())
                .datasourceRef(value.datasourceRef())
                .sql(value.sql())
                .parameters(value.parameters().stream().map(parameter -> MysqlTemplateParameterDTO.builder()
                        .name(parameter.getName())
                        .type(parameter.getType().name())
                        .required(parameter.isRequired())
                        .description(parameter.getDescription())
                        .build()).collect(Collectors.toList()))
                .maxRows(value.maxRows())
                .maxResultBytes(value.maxResultBytes())
                .maxColumns(value.maxColumns())
                .timeoutMs(value.timeoutMs())
                .status(value.status())
                .createTime(value.createTime())
                .updateTime(value.updateTime())
                .build();
    }

    private MysqlBindingDTO toDTO(MysqlBindingAdminView value) {
        return MysqlBindingDTO.builder()
                .id(value.id())
                .gatewayId(value.gatewayId())
                .toolId(value.toolId())
                .toolName(value.toolName())
                .toolType(value.toolType())
                .toolDescription(value.toolDescription())
                .toolVersion(value.toolVersion())
                .protocolId(value.protocolId())
                .protocolType(value.protocolType())
                .status(value.status())
                .createTime(value.createTime())
                .updateTime(value.updateTime())
                .build();
    }

    private MysqlTemplateTestDTO toDTO(MysqlTemplateTestReport value) {
        return MysqlTemplateTestDTO.builder()
                .success(value.isSuccess())
                .templateRef(value.getTemplateRef())
                .version(value.getVersion())
                .datasourceRef(value.getDatasourceRef())
                .queryId(value.getQueryId())
                .requestParameters(value.getRequestParameters())
                .requestJson(value.getRequestParameters())
                .stages(value.getStages() == null ? List.of() : value.getStages().stream()
                        .map(this::toDTO).collect(Collectors.toList()))
                .durationMs(value.getDurationMs())
                .metrics(value.getMetrics())
                .responseJson(value.getResponseJson())
                .errorCode(value.getErrorCode())
                .errorMessage(value.getErrorMessage())
                .failedStage(value.getFailedStage())
                .build();
    }

    private MysqlExecutionStageDTO toDTO(MysqlExecutionStage value) {
        return MysqlExecutionStageDTO.builder()
                .name(value.getName())
                .label(value.getLabel())
                .status(value.getStatus() == null ? null : value.getStatus().name())
                .durationMs(value.getDurationMs())
                .errorCode(value.getErrorCode())
                .errorMessage(value.getErrorMessage())
                .build();
    }

    private <T> Response<T> mysqlSuccess(Object value) {
        @SuppressWarnings("unchecked") T data = (T) value;
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> mysqlError(Exception exception) {
        String code = errorCode(exception);
        log.warn("MySQL 管理请求失败 errorCode: {}, errorType: {}", code, exception.getClass().getSimpleName());
        return Response.<T>builder()
                .code(code)
                .info(errorInfo(exception))
                .build();
    }

    private <T> ResponsePage<List<T>> mysqlPageError(Exception exception) {
        String code = errorCode(exception);
        log.warn("MySQL 管理分页请求失败 errorCode: {}, errorType: {}", code, exception.getClass().getSimpleName());
        return ResponsePage.<List<T>>builder()
                .code(code)
                .info(errorInfo(exception))
                .data(List.of())
                .total(0L)
                .build();
    }

    private String errorCode(Exception exception) {
        return exception instanceof MysqlDomainException domain ? domain.getCode() : ResponseCode.UN_ERROR.getCode();
    }

    private String errorInfo(Exception exception) {
        String info = exception instanceof MysqlDomainException domain ? domain.getMessage() : ResponseCode.UN_ERROR.getInfo();
        return info == null || info.isBlank() ? ResponseCode.UN_ERROR.getInfo() : info;
    }

}
