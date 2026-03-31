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
        } else if (targetId === 'gateway-tool') {
            loadGatewayToolList();
        } else if (targetId === 'gateway-protocol') {
            loadGatewayProtocolList();
        } else if (targetId === 'gateway-auth') {
            loadGatewayAuthList();
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
    function handleFormSubmit(formId, endpoint, dataProcessor, onSuccess) {
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
                        if (onSuccess) onSuccess();
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
    }, function() {
        $('#gatewayToolModal').modal('hide');
        loadGatewayToolList();
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
                    protocolId: data.protocolId ? parseInt(data.protocolId) : null,
                    httpUrl: data.httpUrl,
                    httpMethod: data.httpMethod,
                    timeout: parseInt(data.timeout) || 5000,
                    httpHeaders: data.httpHeaders,
                    mappings: mappings
                }
            ]
        };
    }, function() {
        $('#gatewayProtocolModal').modal('hide');
        loadGatewayProtocolList();
    });

    // 4. 保存网关认证配置
    handleFormSubmit('form-gateway-auth', API_ENDPOINTS.SAVE_GATEWAY_AUTH, function(data) {
        return {
            gatewayId: data.gatewayId,
            rateLimit: parseInt(data.rateLimit),
            expireTime: parseInt(data.expireTime)
        };
    }, function() {
        $('#gatewayAuthModal').modal('hide');
        loadGatewayAuthList();
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

    // 刷新工具列表按钮
    $('#refreshGatewayToolList').on('click', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayToolList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    // 获取网关工具列表数据
    function loadGatewayToolList(callback) {
        const tbody = $('#gatewayToolTableBody');
        if(!callback) {
            tbody.html('<tr><td colspan="8" class="text-center text-muted py-4"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>加载中...</td></tr>');
        }
        
        $.ajax({
            url: API_ENDPOINTS.GET_GATEWAY_TOOL_LIST,
            type: 'GET',
            success: function(response) {
                if(response && response.code === '0000' && response.data) {
                    const list = response.data;
                    
                    if(list.length === 0) {
                        tbody.html('<tr><td colspan="8" class="text-center text-muted py-4"><i class="bi bi-inbox fs-4 d-block mb-2"></i>暂无网关工具数据</td></tr>');
                    } else {
                        let html = '';
                        list.forEach(function(item) {
                            // Serialize item for edit
                            const itemData = encodeURIComponent(JSON.stringify(item));
                            html += `
                                <tr>
                                    <td><code>${item.gatewayId || '-'}</code></td>
                                    <td><span class="badge bg-secondary">${item.toolId || '-'}</span></td>
                                    <td class="fw-bold text-truncate" style="max-width: 150px;" title="${item.toolName || ''}">${item.toolName || '-'}</td>
                                    <td>${item.toolType || '-'}</td>
                                    <td><span class="text-truncate d-inline-block text-muted" style="max-width: 150px;" title="${item.toolDescription || ''}">${item.toolDescription || '-'}</span></td>
                                    <td>${item.toolVersion || '-'}</td>
                                    <td><span class="badge bg-info text-dark">${item.protocolType || '-'}</span></td>
                                    <td>
                                        <div class="btn-group btn-group-sm">
                                            <button type="button" class="btn btn-outline-primary btn-edit-tool" data-item="${itemData}">
                                                <i class="bi bi-pencil-square"></i> 修改
                                            </button>
                                            <button type="button" class="btn btn-outline-danger btn-delete-tool" data-gateway-id="${item.gatewayId}" data-tool-id="${item.toolId}">
                                                <i class="bi bi-trash"></i> 删除
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            `;
                        });
                        tbody.html(html);
                        
                        // 绑定事件
                        bindToolActionEvents();
                    }
                } else {
                    tbody.html(`<tr><td colspan="8" class="text-center text-danger py-4"><i class="bi bi-exclamation-triangle me-2"></i>加载失败: ${response.info || '未知错误'}</td></tr>`);
                }
            },
            error: function() {
                tbody.html('<tr><td colspan="8" class="text-center text-danger py-4"><i class="bi bi-wifi-off me-2"></i>网络请求失败，请检查服务是否启动</td></tr>');
            },
            complete: function() {
                if(callback) callback();
            }
        });
    }

    // 绑定工具列表操作按钮事件
    function bindToolActionEvents() {
        // 修改工具
        $('.btn-edit-tool').on('click', function() {
            try {
                const itemDataStr = decodeURIComponent($(this).data('item'));
                const item = JSON.parse(itemDataStr);
                
                // 填充表单
                $('#tool-gatewayId').val(item.gatewayId);
                $('#tool-toolId').val(item.toolId).prop('readonly', true); // 工具ID通常不建议修改
                $('#tool-toolName').val(item.toolName);
                $('#tool-toolType').val(item.toolType);
                $('#tool-toolDescription').val(item.toolDescription);
                $('#tool-toolVersion').val(item.toolVersion);
                $('#tool-protocolId').val(item.protocolId);
                $('#tool-protocolType').val(item.protocolType);
                
                // 修改模态框标题
                $('#gatewayToolModalLabel').html('<i class="bi bi-pencil-square me-2"></i>修改网关工具配置');
                
                // 显示模态框
                $('#gatewayToolModal').modal('show');
            } catch (e) {
                console.error("解析数据失败", e);
                showToast("解析数据失败", false);
            }
        });

        // 删除工具
        $('.btn-delete-tool').on('click', function() {
            const gatewayId = $(this).data('gateway-id');
            const toolId = $(this).data('tool-id');
            
            if(confirm(`确定要删除工具 ID: ${toolId} 吗？`)) {
                const $btn = $(this);
                const originalHtml = $btn.html();
                $btn.html('<i class="bi bi-hourglass-split"></i>').prop('disabled', true);
                
                // 由于删除接口使用POST且为FormData形式，这里按照后端的@RequestParam进行传参
                $.ajax({
                    url: `${API_ENDPOINTS.DELETE_GATEWAY_TOOL}?gatewayId=${encodeURIComponent(gatewayId)}&toolId=${encodeURIComponent(toolId)}`,
                    type: 'POST',
                    success: function(response) {
                        if(response && response.code === '0000') {
                            showToast('删除成功！');
                            loadGatewayToolList();
                        } else {
                            showToast('删除失败：' + (response.info || '未知错误'), false);
                            $btn.html(originalHtml).prop('disabled', false);
                        }
                    },
                    error: function(xhr, status, error) {
                        showToast('请求失败：' + error, false);
                        $btn.html(originalHtml).prop('disabled', false);
                    }
                });
            }
        });
    }

    // 新增工具按钮点击事件
    $('#addGatewayToolBtn').on('click', function() {
        $('#form-gateway-tool')[0].reset();
        $('#tool-toolId').prop('readonly', false);
        $('#gatewayToolModalLabel').html('<i class="bi bi-tools me-2"></i>新增网关工具配置');
    });

    // ==========================================
    // 网关协议列表相关
    // ==========================================
    $('#refreshGatewayProtocolList').on('click', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayProtocolList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    function loadGatewayProtocolList(callback) {
        const tbody = $('#gatewayProtocolTableBody');
        if(!callback) {
            tbody.html('<tr><td colspan="5" class="text-center text-muted py-4"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>加载中...</td></tr>');
        }
        
        $.ajax({
            url: API_ENDPOINTS.GET_GATEWAY_PROTOCOL_LIST,
            type: 'GET',
            success: function(response) {
                if(response && response.code === '0000' && response.data) {
                    const list = response.data;
                    
                    if(list.length === 0) {
                        tbody.html('<tr><td colspan="5" class="text-center text-muted py-4"><i class="bi bi-inbox fs-4 d-block mb-2"></i>暂无网关协议数据</td></tr>');
                    } else {
                        let html = '';
                        list.forEach(function(item) {
                            const itemData = encodeURIComponent(JSON.stringify(item));
                            html += `
                                <tr>
                                    <td><code>${item.protocolId || '-'}</code></td>
                                    <td class="text-truncate" style="max-width: 250px;" title="${item.httpUrl || ''}">${item.httpUrl || '-'}</td>
                                    <td><span class="badge bg-secondary">${item.httpMethod || '-'}</span></td>
                                    <td>${item.timeout || '-'} ms</td>
                                    <td>
                                        <div class="btn-group btn-group-sm">
                                            <button type="button" class="btn btn-outline-primary btn-edit-protocol" data-item="${itemData}">
                                                <i class="bi bi-pencil-square"></i> 修改
                                            </button>
                                            <button type="button" class="btn btn-outline-danger btn-delete-protocol" data-protocol-id="${item.protocolId}">
                                                <i class="bi bi-trash"></i> 删除
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            `;
                        });
                        tbody.html(html);
                        bindProtocolActionEvents();
                    }
                } else {
                    tbody.html(`<tr><td colspan="5" class="text-center text-danger py-4"><i class="bi bi-exclamation-triangle me-2"></i>加载失败: ${response.info || '未知错误'}</td></tr>`);
                }
            },
            error: function() {
                tbody.html('<tr><td colspan="5" class="text-center text-danger py-4"><i class="bi bi-wifi-off me-2"></i>网络请求失败，请检查服务是否启动</td></tr>');
            },
            complete: function() {
                if(callback) callback();
            }
        });
    }

    function bindProtocolActionEvents() {
        $('.btn-edit-protocol').on('click', function() {
            try {
                const itemDataStr = decodeURIComponent($(this).data('item'));
                const item = JSON.parse(itemDataStr);
                
                $('#protocol-protocolId').val(item.protocolId);
                $('#protocol-httpUrl').val(item.httpUrl);
                $('#protocol-httpMethod').val(item.httpMethod);
                $('#protocol-timeout').val(item.timeout);
                $('#protocol-httpHeaders').val(item.httpHeaders);
                
                if (item.mappings && item.mappings.length > 0) {
                    $('#protocol-mappingsJson').val(JSON.stringify(item.mappings, null, 2));
                } else {
                    $('#protocol-mappingsJson').val('');
                }
                
                $('#gatewayProtocolModalLabel').html('<i class="bi bi-pencil-square me-2"></i>修改网关协议配置');
                $('#gatewayProtocolModal').modal('show');
            } catch (e) {
                console.error("解析数据失败", e);
                showToast("解析数据失败", false);
            }
        });

        $('.btn-delete-protocol').on('click', function() {
            const protocolId = $(this).data('protocol-id');
            
            if(confirm(`确定要删除协议 ID: ${protocolId} 吗？`)) {
                const $btn = $(this);
                const originalHtml = $btn.html();
                $btn.html('<i class="bi bi-hourglass-split"></i>').prop('disabled', true);
                
                $.ajax({
                    url: `${API_ENDPOINTS.DELETE_GATEWAY_PROTOCOL}?protocolId=${encodeURIComponent(protocolId)}`,
                    type: 'POST',
                    success: function(response) {
                        if(response && response.code === '0000') {
                            showToast('删除成功！');
                            loadGatewayProtocolList();
                        } else {
                            showToast('删除失败：' + (response.info || '未知错误'), false);
                            $btn.html(originalHtml).prop('disabled', false);
                        }
                    },
                    error: function(xhr, status, error) {
                        showToast('请求失败：' + error, false);
                        $btn.html(originalHtml).prop('disabled', false);
                    }
                });
            }
        });
    }

    $('#addGatewayProtocolBtn').on('click', function() {
        $('#form-gateway-protocol')[0].reset();
        $('#protocol-protocolId').val(''); // Clear protocol ID for new entry
        $('#protocol-mappingsJson').val(''); // Clear mappings text area
        $('#gatewayProtocolModalLabel').html('<i class="bi bi-hdd-network me-2"></i>新增网关协议配置');
    });

    // ==========================================
    // 网关认证列表相关
    // ==========================================
    $('#refreshGatewayAuthList').on('click', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayAuthList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    function loadGatewayAuthList(callback) {
        const tbody = $('#gatewayAuthTableBody');
        if(!callback) {
            tbody.html('<tr><td colspan="5" class="text-center text-muted py-4"><div class="spinner-border spinner-border-sm text-primary me-2" role="status"></div>加载中...</td></tr>');
        }
        
        $.ajax({
            url: API_ENDPOINTS.GET_GATEWAY_AUTH_LIST,
            type: 'GET',
            success: function(response) {
                if(response && response.code === '0000' && response.data) {
                    const list = response.data;
                    
                    if(list.length === 0) {
                        tbody.html('<tr><td colspan="5" class="text-center text-muted py-4"><i class="bi bi-inbox fs-4 d-block mb-2"></i>暂无网关认证数据</td></tr>');
                    } else {
                        let html = '';
                        list.forEach(function(item) {
                            const itemData = encodeURIComponent(JSON.stringify(item));
                            // 格式化时间戳
                            let expireTimeStr = '-';
                            if (item.expireTime) {
                                const d = new Date(item.expireTime);
                                expireTimeStr = d.toLocaleString();
                            }
                            html += `
                                <tr>
                                    <td><code>${item.gatewayId || '-'}</code></td>
                                    <td><span class="text-truncate d-inline-block" style="max-width: 250px;" title="${item.apiKey || ''}">${item.apiKey || '-'}</span></td>
                                    <td>${item.rateLimit || '-'} 次/秒</td>
                                    <td>${expireTimeStr}</td>
                                    <td>
                                        <div class="btn-group btn-group-sm">
                                            <button type="button" class="btn btn-outline-primary btn-edit-auth" data-item="${itemData}">
                                                <i class="bi bi-pencil-square"></i> 修改
                                            </button>
                                            <button type="button" class="btn btn-outline-danger btn-delete-auth" data-gateway-id="${item.gatewayId}">
                                                <i class="bi bi-trash"></i> 删除
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            `;
                        });
                        tbody.html(html);
                        bindAuthActionEvents();
                    }
                } else {
                    tbody.html(`<tr><td colspan="5" class="text-center text-danger py-4"><i class="bi bi-exclamation-triangle me-2"></i>加载失败: ${response.info || '未知错误'}</td></tr>`);
                }
            },
            error: function() {
                tbody.html('<tr><td colspan="5" class="text-center text-danger py-4"><i class="bi bi-wifi-off me-2"></i>网络请求失败，请检查服务是否启动</td></tr>');
            },
            complete: function() {
                if(callback) callback();
            }
        });
    }

    function bindAuthActionEvents() {
        $('.btn-edit-auth').on('click', function() {
            try {
                const itemDataStr = decodeURIComponent($(this).data('item'));
                const item = JSON.parse(itemDataStr);
                
                $('#auth-gatewayId').val(item.gatewayId).prop('readonly', true);
                $('#auth-rateLimit').val(item.rateLimit);
                $('#auth-expireTime').val(item.expireTime);
                
                $('#gatewayAuthModalLabel').html('<i class="bi bi-pencil-square me-2"></i>修改网关认证配置');
                $('#gatewayAuthModal').modal('show');
            } catch (e) {
                console.error("解析数据失败", e);
                showToast("解析数据失败", false);
            }
        });

        $('.btn-delete-auth').on('click', function() {
            const gatewayId = $(this).data('gateway-id');
            
            if(confirm(`确定要删除网关 ID: ${gatewayId} 的认证配置吗？`)) {
                const $btn = $(this);
                const originalHtml = $btn.html();
                $btn.html('<i class="bi bi-hourglass-split"></i>').prop('disabled', true);
                
                $.ajax({
                    url: `${API_ENDPOINTS.DELETE_GATEWAY_AUTH}?gatewayId=${encodeURIComponent(gatewayId)}`,
                    type: 'POST',
                    success: function(response) {
                        if(response && response.code === '0000') {
                            showToast('删除成功！');
                            loadGatewayAuthList();
                        } else {
                            showToast('删除失败：' + (response.info || '未知错误'), false);
                            $btn.html(originalHtml).prop('disabled', false);
                        }
                    },
                    error: function(xhr, status, error) {
                        showToast('请求失败：' + error, false);
                        $btn.html(originalHtml).prop('disabled', false);
                    }
                });
            }
        });
    }

    $('#addGatewayAuthBtn').on('click', function() {
        $('#form-gateway-auth')[0].reset();
        $('#auth-gatewayId').prop('readonly', false);
        $('#gatewayAuthModalLabel').html('<i class="bi bi-shield-check me-2"></i>新增认证配置');
    });

});