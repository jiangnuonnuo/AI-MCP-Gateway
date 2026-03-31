package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.IAdminService;
import cn.bugstack.ai.api.dto.GatewayConfigDTO;
import cn.bugstack.ai.api.dto.GatewayConfigRequestDTO;
import cn.bugstack.ai.api.dto.GatewayConfigResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.cases.admin.IAdminAuthService;
import cn.bugstack.ai.cases.admin.IAdminGatewayService;
import cn.bugstack.ai.cases.admin.IAdminManageService;
import cn.bugstack.ai.cases.admin.IAdminProtocolService;
import cn.bugstack.ai.domain.admin.model.entity.GatewayProtocolConfigEntity;
import cn.bugstack.ai.api.dto.GatewayAuthDTO;
import cn.bugstack.ai.api.dto.GatewayProtocolDTO;
import cn.bugstack.ai.api.dto.GatewayToolConfigDTO;
import cn.bugstack.ai.domain.admin.model.entity.GatewayAuthConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayConfigEntity;
import cn.bugstack.ai.domain.admin.model.entity.GatewayToolConfigEntity;
import cn.bugstack.ai.domain.auth.model.entity.RegisterCommandEntity;
import cn.bugstack.ai.domain.gateway.model.entity.GatewayConfigCommandEntity;
import cn.bugstack.ai.domain.gateway.model.entity.GatewayToolConfigCommandEntity;
import cn.bugstack.ai.domain.gateway.model.valobj.GatewayConfigVO;
import cn.bugstack.ai.domain.gateway.model.valobj.GatewayToolConfigVO;
import cn.bugstack.ai.domain.protocol.model.entity.StorageCommandEntity;
import cn.bugstack.ai.domain.protocol.model.valobj.http.HTTPProtocolVO;
import cn.bugstack.ai.types.enums.GatewayEnum;
import cn.bugstack.ai.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 运营配置管理服务
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/24 08:00
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RequestMapping("/admin/")
public class AdminController implements IAdminService {

    @Resource
    private IAdminGatewayService adminGatewayService;
    @Resource
    private IAdminAuthService adminAuthService;
    @Resource
    private IAdminProtocolService adminProtocolService;
    @Resource
    private IAdminManageService adminManageService;

