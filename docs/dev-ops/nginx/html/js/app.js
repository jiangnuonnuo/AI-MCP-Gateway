// js/app.js
$(document).ready(function() {
    // 检查登录状态
    if(localStorage.getItem('mcp_admin_logged_in') !== 'true') {
        window.location.href = 'index.html';
        return;
    }

    // 退出登录
    $('#logoutBtn').on('click', function(e) {
        e.preventDefault();
        localStorage.removeItem('mcp_admin_logged_in');
        window.location.href = 'index.html';
    });

    // 侧边栏导航切换和动态加载页面
    $('.nav-link[data-target]').on('click', function(e) {
        e.preventDefault();
        
        // 更新激活状态
        $('.nav-link').removeClass('active');
        $(this).addClass('active');
        
        const targetId = $(this).data('target');
        loadView(targetId);
    });

    // 动态加载视图
    function loadView(targetId) {
        const viewPath = `views/${targetId}.html`;
        $('#main-content-wrapper').html('<div class="text-center py-5"><div class="spinner-border text-primary" role="status"></div><div class="mt-2 text-muted">加载中...</div></div>');
        
        $('#main-content-wrapper').load(viewPath, function(response, status, xhr) {
            if (status == "error") {
                $('#main-content-wrapper').html(`<div class="alert alert-danger m-4">页面加载失败：${xhr.status} ${xhr.statusText}</div>`);
                return;
            }
            
            // 页面加载后的初始化逻辑
            initViewLogic(targetId);
        });
    }

    // 初始化各个页面的逻辑
    function initViewLogic(targetId) {
        if (targetId === 'dashboard') {
            $('#display-api-url').text(API_BASE_URL);
            const dateOptions = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
            const dateEl = document.getElementById('current-date');
            if(dateEl) dateEl.textContent = new Date().toLocaleDateString('zh-CN', dateOptions);
            
            // 尝试获取网关总数
            $.ajax({
                url: API_ENDPOINTS.GET_GATEWAY_LIST,
                type: 'GET',
                success: function(response) {
                    if(response && response.code === '0000' && response.data) {
                        $('#stat-gateway-count').text(response.data.length);
                    }
                }
            });
        } else if (targetId === 'gateway-list') {
            loadGatewayList();
        } else if (targetId === 'gateway-tool') {
            loadGatewayToolList();
        } else if (targetId === 'gateway-protocol') {
            loadGatewayProtocolList();
        } else if (targetId === 'gateway-auth') {
            loadGatewayAuthList();
        }
    }

    // 初始加载 Dashboard
    loadView('dashboard');

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

    // 表单提交通用处理 - 使用事件委托
    function handleFormSubmitDelegated(formId, endpoint, dataProcessor, onSuccess) {
        // 先解绑以防重复绑定
        $(document).off('submit', '#' + formId);
        $(document).on('submit', '#' + formId, function(e) {
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
    handleFormSubmitDelegated('form-gateway-config', API_ENDPOINTS.SAVE_GATEWAY_CONFIG, function(data) {
        return {
            gatewayId: data.gatewayId,
            gatewayName: data.gatewayName,
            gatewayDesc: data.gatewayDesc,
            version: data.version,
            auth: parseInt(data.auth),
            status: parseInt(data.status)
        };
    }, function() {
        $('#gatewayConfigModal').modal('hide');
        setTimeout(loadGatewayList, 300);
    });

    // 2. 保存网关工具配置
    handleFormSubmitDelegated('form-gateway-tool', API_ENDPOINTS.SAVE_GATEWAY_TOOL_CONFIG, function(data) {
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
        // 由于模态框关闭动画有延迟，稍微延时刷新列表避免遮罩问题
        setTimeout(loadGatewayToolList, 300);
    });

    // 3. 保存网关协议配置
    handleFormSubmitDelegated('form-gateway-protocol', API_ENDPOINTS.SAVE_GATEWAY_PROTOCOL, function(data) {
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
        setTimeout(loadGatewayProtocolList, 300);
    });

    // 4. 保存网关认证配置
    handleFormSubmitDelegated('form-gateway-auth', API_ENDPOINTS.SAVE_GATEWAY_AUTH, function(data) {
        return {
            gatewayId: data.gatewayId,
            rateLimit: parseInt(data.rateLimit),
            expireTime: parseInt(data.expireTime)
        };
    }, function() {
        $('#gatewayAuthModal').modal('hide');
        setTimeout(loadGatewayAuthList, 300);
    });

    // ==========================================
    // 网关列表相关
    // ==========================================
    $(document).on('click', '#refreshGatewayList', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    $(document).on('click', '#addGatewayBtn', function() {
        $('#form-gateway-config')[0].reset();
        $('#config-gatewayId').prop('readonly', false);
        $('#gatewayConfigModalLabel').html('<i class="bi bi-pencil-square me-2"></i>新增网关基础配置');
    });

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

    // ==========================================
    // 网关工具相关
    // ==========================================
    $(document).on('click', '#refreshGatewayToolList', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayToolList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    $(document).on('click', '#addGatewayToolBtn', function() {
        $('#form-gateway-tool')[0].reset();
        
        // 自动生成8位数字工具ID
        const generatedToolId = Math.floor(10000000 + Math.random() * 90000000);
        $('#tool-toolId').val(generatedToolId);
        
        $('#tool-gatewayId-help').text('网关ID: -');
        $('#tool-protocolId-help').text('协议ID: -');
        $('#gatewayToolModalLabel').html('<i class="bi bi-tools me-2"></i>新增网关工具配置');
        
        loadGatewayOptions();
        loadProtocolOptions();
    });

    // 事件委托 - 修改工具
    $(document).on('click', '.btn-edit-tool', function() {
        try {
            const itemDataStr = decodeURIComponent($(this).data('item'));
            const item = JSON.parse(itemDataStr);
            
            // 填充表单
            $('#tool-toolId').val(item.toolId).prop('readonly', true);
            $('#tool-toolName').val(item.toolName);
            $('#tool-toolType').val(item.toolType);
            $('#tool-toolDescription').val(item.toolDescription);
            $('#tool-toolVersion').val(item.toolVersion);
            $('#tool-protocolType').val(item.protocolType);
            
            $('#gatewayToolModalLabel').html('<i class="bi bi-pencil-square me-2"></i>修改网关工具配置');
            
            // 加载下拉框选项并设置选中值
            loadGatewayOptions(item.gatewayId);
            loadProtocolOptions(item.protocolId);
            
            $('#gatewayToolModal').modal('show');
        } catch (e) {
            console.error("解析数据失败", e);
            showToast("解析数据失败", false);
        }
    });

    // 动态加载网关配置选项
    function loadGatewayOptions(selectedGatewayId = null) {
        const $select = $('#tool-gatewayId');
        const $helpText = $('#tool-gatewayId-help');
        
        // 保持现有选项，只更新"加载中"状态
        $select.html('<option value="">加载网关列表中...</option>');
        
        $.ajax({
            url: API_ENDPOINTS.GET_GATEWAY_LIST,
            type: 'GET',
            success: function(response) {
                if(response && response.code === '0000' && response.data) {
                    let optionsHtml = '<option value="">请选择网关...</option>';
                    response.data.forEach(function(gw) {
                        // 使用松散比较(==)或转换类型，因为从后端传来的类型可能和本地解析的不一致
                        const isSelected = selectedGatewayId == gw.gatewayId ? 'selected' : '';
                        optionsHtml += `<option value="${gw.gatewayId}" ${isSelected}>${gw.gatewayName}</option>`;
                    });
                    $select.html(optionsHtml);
                    
                    // 如果有选中值，触发 change 事件以更新小字提示
                    if (selectedGatewayId) {
                        $helpText.text(`网关ID: ${selectedGatewayId}`);
                    } else {
                        $helpText.text('网关ID: -');
                    }
                } else {
                    $select.html('<option value="">加载失败，请重试</option>');
                }
            },
            error: function() {
                $select.html('<option value="">加载失败，请检查网络</option>');
            }
        });
    }

    // 监听下拉框改变事件更新小字
    $(document).on('change', '#tool-gatewayId', function() {
        const selectedId = $(this).val();
        if (selectedId) {
            $('#tool-gatewayId-help').text(`网关ID: ${selectedId}`);
        } else {
            $('#tool-gatewayId-help').text('网关ID: -');
        }
    });

    // 动态加载关联协议选项
    function loadProtocolOptions(selectedProtocolId = null) {
        const $select = $('#tool-protocolId');
        const $helpText = $('#tool-protocolId-help');
        
        $select.html('<option value="">加载协议列表中...</option>');
        
        $.ajax({
            url: API_ENDPOINTS.GET_GATEWAY_PROTOCOL_LIST,
            type: 'GET',
            success: function(response) {
                if(response && response.code === '0000' && response.data) {
                    let optionsHtml = '<option value="">请选择关联协议...</option>';
                    response.data.forEach(function(protocol) {
                        const isSelected = selectedProtocolId == protocol.protocolId ? 'selected' : '';
                        optionsHtml += `<option value="${protocol.protocolId}" ${isSelected}>${protocol.httpUrl}</option>`;
                    });
                    $select.html(optionsHtml);
                    
                    if (selectedProtocolId) {
                        $helpText.text(`协议ID: ${selectedProtocolId}`);
                    } else {
                        $helpText.text('协议ID: -');
                    }
                } else {
                    $select.html('<option value="">加载失败，请重试</option>');
                }
            },
            error: function() {
                $select.html('<option value="">加载失败，请检查网络</option>');
            }
        });
    }

    // 监听协议下拉框改变事件
    $(document).on('change', '#tool-protocolId', function() {
        const selectedId = $(this).val();
        if (selectedId) {
            $('#tool-protocolId-help').text(`协议ID: ${selectedId}`);
        } else {
            $('#tool-protocolId-help').text('协议ID: -');
        }
    });

    // 事件委托 - 删除工具
    $(document).on('click', '.btn-delete-tool', function() {
        const gatewayId = $(this).data('gateway-id');
        const toolId = $(this).data('tool-id');
        
        if(confirm(`确定要删除工具 ID: ${toolId} 吗？`)) {
            const $btn = $(this);
            const originalHtml = $btn.html();
            $btn.html('<i class="bi bi-hourglass-split"></i>').prop('disabled', true);
            
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

    // ==========================================
    // 网关协议列表相关
    // ==========================================
    let uploadedOpenApiJson = ''; // 用于存储上传的 JSON 字符串

    $(document).on('click', '#refreshGatewayProtocolList', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayProtocolList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    $(document).on('click', '#addGatewayProtocolBtn', function() {
        $('#form-gateway-protocol')[0].reset();
        $('#protocol-protocolId').val(''); 
        $('#protocol-mappingsJson').val(''); 
        $('#gatewayProtocolModalLabel').html('<i class="bi bi-hdd-network me-2"></i>新增网关协议配置');
    });

    // 导入协议按钮点击
    $(document).on('click', '#importProtocolBtn', function() {
        $('#form-import-protocol')[0].reset();
        $('#endpoints-selection-container').addClass('d-none');
        $('#endpoints-list').empty();
        $('#btn-submit-import').prop('disabled', true);
        uploadedOpenApiJson = '';
    });

    // 监听文件上传
    $(document).on('change', '#import-json-file', function(e) {
        const file = e.target.files[0];
        if (!file) {
            $('#endpoints-selection-container').addClass('d-none');
            $('#btn-submit-import').prop('disabled', true);
            return;
        }

        const reader = new FileReader();
        reader.onload = function(event) {
            try {
                const jsonStr = event.target.result;
                const jsonObj = JSON.parse(jsonStr);
                uploadedOpenApiJson = jsonStr;

                if (!jsonObj.paths || Object.keys(jsonObj.paths).length === 0) {
                    showToast('文件中未找到有效的接口路径 (paths)', false);
                    return;
                }

                // 渲染接口列表
                let listHtml = '<div class="list-group">';
                Object.keys(jsonObj.paths).forEach((path, index) => {
                    const methods = Object.keys(jsonObj.paths[path]).join(', ').toUpperCase();
                    listHtml += `
                        <label class="list-group-item d-flex gap-2">
                            <input class="form-check-input flex-shrink-0 endpoint-checkbox" type="checkbox" value="${path}" checked>
                            <span>
                                <strong>${path}</strong>
                                <small class="d-block text-muted">Methods: ${methods}</small>
                            </span>
                        </label>
                    `;
                });
                listHtml += '</div>';

                $('#endpoints-list').html(listHtml);
                $('#endpoints-selection-container').removeClass('d-none');
                $('#btn-submit-import').prop('disabled', false);

            } catch (err) {
                console.error(err);
                showToast('JSON 文件解析失败，请检查格式', false);
            }
        };
        reader.readAsText(file);
    });

    // 提交导入
    $(document).on('submit', '#form-import-protocol', function(e) {
        e.preventDefault();
        
        const selectedEndpoints = [];
        $('.endpoint-checkbox:checked').each(function() {
            selectedEndpoints.push($(this).val());
        });

        if (selectedEndpoints.length === 0) {
            showToast('请至少选择一个要导入的接口', false);
            return;
        }

        const $btn = $('#btn-submit-import');
        const originalHtml = $btn.html();
        $btn.html('<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>导入中...').prop('disabled', true);

        const requestData = {
            openApiJson: uploadedOpenApiJson,
            endpoints: selectedEndpoints
        };

        $.ajax({
            url: API_ENDPOINTS.IMPORT_GATEWAY_PROTOCOL,
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(requestData),
            success: function(response) {
                if(response && response.code === '0000') {
                    showToast('协议导入成功！');
                    $('#importProtocolModal').modal('hide');
                    setTimeout(loadGatewayProtocolList, 300);
                } else {
                    showToast('导入失败：' + (response.info || '未知错误'), false);
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

    $(document).on('click', '.btn-edit-protocol', function() {
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

    $(document).on('click', '.btn-delete-protocol', function() {
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

    // ==========================================
    // 网关认证列表相关
    // ==========================================
    $(document).on('click', '#refreshGatewayAuthList', function() {
        const $btn = $(this);
        const originalHtml = $btn.html();
        $btn.html('<i class="bi bi-arrow-clockwise fa-spin"></i> 刷新中...').prop('disabled', true);
        
        loadGatewayAuthList(() => {
            $btn.html(originalHtml).prop('disabled', false);
        });
    });

    $(document).on('click', '#addGatewayAuthBtn', function() {
        $('#form-gateway-auth')[0].reset();
        $('#auth-gatewayId').prop('readonly', false);
        $('#gatewayAuthModalLabel').html('<i class="bi bi-shield-check me-2"></i>新增认证配置');
    });

    $(document).on('click', '.btn-edit-auth', function() {
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

    $(document).on('click', '.btn-delete-auth', function() {
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

});
