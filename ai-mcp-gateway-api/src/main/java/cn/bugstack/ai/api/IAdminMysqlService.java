package cn.bugstack.ai.api;

import cn.bugstack.ai.api.dto.*;
import cn.bugstack.ai.api.response.Response;
import cn.bugstack.ai.api.response.ResponsePage;

import java.util.List;

/**
 * MySQL 管理服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public interface IAdminMysqlService {

    ResponsePage<List<MysqlDataSourceDTO>> queryMysqlDataSourcePage(MysqlDataSourceQueryDTO queryDTO);

    Response<MysqlDataSourceDTO> queryMysqlDataSourceDetail(String datasourceRef);

    Response<MysqlDataSourceDTO> saveMysqlDataSource(MysqlDataSourceRequestDTO requestDTO);

    Response<MysqlDataSourceDTO> changeMysqlDataSourceStatus(String datasourceRef, Integer status);

    Response<MysqlDataSourceDTO> deleteMysqlDataSource(String datasourceRef);

    Response<MysqlDataSourceDTO> testMysqlDataSource(MysqlAdminTestRequestDTO requestDTO);

    ResponsePage<List<MysqlTemplateDTO>> queryMysqlTemplatePage(MysqlTemplateQueryDTO queryDTO);

    Response<MysqlTemplateDTO> queryMysqlTemplateDetail(Long protocolId, String version);

    Response<MysqlTemplateDTO> saveMysqlTemplate(MysqlTemplateRequestDTO requestDTO);

    Response<MysqlTemplateDTO> changeMysqlTemplateStatus(Long protocolId, String version, Integer status);

    Response<MysqlTemplateDTO> deleteMysqlTemplate(Long protocolId, String version);

    Response<MysqlTemplateTestDTO> testMysqlTemplate(MysqlAdminTestRequestDTO requestDTO);

    Response<List<ToolTestToolDTO>> queryTestCenterTools(String gatewayId);

    Response<ToolManualTestDTO> testCenterToolCall(ToolManualTestRequestDTO requestDTO);

    ResponsePage<List<MysqlBindingDTO>> queryMysqlBindingPage(MysqlBindingQueryDTO queryDTO);

    Response<MysqlBindingDTO> queryMysqlBindingDetail(Long id);

    Response<MysqlBindingDTO> saveMysqlBinding(MysqlBindingRequestDTO requestDTO);

    Response<MysqlBindingDTO> changeMysqlBindingStatus(Long id, Integer status);

    Response<MysqlBindingDTO> deleteMysqlBinding(Long id);

}