    @RequestMapping(value = "save_gateway_config", method = RequestMethod.POST)
    @Override
    public Response<GatewayConfigResponseDTO> saveGatewayConfig(@RequestBody GatewayConfigRequestDTO.GatewayConfig requestDTO) {
        try {
            log.info("保存网关配置开始 gatewayId: {}", requestDTO.getGatewayId());
            GatewayConfigCommandEntity commandEntity = GatewayConfigCommandEntity.builder()
                    .gatewayConfigVO(GatewayConfigVO.builder()
                            .gatewayId(requestDTO.getGatewayId())
                            .gatewayName(requestDTO.getGatewayName())
                            .gatewayDesc(requestDTO.getGatewayDesc())
                            .version(requestDTO.getVersion())
                            .auth(GatewayEnum.GatewayAuthStatusEnum.getByCode(requestDTO.getAuth()))
                            .status(GatewayEnum.GatewayStatus.get(requestDTO.getStatus()))
                            .build())
                    .build();
            adminGatewayService.saveGatewayConfig(commandEntity);
            log.info("保存网关配置完成 gatewayId: {}", requestDTO.getGatewayId());
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("保存网关配置失败 gatewayId: {}", requestDTO.getGatewayId(), e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "save_gateway_tool_config", method = RequestMethod.POST)
    @Override
    public Response<GatewayConfigResponseDTO> saveGatewayToolConfig(@RequestBody GatewayConfigRequestDTO.GatewayToolConfig requestDTO) {
        try {
            log.info("保存网关工具配置开始 gatewayId: {}", requestDTO.getGatewayId());
            GatewayToolConfigCommandEntity commandEntity = GatewayToolConfigCommandEntity.builder()
                    .gatewayToolConfigVO(GatewayToolConfigVO.builder()
                            .gatewayId(requestDTO.getGatewayId())
                            .toolId(requestDTO.getToolId())
                            .toolName(requestDTO.getToolName())
                            .toolType(requestDTO.getToolType())
                            .toolDescription(requestDTO.getToolDescription())
                            .toolVersion(requestDTO.getToolVersion())
                            .protocolId(requestDTO.getProtocolId())
                            .protocolType(requestDTO.getProtocolType())
                            .build())
                    .build();
            adminGatewayService.saveGatewayToolConfig(commandEntity);
            log.info("保存网关工具配置完成 gatewayId: {}", requestDTO.getGatewayId());
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("保存网关工具配置失败 gatewayId: {}", requestDTO.getGatewayId(), e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "save_gateway_protocol", method = RequestMethod.POST)
    @Override
    public Response<GatewayConfigResponseDTO> saveGatewayProtocol(@RequestBody GatewayConfigRequestDTO.GatewayProtocol requestDTO) {
        try {
            log.info("保存网关协议配置开始");
            StorageCommandEntity commandEntity = new StorageCommandEntity();
            if (requestDTO.getHttpProtocols() != null) {
                commandEntity.setHttpProtocolVOS(requestDTO.getHttpProtocols().stream().map(p -> {
                    HTTPProtocolVO vo = new HTTPProtocolVO();
                    vo.setProtocolId(p.getProtocolId());
                    vo.setHttpUrl(p.getHttpUrl());
                    vo.setHttpHeaders(p.getHttpHeaders());
                    vo.setHttpMethod(p.getHttpMethod());
                    vo.setTimeout(p.getTimeout());
                    if (p.getMappings() != null) {
                        vo.setMappings(p.getMappings().stream().map(m -> HTTPProtocolVO.ProtocolMapping.builder()
                                .mappingType(m.getMappingType())
                                .parentPath(m.getParentPath())
                                .fieldName(m.getFieldName())
                                .mcpPath(m.getMcpPath())
                                .mcpType(m.getMcpType())
                                .mcpDesc(m.getMcpDesc())
                                .isRequired(m.getIsRequired())
                                .sortOrder(m.getSortOrder())
                                .build()).collect(Collectors.toList()));
                    }
                    return vo;
                }).collect(Collectors.toList()));
            }
            adminProtocolService.saveGatewayProtocol(commandEntity);
            log.info("保存网关协议配置完成");
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("保存网关协议配置失败", e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "save_gateway_auth", method = RequestMethod.POST)
    @Override
    public Response<GatewayConfigResponseDTO> saveGatewayAuth(@RequestBody GatewayConfigRequestDTO.GatewayAuth requestDTO) {
        try {
            log.info("保存网关auth认证开始 gatewayId: {}", requestDTO.getGatewayId());
            RegisterCommandEntity commandEntity = RegisterCommandEntity.builder()
                    .gatewayId(requestDTO.getGatewayId())
                    .rateLimit(requestDTO.getRateLimit())
                    .expireTime(requestDTO.getExpireTime())
                    .build();
            adminAuthService.saveGatewayAuth(commandEntity);
            log.info("保存网关auth认证完成 gatewayId: {}", requestDTO.getGatewayId());
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("保存网关auth认证失败 gatewayId: {}", requestDTO.getGatewayId(), e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_gateway_config_list", method = RequestMethod.GET)
    @Override
    public Response<List<GatewayConfigDTO>> queryGatewayConfigList() {
        try {
            log.info("查询网关配置列表开始");
            List<GatewayConfigEntity> entities = adminManageService.queryGatewayConfigList();
            List<GatewayConfigDTO> dtoList = entities.stream().map(e -> GatewayConfigDTO.builder()
                    .gatewayId(e.getGatewayId())
                    .gatewayName(e.getGatewayName())
                    .gatewayDesc(e.getGatewayDesc())
                    .version(e.getVersion())
                    .auth(e.getAuth())
                    .status(e.getStatus())
                    .build()).collect(Collectors.toList());
            log.info("查询网关配置列表完成 count: {}", dtoList.size());
            return Response.<List<GatewayConfigDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dtoList)
                    .build();
        } catch (Exception e) {
            log.error("查询网关配置列表失败", e);
            return Response.<List<GatewayConfigDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_gateway_tool_list", method = RequestMethod.GET)
    @Override
    public Response<List<GatewayToolConfigDTO>> queryGatewayToolList() {
        try {
            log.info("查询网关工具列表开始");
            List<GatewayToolConfigEntity> entities = adminManageService.queryGatewayToolList();
            List<GatewayToolConfigDTO> dtoList = entities.stream().map(e -> GatewayToolConfigDTO.builder()
                    .gatewayId(e.getGatewayId())
                    .toolId(e.getToolId())
                    .toolName(e.getToolName())
                    .toolType(e.getToolType())
                    .toolDescription(e.getToolDescription())
                    .toolVersion(e.getToolVersion())
                    .protocolId(e.getProtocolId())
                    .protocolType(e.getProtocolType())
                    .build()).collect(Collectors.toList());
            log.info("查询网关工具列表完成 count: {}", dtoList.size());
            return Response.<List<GatewayToolConfigDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dtoList)
                    .build();
        } catch (Exception e) {
            log.error("查询网关工具列表失败", e);
            return Response.<List<GatewayToolConfigDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "delete_gateway_tool_config", method = RequestMethod.POST)
    @Override
    public Response<GatewayConfigResponseDTO> deleteGatewayToolConfig(@RequestParam String gatewayId, @RequestParam Long toolId) {
        try {
            log.info("删除网关工具配置开始 gatewayId: {} toolId: {}", gatewayId, toolId);
            adminGatewayService.deleteGatewayToolConfig(toolId);
            log.info("删除网关工具配置完成 gatewayId: {} toolId: {}", gatewayId, toolId);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("删除网关工具配置失败 gatewayId: {} toolId: {}", gatewayId, toolId, e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_gateway_protocol_list", method = RequestMethod.GET)
    public Response<List<GatewayProtocolDTO>> queryGatewayProtocolList() {
        try {
            log.info("查询网关协议列表开始");
            List<GatewayProtocolConfigEntity> entities = adminManageService.queryGatewayProtocolList();
            List<GatewayProtocolDTO> dtoList = entities.stream().map(e -> GatewayProtocolDTO.builder()
                    .protocolId(e.getProtocolId())
                    .httpUrl(e.getHttpUrl())
                    .httpMethod(e.getHttpMethod())
                    .httpHeaders(e.getHttpHeaders())
                    .timeout(e.getTimeout())
                    .mappings(e.getMappings() == null ? null : e.getMappings().stream().map(m -> GatewayProtocolDTO.ProtocolMappingDTO.builder()
                            .mappingType(m.getMappingType())
                            .parentPath(m.getParentPath())
                            .fieldName(m.getFieldName())
                            .mcpPath(m.getMcpPath())
                            .mcpType(m.getMcpType())
                            .mcpDesc(m.getMcpDesc())
                            .isRequired(m.getIsRequired())
                            .sortOrder(m.getSortOrder())
                            .build()).collect(Collectors.toList()))
                    .build()).collect(Collectors.toList());
            log.info("查询网关协议列表完成 count: {}", dtoList.size());
            return Response.<List<GatewayProtocolDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dtoList)
                    .build();
        } catch (Exception e) {
            log.error("查询网关协议列表失败", e);
            return Response.<List<GatewayProtocolDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "delete_gateway_protocol", method = RequestMethod.POST)
    public Response<GatewayConfigResponseDTO> deleteGatewayProtocol(@RequestParam Long protocolId) {
        try {
            log.info("删除网关协议配置开始 protocolId: {}", protocolId);
            adminProtocolService.deleteGatewayProtocol(protocolId);
            log.info("删除网关协议配置完成 protocolId: {}", protocolId);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("删除网关协议配置失败 protocolId: {}", protocolId, e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_gateway_auth_list", method = RequestMethod.GET)
    public Response<List<GatewayAuthDTO>> queryGatewayAuthList() {
        try {
            log.info("查询网关认证列表开始");
            List<GatewayAuthConfigEntity> entities = adminManageService.queryGatewayAuthList();
            List<GatewayAuthDTO> dtoList = entities.stream().map(e -> GatewayAuthDTO.builder()
                    .gatewayId(e.getGatewayId())
                    .apiKey(e.getApiKey())
                    .rateLimit(e.getRateLimit())
                    .expireTime(e.getExpireTime())
                    .build()).collect(Collectors.toList());
            log.info("查询网关认证列表完成 count: {}", dtoList.size());
            return Response.<List<GatewayAuthDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dtoList)
                    .build();
        } catch (Exception e) {
            log.error("查询网关认证列表失败", e);
            return Response.<List<GatewayAuthDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "delete_gateway_auth", method = RequestMethod.POST)
    public Response<GatewayConfigResponseDTO> deleteGatewayAuth(@RequestParam String gatewayId) {
        try {
            log.info("删除网关认证配置开始 gatewayId: {}", gatewayId);
            adminAuthService.deleteGatewayAuth(gatewayId);
            log.info("删除网关认证配置完成 gatewayId: {}", gatewayId);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(GatewayConfigResponseDTO.builder().success(true).build())
                    .build();
        } catch (Exception e) {
            log.error("删除网关认证配置失败 gatewayId: {}", gatewayId, e);
            return Response.<GatewayConfigResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
