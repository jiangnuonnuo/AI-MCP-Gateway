package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.GatewayConfigRequestDTO;
import cn.bugstack.ai.api.dto.GatewayConfigResponseDTO;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.api.dto.GatewayConfigDTO;
import cn.bugstack.ai.api.dto.GatewayToolConfigDTO;
import java.util.List;

/**
 * 运营配置管理服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2026/3/24 08:00
 */
public interface IAdminService {

    Response<GatewayConfigResponseDTO> saveGatewayConfig(GatewayConfigRequestDTO.GatewayConfig requestDTO);

    Response<GatewayConfigResponseDTO> saveGatewayToolConfig(GatewayConfigRequestDTO.GatewayToolConfig requestDTO);

    Response<GatewayConfigResponseDTO> saveGatewayProtocol(GatewayConfigRequestDTO.GatewayProtocol requestDTO);

    Response<GatewayConfigResponseDTO> saveGatewayAuth(GatewayConfigRequestDTO.GatewayAuth requestDTO);

    Response<List<GatewayConfigDTO>> queryGatewayConfigList();

    Response<List<GatewayToolConfigDTO>> queryGatewayToolList();

    Response<GatewayConfigResponseDTO> deleteGatewayToolConfig(String gatewayId, Long toolId);

}
