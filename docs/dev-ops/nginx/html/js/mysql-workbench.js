(function (window, $) {
    'use strict';

    const icons = ['bi-database', 'bi-file-earmark-code', 'bi-link-45deg'];
    const kpiIcons = ['▣', '◉', '♣', '◇'];

    const previews = {
        datasource: [
            { id: 'ds-warehouse', datasourceRef: 'data-warehouse', name: 'data-warehouse', description: 'MySQL 8.0 · warehouse', host: '10.20.4.18', port: 3306, database: 'warehouse', username: 'report_reader', readOnly: true, templateCount: 8, status: 'enabled', statusLabel: '在线', updatedAt: '2 分钟前' },
            { id: 'ds-inventory', datasourceRef: 'inventory-prod', name: 'inventory-prod', description: 'MySQL 8.0 · inventory', host: '10.20.4.21', port: 3306, database: 'inventory', username: 'inventory_ro', readOnly: true, templateCount: 3, status: 'enabled', statusLabel: '在线', updatedAt: '18 分钟前' },
            { id: 'ds-crm', datasourceRef: 'crm-analytics', name: 'crm-analytics', description: 'MySQL 8.0 · crm', host: '10.20.4.24', port: 3306, database: 'crm', username: 'crm_reader', readOnly: true, templateCount: 2, status: 'enabled', statusLabel: '在线', updatedAt: '1 小时前' },
            { id: 'ds-marketing', datasourceRef: 'marketing-read', name: 'marketing-read', description: 'MySQL 8.0 · marketing', host: '10.20.5.11', port: 3306, database: 'marketing', username: 'marketing_ro', readOnly: true, templateCount: 0, status: 'attention', statusLabel: '待检查', updatedAt: '3 小时前' }
        ],
        template: [
            { id: 'tpl-orders', name: '订单来源汇总', description: '按渠道统计订单数量、金额和用户数', datasourceId: 'ds-warehouse', datasource: 'data-warehouse', toolCount: 2, status: 'enabled', statusLabel: '已发布', sql: 'SELECT c.channel_name,\n       SUM(o.pay_amount) AS revenue,\n       COUNT(o.order_id) AS orders\nFROM fact_order o\nJOIN dim_channel c ON c.id = o.channel_id\nWHERE o.pay_time BETWEEN :fromTime AND :toTime\nGROUP BY c.channel_name', params: [{ name: 'fromTime', type: 'DATETIME' }, { name: 'toTime', type: 'DATETIME' }, { name: 'channelId', type: 'INTEGER' }, { name: 'status', type: 'STRING' }], guards: '只读保护 · 1000 行 · 3000 ms', updatedAt: '12 分钟前' },
            { id: 'tpl-store', name: '门店营收报表', description: '按门店统计日/月销售数据', datasourceId: 'ds-warehouse', datasource: 'data-warehouse', toolCount: 1, status: 'enabled', statusLabel: '已发布', sql: 'SELECT store_id, SUM(pay_amount) AS revenue\nFROM fact_order\nWHERE pay_time BETWEEN :fromTime AND :toTime\nGROUP BY store_id', params: [{ name: 'fromTime', type: 'DATETIME' }, { name: 'toTime', type: 'DATETIME' }], guards: '只读保护 · 2000 行 · 5000 ms', updatedAt: '28 分钟前' },
            { id: 'tpl-inventory', name: '商品库存预警', description: '查询低库存商品列表', datasourceId: 'ds-inventory', datasource: 'inventory-prod', toolCount: 0, status: 'draft', statusLabel: '草稿', sql: 'SELECT sku, stock, safety_stock\nFROM inventory_snapshot\nWHERE stock < safety_stock\nORDER BY stock ASC', params: [], guards: '待校验', updatedAt: '昨天' },
            { id: 'tpl-member', name: '会员画像数据', description: '用户分层与 RFM 分析', datasourceId: 'ds-crm', datasource: 'crm-analytics', toolCount: 1, status: 'enabled', statusLabel: '已发布', sql: 'SELECT member_id, segment, recency, frequency, monetary\nFROM member_rfm\nWHERE member_id = :memberId', params: [{ name: 'memberId', type: 'STRING' }], guards: '只读保护 · 100 行 · 2000 ms', updatedAt: '昨天' }
        ],
        binding: [
            { id: 'bind-orders', templateId: 'tpl-orders', templateName: '订单来源汇总', datasourceId: 'ds-warehouse', datasource: 'data-warehouse', gatewayId: 'gw-commerce-prod', gateway: 'gateway-commerce-prod', toolName: 'order_channel_summary', description: '查询订单渠道汇总', version: '1.0.0', protocol: 'MYSQL · 900001', status: 'enabled', statusLabel: '已启用', updatedAt: '7 分钟前' },
            { id: 'bind-store', templateId: 'tpl-store', templateName: '门店营收报表', datasourceId: 'ds-warehouse', datasource: 'data-warehouse', gatewayId: 'gw-commerce-prod', gateway: 'gateway-commerce-prod', toolName: 'store_revenue_report', description: '查询门店营收数据', version: '1.0.0', protocol: 'MYSQL · 900002', status: 'enabled', statusLabel: '已启用', updatedAt: '34 分钟前' },
            { id: 'bind-member', templateId: 'tpl-member', templateName: '会员画像数据', datasourceId: 'ds-crm', datasource: 'crm-analytics', gatewayId: 'gw-analytics-dev', gateway: 'gateway-analytics-dev', toolName: 'member_profile', description: '查询会员画像摘要', version: '1.0.0', protocol: 'MYSQL · 900004', status: 'enabled', statusLabel: '已启用', updatedAt: '1 小时前' },
            { id: 'bind-inventory', templateId: 'tpl-inventory', templateName: '商品库存预警', datasourceId: 'ds-inventory', datasource: 'inventory-prod', gatewayId: 'gw-internal', gateway: 'gateway-internal', toolName: 'inventory_alert', description: '查询低库存商品', version: '0.9.0', protocol: 'MYSQL · 900003', status: 'draft', statusLabel: '草稿', updatedAt: '昨天' }
        ]
    };

    const configs = {
        datasource: {
            title: '数据源',
            kpis: [['08', '数据源', '● 全部正常'], ['07', '连接池就绪', '↗ 稳定'], ['12', '关联模板', '↗ +4 本周'], ['100%', '只读策略', '● 已开启']],
            endpoints: { page: API_ENDPOINTS.GET_MYSQL_DATASOURCE_PAGE, detail: API_ENDPOINTS.GET_MYSQL_DATASOURCE_DETAIL, save: API_ENDPOINTS.SAVE_MYSQL_DATASOURCE, status: API_ENDPOINTS.CHANGE_MYSQL_DATASOURCE_STATUS, remove: API_ENDPOINTS.DELETE_MYSQL_DATASOURCE, test: API_ENDPOINTS.TEST_MYSQL_DATASOURCE },
            countId: 'nav-datasource-count'
        },
        template: {
            title: 'SQL 模板',
            kpis: [['12', 'SQL 模板', '↗ +4 本周'], ['05', '数据源', '● 正常连接'], ['08', '已发布 Tool', '↗ +3 本周'], ['100%', '模板健康度', '● 全部正常']],
            endpoints: { page: API_ENDPOINTS.GET_MYSQL_TEMPLATE_PAGE, detail: API_ENDPOINTS.GET_MYSQL_TEMPLATE_DETAIL, save: API_ENDPOINTS.SAVE_MYSQL_TEMPLATE, status: API_ENDPOINTS.CHANGE_MYSQL_TEMPLATE_STATUS, remove: API_ENDPOINTS.DELETE_MYSQL_TEMPLATE, test: API_ENDPOINTS.TEST_MYSQL_TEMPLATE },
            countId: 'nav-template-count'
        },
        binding: {
            title: 'Tool 绑定',
            kpis: [['09', '在线绑定', '● 0 个断链'], ['12', '模板', '↗ 可绑定'], ['16', 'Tool', '● 正常路由'], ['03', '网关', '全部在线']],
            endpoints: { page: API_ENDPOINTS.GET_MYSQL_BINDING_PAGE, detail: API_ENDPOINTS.GET_MYSQL_BINDING_DETAIL, save: API_ENDPOINTS.SAVE_MYSQL_BINDING, status: API_ENDPOINTS.CHANGE_MYSQL_BINDING_STATUS, remove: API_ENDPOINTS.DELETE_MYSQL_BINDING },
            countId: 'nav-binding-count'
        }
    };

    function escapeHtml(value) {
        return $('<div>').text(value == null ? '' : String(value)).html();
    }

    function statusClass(status) {
        return status === 'enabled' ? 'live' : status === 'attention' ? 'danger' : 'draft';
    }

    function statusLabel(row) {
        if (row.statusLabel) return row.statusLabel;
        return row.status === 'enabled' ? '已启用' : row.status === 'attention' ? '待检查' : '草稿';
    }

    function formatStatusTag(row) {
        const label = statusLabel(row);
        return `<span class="tag ${statusClass(row.status)}">● ${escapeHtml(label)}</span>`;
    }

    function apiError(response, fallback) {
        const code = response && (response.errorCode || response.code || response.data && response.data.errorCode);
        const messages = {
            DATASOURCE_IN_USE: '该数据源仍被模板或绑定引用，请先解除引用。',
            DATASOURCE_CREDENTIAL_REQUIRED: '新建数据源必须提供密码和密钥引用。',
            DATASOURCE_CREDENTIAL_KEY_UNAVAILABLE: '服务端未配置数据源凭证主密钥，请设置 MCP_MYSQL_DATASOURCE_KEY 后重启服务。',
            DATASOURCE_JDBC_URL_SENSITIVE: 'JDBC 地址不得包含密码、Token 或其他凭证参数。',
            DATASOURCE_CONNECTION_FAILED: '无法连接该数据源，请检查主机、端口、数据库名、用户名和密码。',
            ENABLED_TEMPLATE_IMMUTABLE: '启用中的模板不可修改 SQL、数据源或参数契约，请先停用或创建新模板。',
            PROTOCOL_IMMUTABLE: '启用中的模板不可修改 SQL、数据源或参数契约，请先停用或创建新模板。',
            TOOL_NAME_DUPLICATE: '同一 Gateway 下已存在同名 Tool，请更换工具名称。',
            TOOL_NAME_CONFLICT: '同一 Gateway 下已存在同名 Tool，请更换工具名称。',
            TEMPLATE_IN_USE: '该模板仍被 Tool 绑定引用，请先解除绑定。',
            BINDING_RESOURCE_UNAVAILABLE: '模板或数据源未启用，无法发布该绑定。',
            GATEWAY_NOT_FOUND: 'Gateway 不存在或已不可用。',
            RESOURCE_NOT_FOUND: '资源不存在，可能已被其他管理员删除。',
            VALIDATION_ERROR: '请检查表单中的字段和格式。'
        };
        return messages[code] || (response && (response.info || response.message)) || fallback || '请求失败，请稍后重试。';
    }

    function showToast(message, success) {
        const toast = $('#liveToast');
        if (!toast.length) return;
        $('#toastMessage').html(`<i class="bi ${success === false ? 'bi-exclamation-triangle-fill' : 'bi-check-circle-fill'}" aria-hidden="true"></i><span>${escapeHtml(message)}</span>`);
        toast.toggleClass('is-error', success === false);
        if (window.bootstrap && bootstrap.Toast) bootstrap.Toast.getOrCreateInstance(toast[0], { delay: 3600 }).show();
    }

    let confirmResolver = null;
    function confirmAction(message) {
        const dialog = $('#confirmDialog');
        if (!dialog.length) return Promise.resolve(window.confirm(message));
        $('#confirmDialogMessage').text(message);
        dialog.removeAttr('hidden');
        $('#confirmDialogConfirm').trigger('focus');
        return new Promise(resolve => { confirmResolver = resolve; });
    }

    $(document).off('click.workbench-confirm', '#confirmDialogCancel').on('click.workbench-confirm', '#confirmDialogCancel', function () {
        $('#confirmDialog').attr('hidden', 'hidden');
        if (confirmResolver) confirmResolver(false);
        confirmResolver = null;
    });
    $(document).off('click.workbench-confirm', '#confirmDialogConfirm').on('click.workbench-confirm', '#confirmDialogConfirm', function () {
        $('#confirmDialog').attr('hidden', 'hidden');
        if (confirmResolver) confirmResolver(true);
        confirmResolver = null;
    });

    function kpiHtml(items) {
        return items.map((item, index) => `<div class="kpi-card"><span class="kpi-icon" aria-hidden="true">${kpiIcons[index]}</span><div><div class="kpi-value">${escapeHtml(item[0])}</div><div class="kpi-label">${escapeHtml(item[1])}</div><div class="kpi-note">${escapeHtml(item[2])}</div></div></div>`).join('');
    }

    function normalizePage(response, resource) {
        const raw = response && response.data;
        const list = Array.isArray(raw) ? raw : raw && (raw.list || raw.records || raw.rows || raw.data) || [];
        if (!Array.isArray(list)) return { list: [], total: 0 };
        return { list: list.map(item => normalizeItem(item, resource)), total: Number(response.total || raw && raw.total || list.length) };
    }

    function parseJdbcUrl(value) {
        const jdbcUrl = String(value || '');
        const match = jdbcUrl.match(/^jdbc:mysql:\/\/([^/:]+|\[[^\]]+\])(?::(\d+))?\/([^?;]+)/i);
        return match ? { host: match[1], port: Number(match[2] || 3306), database: decodeURIComponent(match[3]) }
            : { host: '-', port: 3306, database: '-' };
    }

    function normalizeItem(item, resource) {
        if (resource === 'datasource') {
            const connection = parseJdbcUrl(item.jdbcUrlMasked || item.jdbcUrl || item.jdbcURL);
            return stripSensitive($.extend({}, item, {
            id: item.id || item.datasourceId || item.dataSourceId,
            name: item.name || item.datasourceName,
            description: item.description || `${item.type || 'MySQL'} · ${item.database || item.databaseName || '-'}`,
            host: item.host || item.hostname || item.jdbcHost || connection.host, port: item.port || item.jdbcPort || connection.port,
            database: item.database || item.databaseName || connection.database, username: item.username || item.userName || '-',
            readOnly: item.readOnly !== false && item.readonly !== false, templateCount: item.templateCount || item.referenceCount || 0,
            status: normalizeStatus(item.status), statusLabel: item.statusLabel || item.statusText || ''
            }));
        }
        if (resource === 'template') return stripSensitive($.extend({}, item, {
            id: item.id || item.protocolId || item.mysqlProtocolId,
            name: item.name || item.templateName,
            description: item.description || item.templateDesc || 'MySQL 只读 SQL 模板',
            version: item.version || item.protocolVersion || '1.0.0',
            datasourceRef: item.datasourceRef || item.datasourceId || item.dataSourceId,
            datasourceId: item.datasourceRef || item.datasourceId || item.dataSourceId,
            datasource: item.datasource || item.datasourceName || item.datasourceRef || '-',
            toolCount: item.toolCount || item.bindingCount || 0, status: normalizeStatus(item.status), statusLabel: item.statusLabel || item.statusText || '',
            sql: item.sql || item.readonlySql || item.sqlText || 'SELECT ...', params: item.params || item.parameters || [], guards: item.guards || '待校验'
        }));
        return stripSensitive($.extend({}, item, {
            id: item.id || item.bindingId || item.toolId, templateId: item.templateId || item.protocolId,
            templateName: item.templateName || item.protocolName || '-', datasourceId: item.datasourceId || item.dataSourceId,
            datasource: item.datasource || item.datasourceName || '-', gatewayId: item.gatewayId, gateway: item.gateway || item.gatewayName || '-',
            toolName: item.toolName || item.name, description: item.description || item.toolDescription || '-', version: item.version || item.toolVersion || '1.0.0',
            protocol: item.protocol || `${item.protocolType || 'MYSQL'} · ${item.protocolId || '-'}`,
            status: normalizeStatus(item.status), statusLabel: item.statusLabel || item.statusText || ''
        }));
    }

    function stripSensitive(row) {
        ['password', 'passwd', 'token', 'authorization', 'ciphertext', 'encryptedPassword', 'nonce', 'jdbcUrl', 'jdbcURL'].forEach(key => { delete row[key]; });
        return row;
    }

    function normalizeStatus(value) {
        if (value === true || value === 1 || value === '1' || value === 'ENABLED' || value === 'enabled' || value === 'ACTIVE') return 'enabled';
        if (value === 'ATTENTION' || value === 'attention' || value === 2) return 'attention';
        return 'draft';
    }

    function valueText(row, resource) {
        if (resource === 'datasource') return { name: row.name, sub: row.description, relation: `${row.host}:${row.port}/${row.database}`, tool: `${row.templateCount || 0} Templates` };
        if (resource === 'template') return { name: row.name, sub: row.description, relation: row.datasource, tool: `${row.toolCount || 0} Tool${row.toolCount === 1 ? '' : 's'}` };
        return { name: row.templateName, sub: row.description, relation: `${row.gateway} · ${row.protocol}`, tool: row.toolName };
    }

    function rowHtml(row, resource, selectedId) {
        const text = valueText(row, resource);
        return `<div class="registry-row ${String(row.id) === String(selectedId) ? 'selected' : ''}" data-row-id="${escapeHtml(row.id)}">
            <input class="registry-check" type="checkbox" aria-label="选择 ${escapeHtml(text.name)}">
            <button type="button" class="record" data-action="select" aria-label="查看 ${escapeHtml(text.name)} 详情"><span class="record-icon"><i class="bi ${icons[resource === 'datasource' ? 0 : resource === 'template' ? 1 : 2]}" aria-hidden="true"></i></span><span class="record-copy"><b class="record-name">${escapeHtml(text.name)}</b><small class="record-sub">${escapeHtml(text.sub)}</small></span></button>
            <span class="tag" title="${escapeHtml(text.relation)}">${escapeHtml(text.relation)}</span><span>${escapeHtml(text.tool)}</span>${formatStatusTag(row)}
            <button type="button" class="row-menu" data-action="menu" aria-label="打开 ${escapeHtml(text.name)} 操作"><i class="bi bi-three-dots" aria-hidden="true"></i></button>
        </div>`;
    }

    function inspectorHtml(row, resource) {
        if (!row) return '<div class="inspector-head"><div class="inspector-title">选择一条记录</div><div class="inspector-sub">从左侧注册表查看资源详情。</div></div><div class="inspector-body"><div class="inspector-note">所有敏感字段仅在写入请求中使用，查询响应不会回显密文或密码。</div></div>';
        const text = valueText(row, resource);
        let detail = '';
        if (resource === 'datasource') detail = `<div class="inspector-label"><span>连接摘要</span><strong>${row.readOnly !== false ? '只读保护' : '请检查策略'}</strong></div><div class="detail-grid"><div class="detail-item">主机<strong>${escapeHtml(row.host)}:${escapeHtml(row.port)}</strong></div><div class="detail-item">数据库<strong>${escapeHtml(row.database)}</strong></div><div class="detail-item">用户名<strong>${escapeHtml(row.username)}</strong></div><div class="detail-item">密码<strong>•••••••• · 不回显</strong></div></div><div class="inspector-note">连接测试只返回健康状态，不在页面或日志展示完整 JDBC 信息。</div>`;
        if (resource === 'template') detail = `<div class="inspector-label"><span>SQL 预览</span><strong>已校验</strong></div><pre class="code-preview">${highlightSql(row.sql || 'SELECT ...')}</pre><div class="inspector-label"><span>参数配置</span><strong>${(row.params || []).length} 个参数</strong></div><div class="detail-grid">${(row.params || []).slice(0, 6).map(param => `<div class="detail-item">${escapeHtml(param.name || param.key)}<strong>${escapeHtml(param.type || 'STRING')}</strong></div>`).join('') || '<div class="detail-item">无参数<strong>固定查询</strong></div>'}</div><div class="inspector-label"><span>执行护栏</span><strong>只读保护</strong></div><div class="inspector-note">${escapeHtml(row.guards || '保存时执行 SQL 安全责任链。')}</div>`;
        if (resource === 'binding') detail = `<div class="inspector-label"><span>绑定关系</span><strong>可追踪</strong></div><div class="detail-grid"><div class="detail-item">Gateway<strong>${escapeHtml(row.gateway)}</strong></div><div class="detail-item">Tool 名称<strong>${escapeHtml(row.toolName)}</strong></div><div class="detail-item">SQL 模板<strong>${escapeHtml(row.templateName)}</strong></div><div class="detail-item">数据源<strong>${escapeHtml(row.datasource)}</strong></div></div><div class="inspector-note">${escapeHtml(row.protocol)} · 停用后不会出现在 tools/list，也不能被 tools/call 调用。</div>`;
        const primaryAction = resource === 'template' || resource === 'datasource' ? `<button type="button" class="button primary" data-inspector-action="test"><i class="bi bi-play" aria-hidden="true"></i>${resource === 'template' ? '测试运行' : '连接测试'}</button>` : '';
        return `<div class="inspector-head"><span class="tag ${statusClass(row.status)} inspector-status">● ${escapeHtml(statusLabel(row))}</span><div class="inspector-title">${escapeHtml(resource === 'binding' ? `${row.templateName} → ${row.toolName}` : text.name)}</div><div class="inspector-sub">${escapeHtml(text.sub || text.relation)} · 更新于 ${escapeHtml(row.updatedAt || '刚刚')}</div></div><div class="inspector-body"><div class="inspector-tabs" role="tablist"><button type="button" class="inspector-tab active" role="tab">概览</button><button type="button" class="inspector-tab" role="tab">关联</button><button type="button" class="inspector-tab" role="tab">变更记录</button></div>${detail}</div><div class="inspector-actions">${primaryAction}<button type="button" class="button" data-inspector-action="edit"><i class="bi bi-pencil" aria-hidden="true"></i>编辑</button><button type="button" class="button" data-inspector-action="toggle"><i class="bi bi-power" aria-hidden="true"></i>${row.status === 'enabled' ? '停用' : '启用'}</button><button type="button" class="button danger" data-inspector-action="delete" aria-label="删除资源"><i class="bi bi-trash" aria-hidden="true"></i></button></div>`;
    }

    function highlightSql(sql) {
        const escaped = escapeHtml(sql);
        return escaped.replace(/\b(SELECT|FROM|JOIN|ON|WHERE|GROUP BY|ORDER BY|BETWEEN|AND|AS|COUNT|SUM)\b/gi, '<span class="keyword">$1</span>').replace(/(:[A-Za-z][A-Za-z0-9_]*)/g, '<span class="param">$1</span>');
    }

    function initOptions(root, resource) {
        const datasourceRefOptions = previews.datasource.map(row => `<option value="${escapeHtml(row.datasourceRef || row.id)}">${escapeHtml(row.name)}</option>`).join('');
        const datasourceIdOptions = previews.datasource.map(row => `<option value="${escapeHtml(row.id)}">${escapeHtml(row.name)}</option>`).join('');
        if (resource === 'template') {
            root.find('#template-datasource, #template-datasource-filter').each(function () { $(this).append(datasourceRefOptions); });
        }
        if (resource === 'binding') {
            root.find('#binding-datasource').append(datasourceIdOptions);
            root.find('#binding-template').append(previews.template.map(row => `<option value="${escapeHtml(row.id)}">${escapeHtml(row.name)}</option>`).join(''));
            const gateways = ['gw-commerce-prod|gateway-commerce-prod', 'gw-analytics-dev|gateway-analytics-dev', 'gw-internal|gateway-internal'];
            root.find('#binding-gateway, #binding-gateway-filter').append(gateways.map(item => { const parts = item.split('|'); return `<option value="${parts[0]}">${parts[1]}</option>`; }).join(''));
        }
    }

    function collectForm(root, resource) {
        const form = root.find('form')[0];
        const raw = {};
        $(form).serializeArray().forEach(item => { raw[item.name] = item.value; });
        if (resource === 'datasource') { raw.port = Number(raw.port); raw.readOnly = raw.readOnly === 'true'; raw.status = Number(raw.status); if (!raw.password) delete raw.password; }
        if (resource === 'template') { raw.maxRows = Number(raw.maxRows); raw.timeoutMs = Number(raw.timeoutMs); raw.maxBytes = Number(raw.maxBytes); raw.status = Number(raw.status); try { raw.params = raw.params ? JSON.parse(raw.params) : []; } catch (e) { raw.__paramsInvalid = true; } }
        if (resource === 'binding') raw.status = Number(raw.status);
        return raw;
    }

    function validateForm(root, resource, data) {
        let valid = true;
        root.find('.field').removeClass('invalid');
        const required = resource === 'datasource' ? ['name', 'database', 'host', 'port', 'username'] : resource === 'template' ? ['name', 'datasourceId', 'sql'] : ['gatewayId', 'toolName', 'templateId', 'datasourceId'];
        required.forEach(name => { const input = root.find(`[name="${name}"]`); const field = input.closest('.field'); if (!input.prop('disabled') && !data[name]) { field.addClass('invalid'); valid = false; } });
        if (resource === 'datasource' && !data.id && !data.password) { root.find('[name="password"]').closest('.field').addClass('invalid'); valid = false; }
        if (resource === 'template' && data.__paramsInvalid) { root.find('[name="params"]').closest('.field').addClass('invalid'); valid = false; }
        return valid;
    }

    function serializeRequest(resource, data) {
        const clean = $.extend({}, data);
        delete clean.__paramsInvalid;
        if (resource === 'datasource') {
            const datasourceRef = String(clean.datasourceRef || clean.name || '').trim().replace(/\s+/g, '-');
            const payload = {
                id: clean.id ? Number(clean.id) : undefined,
                datasourceRef: datasourceRef,
                datasourceName: String(clean.name || '').trim(),
                datasourceType: 'mysql',
                jdbcUrl: `jdbc:mysql://${String(clean.host || '').trim()}:${Number(clean.port)}/${String(clean.database || '').trim()}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`,
                username: String(clean.username || '').trim(),
                password: clean.password,
                encryptionKeyRef: typeof MYSQL_DATASOURCE_KEY_REF === 'undefined' ? undefined : MYSQL_DATASOURCE_KEY_REF,
                status: Number(clean.status)
            };
            if (!payload.password) delete payload.password;
            return payload;
        }
        if (resource === 'template') {
            const protocolId = clean.id ? Number(clean.id) : undefined;
            const datasourceRef = String(clean.datasourceRef || clean.datasourceId || '').trim();
            return {
                protocolId: protocolId,
                version: String(clean.version || '1.0.0').trim(),
                name: String(clean.name || '').trim(),
                description: String(clean.description || '').trim(),
                datasourceRef: datasourceRef,
                sql: String(clean.sql || '').trim(),
                parameters: Array.isArray(clean.params) ? clean.params : [],
                maxRows: Number(clean.maxRows),
                maxResultBytes: Number(clean.maxBytes),
                maxColumns: clean.maxColumns == null || clean.maxColumns === '' ? undefined : Number(clean.maxColumns),
                timeoutMs: Number(clean.timeoutMs),
                status: Number(clean.status)
            };
        }
        if (resource === 'binding') clean.bindingId = clean.id || undefined;
        return clean;
    }

    function request(endpoint, method, payload, onSuccess, onError, onComplete) {
        if (!endpoint) { onError({ info: '接口尚未配置' }); if (onComplete) onComplete(); return; }
        $.ajax({ url: endpoint, type: method || 'GET', data: method === 'GET' ? payload : JSON.stringify(payload), contentType: method === 'GET' ? undefined : 'application/json', success: onSuccess, error: function (xhr) { let response = {}; try { response = JSON.parse(xhr.responseText || '{}'); } catch (e) { /* 安全回退为统一错误 */ } onError(response); }, complete: onComplete });
    }

    function requestParams(endpoint, payload, onSuccess, onError) {
        if (!endpoint) { onError({ info: '接口尚未配置' }); return; }
        $.ajax({ url: endpoint, type: 'POST', data: payload, success: onSuccess, error: function (xhr) { let response = {}; try { response = JSON.parse(xhr.responseText || '{}'); } catch (e) { /* 安全回退为统一错误 */ } onError(response); } });
    }

    window.initMysqlWorkbench = function (resource) {
        const root = $(`.workbench-page[data-resource="${resource}"]`);
        if (!root.length) return;
        const config = configs[resource];
        const state = { rows: previews[resource].slice(), total: previews[resource].length, page: 1, pageSize: 5, selectedId: previews[resource][0] && previews[resource][0].id, statusFilter: 'all', search: '', sort: 'updated', apiAvailable: false };
        root.find(`#${resource === 'template' ? 'template' : resource}-kpis`).html(kpiHtml(config.kpis));
        initOptions(root, resource);

        function filteredRows() {
            const query = state.search.trim().toLowerCase();
            let list = state.rows.filter(row => {
                const matchesStatus = state.statusFilter === 'all' || (state.statusFilter === 'mysql' && resource === 'datasource') || (state.statusFilter === 'enabled' && row.status === 'enabled') || (state.statusFilter === 'draft' && row.status !== 'enabled') || (state.statusFilter === 'attention' && row.status === 'attention') || (state.statusFilter === 'mine');
                const matchesRelation = (!state.datasourceFilter || String(row.datasourceId) === String(state.datasourceFilter)) && (!state.gatewayFilter || String(row.gatewayId) === String(state.gatewayFilter));
                const haystack = JSON.stringify(row).toLowerCase();
                return matchesStatus && matchesRelation && (!query || haystack.indexOf(query) >= 0);
            });
            if (state.sort === 'name') list.sort((a, b) => String(a.name || a.toolName || a.templateName).localeCompare(String(b.name || b.toolName || b.templateName), 'zh-CN'));
            return list;
        }

        function render() {
            const all = filteredRows();
            const total = state.apiAvailable ? state.total : all.length;
            const startIndex = (state.page - 1) * state.pageSize;
            const visible = state.apiAvailable ? state.rows : all.slice(startIndex, startIndex + state.pageSize);
            if (!state.selectedId && visible[0]) state.selectedId = visible[0].id;
            root.find(`#${resource}-rows`).html(visible.length ? visible.map(row => rowHtml(row, resource, state.selectedId)).join('') : '<div class="empty-state">暂无匹配记录</div>');
            const selected = state.rows.find(row => String(row.id) === String(state.selectedId)) || visible[0];
            root.find(`#${resource}-inspector`).html(inspectorHtml(selected, resource));
            const pageCount = Math.max(1, Math.ceil(total / state.pageSize));
            if (state.page > pageCount) state.page = pageCount;
            const shownStart = total ? startIndex + 1 : 0;
            const shownEnd = Math.min(startIndex + visible.length, total);
            root.find(`#${resource}-page-info`).text(`显示 ${shownStart} 到 ${shownEnd} 条，共 ${total} 条`);
            let pagination = `<button type="button" data-page="${state.page - 1}" ${state.page <= 1 ? 'disabled' : ''} aria-label="上一页">‹</button>`;
            for (let page = 1; page <= pageCount && page <= 5; page += 1) pagination += `<button type="button" data-page="${page}" class="${page === state.page ? 'active' : ''}" aria-label="第 ${page} 页">${page}</button>`;
            pagination += `<button type="button" data-page="${state.page + 1}" ${state.page >= pageCount ? 'disabled' : ''} aria-label="下一页">›</button>`;
            root.find(`#${resource}-pagination`).html(pagination);
            if (config.countId) $(`#${config.countId}`).text(String(total).padStart(2, '0'));
        }

        function loadRows() {
            const params = { page: state.page, rows: state.pageSize, keyword: state.search };
            if (state.statusFilter !== 'all' && state.statusFilter !== 'mysql' && state.statusFilter !== 'mine') params.status = state.statusFilter === 'enabled' ? 1 : 0;
            if (resource === 'binding' && state.gatewayFilter) params.gatewayId = state.gatewayFilter;
            request(config.endpoints.page, 'GET', params, function (response) {
                const page = normalizePage(response, resource);
                if (response && (response.code === '0000' || response.code === 0 || response.success === true)) { state.rows = page.list; state.total = page.total; state.apiAvailable = true; state.selectedId = page.list[0] && page.list[0].id; render(); }
            }, function () { state.apiAvailable = false; render(); });
        }

        function loadDetail(row) {
            if (!row || !config.endpoints.detail) return;
            const identity = resource === 'datasource' ? { datasourceRef: row.datasourceRef }
                : resource === 'template' ? { protocolId: row.id, version: row.version } : { id: row.id };
            request(config.endpoints.detail, 'GET', identity, function (response) {
                if (!response || !(response.code === '0000' || response.code === 0 || response.success === true)) return;
                const detail = response.data && (response.data.data || response.data.item || response.data);
                if (!detail || typeof detail !== 'object') return;
                const index = state.rows.findIndex(item => String(item.id) === String(row.id));
                if (index < 0) return;
                state.rows[index] = normalizeItem($.extend({}, state.rows[index], detail), resource);
                render();
            }, function () { /* 详情接口不可用时保留当前安全快照 */ });
        }

        function openDrawer(row) {
            const drawer = root.find(`#${resource}-drawer`);
            const form = root.find('form');
            form[0].reset();
            form.find('[name="id"]').val(row ? row.id : '');
            root.find('.drawer-alert').removeClass('show').text('');
            root.find('.field').removeClass('invalid');
            root.find(`#${resource}-drawer-title`).text(row ? `编辑${config.title}` : `新建${config.title}`);
            if (row) {
                Object.keys(row).forEach(key => { const field = form.find(`[name="${key}"]`); if (field.length && key !== 'password' && key !== 'params') field.val(row[key]); });
                if (resource === 'template') {
                    form.find('[name="version"]').val(row.version || '1.0.0');
                    form.find('[name="datasourceId"]').val(row.datasourceRef || row.datasourceId || '');
                    form.find('[name="params"]').val(JSON.stringify(row.params || [], null, 2));
                }
                if (resource === 'datasource') form.find('[name="readOnly"]').val(String(row.readOnly !== false));
                if (resource === 'binding') { form.find('[name="templateId"]').val(row.templateId); form.find('[name="datasourceId"]').val(row.datasourceId); form.find('[name="gatewayId"]').val(row.gatewayId); }
                form.find('[name="status"]').val(row.status === 'enabled' ? '1' : '0');
            }
            if (resource === 'template' && row && row.status === 'enabled') { drawer.addClass('immutable'); form.find('[name="sql"], [name="datasourceId"], [name="params"]').prop('disabled', true); }
            else { drawer.removeClass('immutable'); form.find('[name="sql"], [name="datasourceId"], [name="params"]').prop('disabled', false); }
            drawer.addClass('open');
            setTimeout(() => drawer.find('input:not([type="hidden"]), select, textarea').first().trigger('focus'), 30);
        }

        function closeDrawer() { root.find('.resource-drawer').removeClass('open'); }

        function saveForm(event) {
            event.preventDefault();
            const form = root.find('form');
            const submit = form.find('button[type="submit"]');
            const data = collectForm(root, resource);
            if (!validateForm(root, resource, data)) { showToast('请检查表单中的必填项和格式。', false); return; }
            if (resource === 'binding') {
                const duplicate = state.rows.some(row => String(row.id) !== String(data.id || '') && String(row.gatewayId) === String(data.gatewayId) && String(row.toolName || '').trim().toLowerCase() === String(data.toolName || '').trim().toLowerCase());
                if (duplicate) { root.find('.drawer-alert').addClass('show').text('同一 Gateway 下已存在同名 Tool，请更换工具名称。'); showToast('工具名称已存在', false); return; }
            }
            submit.prop('disabled', true).addClass('loading').text('保存中...');
            request(config.endpoints.save, 'POST', serializeRequest(resource, data), function (response) {
                if (response && (response.code === '0000' || response.success === true)) { showToast(`${config.title}保存成功`); closeDrawer(); loadRows(); }
                else { root.find('.drawer-alert').addClass('show').text(apiError(response, '保存失败')); }
            }, function (response) { root.find('.drawer-alert').addClass('show').text(apiError(response, '保存失败，请检查管理 API')); }, function () {
                submit.prop('disabled', false).removeClass('loading').html(`<i class="bi bi-check2-circle" aria-hidden="true"></i>保存${config.title}`);
            });
        }

        function changeStatus(row) {
            const nextStatus = row.status === 'enabled' ? 0 : 1;
            const identity = resource === 'datasource' ? { datasourceRef: row.datasourceRef, status: nextStatus }
                : resource === 'template' ? { protocolId: row.id, version: row.version, status: nextStatus } : { id: row.id, status: nextStatus };
            requestParams(config.endpoints.status, identity, function (response) { if (response && (response.code === '0000' || response.success === true)) { row.status = nextStatus ? 'enabled' : 'draft'; row.statusLabel = nextStatus ? '已启用' : '停用'; render(); showToast(nextStatus ? '资源已启用' : '资源已停用'); } else showToast(apiError(response, '状态更新失败'), false); }, function (response) { showToast(apiError(response, '状态更新失败'), false); });
        }

        async function removeRow(row) {
            const title = resource === 'binding' ? row.toolName : row.name;
            if (!await confirmAction(`确定删除“${title}”吗？删除前会检查引用关系，操作不可撤销。`)) return;
            const identity = resource === 'datasource' ? { datasourceRef: row.datasourceRef }
                : resource === 'template' ? { protocolId: row.id, version: row.version } : { id: row.id };
            requestParams(config.endpoints.remove, identity, function (response) { if (response && (response.code === '0000' || response.success === true)) { state.rows = state.rows.filter(item => String(item.id) !== String(row.id)); state.selectedId = state.rows[0] && state.rows[0].id; render(); showToast('资源已删除'); } else showToast(apiError(response, '删除失败'), false); }, function (response) { showToast(apiError(response, '删除失败，请检查引用关系'), false); });
        }

        function testRow(row) {
            if (!config.endpoints.test) { showToast('该资源暂未配置测试接口', false); return; }
            const button = root.find('[data-inspector-action="test"]');
            const original = button.html();
            const payload = resource === 'datasource' ? { datasourceRef: row.datasourceRef } : { id: row.id, version: row.version };
            button.prop('disabled', true).html('<span class="spinner-border spinner-border-sm" aria-hidden="true"></span> 测试中...');
            request(config.endpoints.test, 'POST', payload, function (response) { if (response && (response.code === '0000' || response.success === true)) showToast(resource === 'datasource' ? '连接测试通过' : '模板测试完成'); else showToast(apiError(response, '测试未通过'), false); }, function (response) { showToast(apiError(response, '测试请求失败'), false); }, function () { button.prop('disabled', false).html(original); });
        }

        root.off('.mysql-workbench');
        root.on('click.mysql-workbench', '.tab-button', function () { root.find('.tab-button').removeClass('active').attr('aria-selected', 'false'); $(this).addClass('active').attr('aria-selected', 'true'); state.statusFilter = $(this).data('status-filter'); state.page = 1; render(); loadRows(); });
        let searchTimer = null;
        root.on('input.mysql-workbench', `#${resource}-search`, function () { state.search = this.value; state.page = 1; render(); clearTimeout(searchTimer); searchTimer = setTimeout(loadRows, 280); });
        root.on('change.mysql-workbench', `#${resource}-sort`, function () { state.sort = this.value; render(); });
        root.on('change.mysql-workbench', '#template-datasource-filter, #binding-gateway-filter', function () { state.gatewayFilter = this.value; state.datasourceFilter = this.value; state.page = 1; render(); loadRows(); });
        root.on('click.mysql-workbench', `#${resource}-create`, function () { openDrawer(null); });
        root.on('click.mysql-workbench', '[data-close-drawer]', closeDrawer);
        root.on('submit.mysql-workbench', 'form', saveForm);
        root.on('click.mysql-workbench', '.registry-row', function (event) { if ($(event.target).closest('input, button').length && !$(event.target).closest('[data-action="select"], .record').length) return; const id = $(this).data('row-id'); state.selectedId = id; const row = state.rows.find(item => String(item.id) === String(id)); render(); loadDetail(row); });
        root.on('click.mysql-workbench', '[data-action="select"]', function () { state.selectedId = $(this).closest('.registry-row').data('row-id'); const row = state.rows.find(item => String(item.id) === String(state.selectedId)); render(); loadDetail(row); });
        root.on('click.mysql-workbench', '[data-action="menu"]', function () { const row = state.rows.find(item => String(item.id) === String($(this).closest('.registry-row').data('row-id'))); if (row) openDrawer(row); });
        root.on('click.mysql-workbench', '[data-inspector-action]', function () { const row = state.rows.find(item => String(item.id) === String(state.selectedId)); if (!row) return; const action = $(this).data('inspector-action'); if (action === 'edit') openDrawer(row); if (action === 'toggle') changeStatus(row); if (action === 'delete') removeRow(row); if (action === 'test') testRow(row); });
        root.on('click.mysql-workbench', '[data-page]', function () { if (this.disabled) return; state.page = Number($(this).data('page')); render(); loadRows(); });
        root.on('click.mysql-workbench', '#template-test', function () { const row = state.rows.find(item => String(item.id) === String(state.selectedId)); if (row) testRow(row); });
        $(document).off('input.mysql-global', '#global-search-input').on('input.mysql-global', '#global-search-input', function () { state.search = this.value; state.page = 1; root.find(`#${resource}-search`).val(this.value); render(); clearTimeout(searchTimer); searchTimer = setTimeout(loadRows, 280); });
        $(document).off('keydown.mysql-workbench').on('keydown.mysql-workbench', function (event) { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); root.find(`input[type="search"]`).first().trigger('focus'); } if (event.key === 'Escape') closeDrawer(); });

        render();
        loadRows();
    };
}(window, jQuery));
