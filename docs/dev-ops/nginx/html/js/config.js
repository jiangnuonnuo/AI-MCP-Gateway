// js/config.js

const API_BASE_URL = "http://127.0.0.1:8779/api-gateway"; // 替换为实际的服务端IP和端口
const MYSQL_DATASOURCE_KEY_REF = "env:MCP_MYSQL_DATASOURCE_KEY";

const API_ENDPOINTS = {
    // 获取网关列表
    GET_GATEWAY_LIST: `${API_BASE_URL}/admin/query_gateway_config_list`,
    GET_GATEWAY_PAGE: `${API_BASE_URL}/admin/query_gateway_config_page`,
    // 保存网关配置
    SAVE_GATEWAY_CONFIG: `${API_BASE_URL}/admin/save_gateway_config`,
    // 保存网关工具配置
    SAVE_GATEWAY_TOOL_CONFIG: `${API_BASE_URL}/admin/save_gateway_tool_config`,
    // 获取网关协议列表
    GET_GATEWAY_PROTOCOL_LIST: `${API_BASE_URL}/admin/query_gateway_protocol_list`,
    GET_GATEWAY_PROTOCOL_PAGE: `${API_BASE_URL}/admin/query_gateway_protocol_page`,
    // 根据网关ID获取协议列表
    GET_GATEWAY_PROTOCOL_LIST_BY_ID: `${API_BASE_URL}/admin/query_gateway_protocol_list_by_gateway_id`,
    // 保存网关协议配置
    SAVE_GATEWAY_PROTOCOL: `${API_BASE_URL}/admin/save_gateway_protocol`,
    // 导入网关协议配置
    IMPORT_GATEWAY_PROTOCOL: `${API_BASE_URL}/admin/import_gateway_protocol`,
    // 解析网关协议配置
    ANALYSIS_PROTOCOL: `${API_BASE_URL}/admin/analysis_protocol`,
    // 删除网关协议配置
    DELETE_GATEWAY_PROTOCOL: `${API_BASE_URL}/admin/delete_gateway_protocol`,
    // 获取网关认证列表
    GET_GATEWAY_AUTH_LIST: `${API_BASE_URL}/admin/query_gateway_auth_list`,
    GET_GATEWAY_AUTH_PAGE: `${API_BASE_URL}/admin/query_gateway_auth_page`,
    // 根据网关ID精确查询认证 Key 列表
    GET_GATEWAY_AUTH_LIST_BY_ID: `${API_BASE_URL}/admin/query_gateway_auth_list_by_gateway_id`,
    // 根据网关ID精确查询认证 Key 列表
    GET_GATEWAY_AUTH_LIST_BY_ID: `${API_BASE_URL}/admin/query_gateway_auth_list_by_gateway_id`,
    // 保存网关认证配置
    SAVE_GATEWAY_AUTH: `${API_BASE_URL}/admin/save_gateway_auth`,
    // 删除网关认证配置
    DELETE_GATEWAY_AUTH: `${API_BASE_URL}/admin/delete_gateway_auth`,
    // 获取网关工具列表
    GET_GATEWAY_TOOL_LIST: `${API_BASE_URL}/admin/query_gateway_tool_list`,
    GET_GATEWAY_TOOL_PAGE: `${API_BASE_URL}/admin/query_gateway_tool_page`,
    // 根据网关ID获取工具列表
    GET_GATEWAY_TOOL_LIST_BY_ID: `${API_BASE_URL}/admin/query_gateway_tool_list_by_gateway_id`,
    // 删除网关工具配置
    DELETE_GATEWAY_TOOL: `${API_BASE_URL}/admin/delete_gateway_tool_config`,
    // 测试调用网关 LLM 服务
    TEST_CALL_GATEWAY: `${API_BASE_URL}/admin/test_call_gateway`,

    // MySQL 管理工作台：所有页面只通过 REST 管理接口访问控制面
    GET_MYSQL_DATASOURCE_PAGE: `${API_BASE_URL}/admin/query_mysql_datasource_page`,
    GET_MYSQL_DATASOURCE_DETAIL: `${API_BASE_URL}/admin/query_mysql_datasource_detail`,
    SAVE_MYSQL_DATASOURCE: `${API_BASE_URL}/admin/save_mysql_datasource`,
    CHANGE_MYSQL_DATASOURCE_STATUS: `${API_BASE_URL}/admin/change_mysql_datasource_status`,
    DELETE_MYSQL_DATASOURCE: `${API_BASE_URL}/admin/delete_mysql_datasource`,
    TEST_MYSQL_DATASOURCE: `${API_BASE_URL}/admin/test_mysql_datasource`,
    GET_MYSQL_TEMPLATE_PAGE: `${API_BASE_URL}/admin/query_mysql_template_page`,
    GET_MYSQL_TEMPLATE_DETAIL: `${API_BASE_URL}/admin/query_mysql_template_detail`,
    SAVE_MYSQL_TEMPLATE: `${API_BASE_URL}/admin/save_mysql_template`,
    CHANGE_MYSQL_TEMPLATE_STATUS: `${API_BASE_URL}/admin/change_mysql_template_status`,
    DELETE_MYSQL_TEMPLATE: `${API_BASE_URL}/admin/delete_mysql_template`,
    TEST_MYSQL_TEMPLATE: `${API_BASE_URL}/admin/test_mysql_template`,
    GET_MYSQL_BINDING_PAGE: `${API_BASE_URL}/admin/query_mysql_binding_page`,
    GET_MYSQL_BINDING_DETAIL: `${API_BASE_URL}/admin/query_mysql_binding_detail`,
    SAVE_MYSQL_BINDING: `${API_BASE_URL}/admin/save_mysql_binding`,
    CHANGE_MYSQL_BINDING_STATUS: `${API_BASE_URL}/admin/change_mysql_binding_status`,
    DELETE_MYSQL_BINDING: `${API_BASE_URL}/admin/delete_mysql_binding`,
    GET_GATEWAY_OPTIONS: `${API_BASE_URL}/admin/query_gateway_config_list`
};

// 模拟登录账号
const MOCK_ACCOUNT = {
    username: "admin",
    password: "password123"
};
