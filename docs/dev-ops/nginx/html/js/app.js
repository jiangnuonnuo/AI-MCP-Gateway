// js/app.js
$(document).ready(function() {
    // 检查登录状态
    if(localStorage.getItem('mcp_admin_logged_in') !== 'true') {
        window.location.href = 'index.html';
        return;
    }

    // 初始化页面显示 API 地址
    $('#display-api-url').text(API_BASE_URL);

    // 退出登录
    $('#logoutBtn').on('click', function(e) {
        e.preventDefault();
        localStorage.removeItem('mcp_admin_logged_in');
        window.location.href = 'index.html';
    });

    // 侧边栏导航切换
    $('.nav-link[data-target]').on('click', function(e) {
        e.preventDefault();
        
        // 更新激活状态
        $('.nav-link').removeClass('active');
        $(this).addClass('active');
        
        // 切换内容区域
        const targetId = $(this).data('target');
        $('.content-section').removeClass('active');
        $('#' + targetId).addClass('active');

        // 如果是网关列表页面，自动加载数据
        if (targetId === 'gateway-list') {
            loadGatewayList();
        }
    });

    // 刷新列表按钮
    $('#refreshGatewayList').on('click', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    // 显示 Toast 通知
    function showToast(message, isSuccess = true) {
        const toastEl = $('#liveToast');
        const iconHtml = isSuccess ? '<i class="bi bi-check-circle-fill"></i>' : '<i class="bi bi-exclamation-triangle-fill"></i>';
        
        $('#toastMessage').html(`${iconHtml} <span>${message}</span>`);
        
        if(isSuccess) {
            toastEl.removeClass('bg-danger').addClass('bg-success');
        } else {
            toastEl.removeClass('bg-success').addClass('bg-danger');
        }
        
        const toast = new bootstrap.Toast(toastEl[0]);
        toast.show();
    }

    // 表单提交通用处理
    function handleFormSubmit(formId, endpoint, dataProcessor) {
        $('#' + formId).on('submit', function(e) {
            e.preventDefault();
            
            const $btn = $(this).find('button[type="submit"]');
            const originalHtml = $btn.html();
            $btn.html('<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>保存中...').prop('disabled', true);
            
            // 序列化表单数据为对象
            const formDataArray = $(this).serializeArray();
            const rawData = {};
            $.map(formDataArray, function(n, i){
                rawData[n['name']] = n['value'];
            });
            
            // 数据处理（如果需要转换类型或结构）
            let requestData;
            try {
                requestData = dataProcessor ? dataProcessor(rawData) : rawData;
            } catch(error) {
                showToast('数据格式错误: ' + error.message, false);
                $btn.html(originalHtml).prop('disabled', false);
                return;
            }

            // 发送请求
            $.ajax({
                url: endpoint,
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(requestData),
                success: function(response) {
                    if(response && response.code === '0000') {
                        showToast('配置保存成功！');
                        // $('#' + formId)[0].reset(); // 可选：是否提交后清空表单
                    } else {
                        showToast('保存失败：' + (response.info || '未知错误'), false);
                    }
                },
                error: function(xhr, status, error) {
                    showToast('请求失败：' + error, false);
                },
                complete: function() {
                    $btn.html(originalHtml).prop('disabled', false);
                }
            });
        });
    }

    // 1. 保存网关基础配置
    handleFormSubmit('form-gateway-config', API_ENDPOINTS.SAVE_GATEWAY_CONFIG, function(data) {
        return {
            gatewayId: data.gatewayId,
            gatewayName: data.gatewayName,
            gatewayDesc: data.gatewayDesc,
            version: data.version,
            auth: parseInt(data.auth),
            status: parseInt(data.status)
        };
    });

    // 2. 保存网关工具配置
    handleFormSubmit('form-gateway-tool', API_ENDPOINTS.SAVE_GATEWAY_TOOL_CONFIG, function(data) {
        return {
            gatewayId: data.gatewayId,
            toolId: data.toolId,
            toolName: data.toolName,
            toolType: data.toolType,
            toolDescription: data.toolDescription,
            toolVersion: data.toolVersion,
            protocolId: data.protocolId ? parseInt(data.protocolId) : null,
            protocolType: data.protocolType
        };
    });

    // 3. 保存网关协议配置
    handleFormSubmit('form-gateway-protocol', API_ENDPOINTS.SAVE_GATEWAY_PROTOCOL, function(data) {
        let mappings = null;
        if(data.mappingsJson && data.mappingsJson.trim() !== '') {
            try {
                mappings = JSON.parse(data.mappingsJson);
            } catch(e) {
                throw new Error("Mappings JSON 格式不正确");
            }
        }
        
        return {
            httpProtocols: [
                {
                    protocolId: parseInt(data.protocolId),
                    httpUrl: data.httpUrl,
                    httpMethod: data.httpMethod,
                    timeout: parseInt(data.timeout) || 5000,
                    httpHeaders: data.httpHeaders,
                    mappings: mappings
                }
            ]
        };
    });

    // 4. 保存网关认证配置
    handleFormSubmit('form-gateway-auth', API_ENDPOINTS.SAVE_GATEWAY_AUTH, function(data) {
        return {
            gatewayId: data.gatewayId,
            rateLimit: parseInt(data.rateLimit),
            expireTime: parseInt(data.expireTime)
        };
    });

    // 获取网关列表数据
    function loadGatewayList(callback) {
        const tbody = $('#gatewayTableBody');
        if(!callback) {
            tbody.html('<tr><td colspan="6" class="text-center text-muted py-4"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>加载中...</td></tr>');
        }
        
        $.ajax({
            url: API_ENDPOINTS.GET_GATEWAY_LIST,
            type: 'GET',
            success: function(response) {
                if(response && response.code === '0000' && response.data) {
                    const list = response.data;
                    
                    // 更新控制台统计
                    $('#stat-gateway-count').text(list.length);
                    
                    if(list.length === 0) {
                        tbody.html('<tr><td colspan="6" class="text-center text-muted py-4"><i class="bi bi-inbox fs-4 d-block mb-2"></i>暂无网关数据</td></tr>');
                    } else {
                        let html = '';
                        list.forEach(function(item) {
                            const authLabel = item.auth === 1 ? '<span class="badge bg-success bg-opacity-10 text-success border border-success">启用</span>' : '<span class="badge bg-secondary bg-opacity-10 text-secondary border border-secondary">禁用</span>';
                            const statusLabel = item.status === 1 ? '<span class="badge bg-primary bg-opacity-10 text-primary border border-primary">强校验</span>' : '<span class="badge bg-warning bg-opacity-10 text-warning border border-warning">不校验</span>';
                            
                            html += `
                                <tr>
                                    <td><code>${item.gatewayId || '-'}</code></td>
                                    <td class="fw-bold">${item.gatewayName || '-'}</td>
                                    <td><span class="text-truncate d-inline-block text-muted" style="max-width: 200px;" title="${item.gatewayDesc || ''}">${item.gatewayDesc || '-'}</span></td>
                                    <td><span class="badge bg-light text-dark">${item.version || '-'}</span></td>
                                    <td>${authLabel}</td>
                                    <td>${statusLabel}</td>
                                </tr>
                            `;
                        });
                        tbody.html(html);
                    }
                } else {
                    tbody.html(`<tr><td colspan="6" class="text-center text-danger py-4"><i class="bi bi-exclamation-triangle me-2"></i>加载失败: ${response.info || '未知错误'}</td></tr>`);
                }
            },
            error: function() {
                tbody.html('<tr><td colspan="6" class="text-center text-danger py-4"><i class="bi bi-wifi-off me-2"></i>网络请求失败，请检查服务是否启动</td></tr>');
            },
            complete: function() {
                if(callback) callback();
            }
        });
    }

    // 初始加载一次数据，用于统计
    loadGatewayList();
});