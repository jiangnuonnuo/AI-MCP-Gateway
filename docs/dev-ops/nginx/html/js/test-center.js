/* Unified test center: template validation, manual Tool call, and Agent scheduling. */
(function (window, $) {
    'use strict';

    const state = { mode: 'template', gateways: [], templates: [], template: null, tools: [], fields: [], values: {}, args: {}, filter: 'all', raw: false, output: 'response', report: null, request: null, running: false };
    const labels = {
        template: { request: '01 / TEMPLATE', title: '模板参数', execution: '02 / EXECUTION', run: '运行预检' },
        tool: { request: '01 / TOOL REQUEST', title: '调用参数', execution: '02 / TOOL CALL', run: '运行 Tool' },
        agent: { request: '01 / AGENT PROMPT', title: '调度请求', execution: '02 / AGENT TRACE', run: '运行 Agent' }
    };

    function esc(value) { return $('<div>').text(value == null ? '' : String(value)).html(); }
    function valueAt(object, path) { return path.split('.').reduce((current, key) => current == null ? undefined : current[key], object); }
    function setValue(object, path, value) { const parts = path.split('.'); let cursor = object; parts.forEach((part, i) => { if (i === parts.length - 1) cursor[part] = value; else cursor = cursor[part] || (cursor[part] = {}); }); }
    function normalizeType(type) { const t = String(type || 'string').toLowerCase(); return t === 'int' || t === 'long' || t === 'bigint' ? 'integer' : t === 'decimal' ? 'number' : t; }
    function isBlank(value) { return value === undefined || value === null || value === ''; }
    function formatJSON(value) { try { return JSON.stringify(value == null ? {} : value, null, 2); } catch (e) { return String(value); } }

    function init() {
        const root = $('#test-center-root');
        if (!root.length) return;
        bindEvents();
        loadGateways();
        loadTemplates();
        setMode('template');
        renderTrace([]);
    }

    function bindEvents() {
        $(document).off('click.testCenter', '.tc-mode').on('click.testCenter', '.tc-mode', function () { setMode($(this).data('mode')); });
        $(document).off('click.testCenter', '#tc-collapse').on('click.testCenter', '#tc-collapse', function () { const collapsed = $('#tc-shell').toggleClass('parameters-collapsed').hasClass('parameters-collapsed'); $(this).attr('aria-expanded', String(!collapsed)); $(this).attr('aria-label', collapsed ? '展开参数面板' : '收起参数面板'); });
        $(document).off('change.testCenter', '#tc-template-ref').on('change.testCenter', '#tc-template-ref', function () { selectTemplate($(this).val()); });
        $(document).off('click.testCenter', '#tc-refresh-templates').on('click.testCenter', '#tc-refresh-templates', loadTemplates);
        $(document).off('change.testCenter', '#tc-tool-gateway').on('change.testCenter', '#tc-tool-gateway', function () { loadTools($(this).val()); });
        $(document).off('change.testCenter', '#tc-agent-gateway').on('change.testCenter', '#tc-agent-gateway', function () { loadAgentAuthOptions($(this).val()); });
        $(document).off('change.testCenter', '#tc-tool-name').on('change.testCenter', '#tc-tool-name', function () { const tool = state.tools.find(item => item.name === $(this).val()); $('#tc-tool-desc').text(tool && tool.description ? tool.description : '这里只选择已有 Tool，不创建或修改绑定。'); setToolFields(tool); });
        $(document).off('input.testCenter', '#tc-param-search').on('input.testCenter', '#tc-param-search', renderGroups);
        $(document).off('click.testCenter', '.tc-filter').on('click.testCenter', '.tc-filter', function () { $('.tc-filter').removeClass('active'); $(this).addClass('active'); state.filter = $(this).data('filter'); renderGroups(); });
        $(document).off('click.testCenter', '.tc-group-head').on('click.testCenter', '.tc-group-head', function () { $(this).closest('.tc-param-group').toggleClass('collapsed'); });
        $(document).off('input.testCenter change.testCenter', '.tc-param-input').on('input.testCenter change.testCenter', '.tc-param-input', function () { updateField($(this)); });
        $(document).off('click.testCenter', '#tc-toggle-json').on('click.testCenter', '#tc-toggle-json', toggleRaw);
        $(document).off('click.testCenter', '#tc-reset').on('click.testCenter', '#tc-reset', reset);
        $(document).off('click.testCenter', '#tc-run').on('click.testCenter', '#tc-run', run);
        $(document).off('click.testCenter', '#tc-cancel').on('click.testCenter', '#tc-cancel', cancelRun);
        $(document).off('click.testCenter', '[data-output]').on('click.testCenter', '[data-output]', function () { state.output = $(this).data('output'); renderOutput(); });
        $(document).off('click.testCenter', '#tc-copy-json').on('click.testCenter', '#tc-copy-json', copyOutput);
        $(document).off('click.testCenter', '#tc-transport button').on('click.testCenter', '#tc-transport button', function () { $('#tc-transport button').removeClass('active'); $(this).addClass('active'); });
    }

    function setMode(mode) {
        state.mode = mode || 'template'; state.fields = []; state.values = {}; state.args = {}; state.tools = []; state.raw = false;
        const text = labels[state.mode];
        $('.tc-mode').removeClass('active').filter('[data-mode="' + state.mode + '"]').addClass('active');
        $('#tc-request-kicker').text(text.request); $('#tc-param-title').text(text.title); $('#tc-execution-kicker').text(text.execution); $('#tc-run span').text(text.run);
        $('#tc-template-config').toggleClass('tc-hidden', state.mode !== 'template'); $('#tc-tool-config').toggleClass('tc-hidden', state.mode !== 'tool'); $('#tc-agent-config').toggleClass('tc-hidden', state.mode !== 'agent'); $('#tc-schema-parameters').toggleClass('tc-hidden', state.mode === 'agent');
        $('#tc-tool-name').prop('disabled', true).html('<option value="">请先选择 Gateway</option>');
        if (state.mode === 'tool') $('#tc-tool-gateway').val('');
        if (state.mode === 'agent') { $('#tc-agent-gateway').val(''); resetAgentAuthOptions(); }
        $('#tc-param-groups').html('<div class="tc-help">等待加载参数定义。</div>'); $('#tc-param-count').text(state.mode === 'agent' ? 'Agent 不使用手动参数' : '等待加载参数定义'); $('#tc-param-ratio').text('0 / 0'); $('#tc-progress-bar').css('width', '0%'); $('#tc-raw-arguments').val('{}').addClass('tc-hidden'); $('#tc-param-groups,.tc-parameter-tools,.tc-progress').removeClass('tc-hidden'); $('#tc-toggle-json').text('切换到 JSON 编辑'); $('#tc-json-error').text(''); state.report = null; state.request = null; renderTrace([]); clearOutput();
        if (state.mode === 'template') {
            updateTemplateMeta(state.template);
            if (state.template) {
                state.fields = templateFields(state.template);
                renderGroups();
            } else {
                $('#tc-param-groups').html('<div class="tc-help">请从上方选择 SQL 模板。</div>');
            }
        }
        if (state.mode === 'tool' || state.mode === 'agent') loadGateways();
    }

    function loadGateways() {
        $.ajax({ url: API_ENDPOINTS.GET_GATEWAY_LIST, type: 'GET' }).done(function (response) {
            // Gateway status 表示认证校验模式（0-不校验，1-强校验），不是 Gateway 启停状态。
            // Agent 测试需要展示所有可用 Gateway，不能因为 status=0 隐藏未开启认证校验的网关。
            state.gateways = response && response.code === '0000' && Array.isArray(response.data)
                ? response.data.filter(function (gateway) { return gateway && gateway.gatewayId; })
                : [];
            ['#tc-tool-gateway', '#tc-agent-gateway'].forEach(function (selector) { const $select = $(selector); $select.html('<option value="">请选择 Gateway</option>'); state.gateways.forEach(function (gateway) { const $option = $('<option>').val(gateway.gatewayId).text(gateway.gatewayName ? gateway.gatewayName + ' · ' + gateway.gatewayId : gateway.gatewayId).attr('data-auth', gateway.auth === 1 || gateway.auth === '1' ? '1' : '0'); $select.append($option); }); });
        }).fail(function () { $('#tc-tool-gateway,#tc-agent-gateway').html('<option value="">Gateway 加载失败</option>'); showToast('Gateway 列表加载失败，请检查管理接口', false); });
    }

    function resetAgentAuthOptions() {
        $('#tc-agent-auth').prop('disabled', true).html('<option value="">先选择 Gateway</option>');
        $('#tc-agent-auth-meta').text('先选择 Gateway');
        $('#tc-agent-auth-help').text('未开启 Gateway 认证时无需选择 Key。');
    }

    function loadAgentAuthOptions(gatewayId) {
        const $gateway = $('#tc-agent-gateway');
        const $auth = $('#tc-agent-auth');
        if (!gatewayId) { resetAgentAuthOptions(); return; }
        const authRequired = String($gateway.find('option:selected').attr('data-auth')) === '1';
        if (!authRequired) {
            $auth.prop('disabled', false).html('<option value="">无需认证</option>').val('');
            $('#tc-agent-auth-meta').text('optional');
            $('#tc-agent-auth-help').text('当前 Gateway 未开启认证校验。');
            return;
        }
        $auth.prop('disabled', true).html('<option value="">加载认证 Key 中…</option>');
        $('#tc-agent-auth-meta').text('required');
        $('#tc-agent-auth-help').text('当前 Gateway 已开启认证，请选择有效 Key。');
        $.ajax({ url: API_ENDPOINTS.GET_GATEWAY_AUTH_LIST_BY_ID, type: 'GET', data: { gatewayId: gatewayId } }).done(function (response) {
            const rows = response && response.code === '0000' && Array.isArray(response.data) ? response.data : [];
            $auth.empty().append('<option value="">请选择认证 Key</option>');
            rows.forEach(function (item) { if (item && item.apiKey) $auth.append($('<option>').val(item.apiKey).text(item.apiKey)); });
            $auth.prop('disabled', !rows.length);
            if (!rows.length) $('#tc-agent-auth-help').text('该 Gateway 没有有效认证 Key，请先在认证配置中创建。');
        }).fail(function () {
            $auth.prop('disabled', true).html('<option value="">认证 Key 加载失败</option>');
            $('#tc-agent-auth-help').text('认证 Key 加载失败，请检查管理接口。');
        });
    }

    function templateFields(template) {
        return (template && (template.parameters || template.params) || []).map(function (item) {
            return { path: item.name, name: item.name, group: '执行参数', type: normalizeType(item.type), required: !!item.required, description: item.description || '' };
        });
    }

    function templateList(response) {
        const raw = response && response.data;
        const rows = Array.isArray(raw) ? raw : raw && (raw.list || raw.records || raw.rows || raw.data) || [];
        return Array.isArray(rows) ? rows : [];
    }

    function loadTemplates() {
        const $select = $('#tc-template-ref');
        if (!$select.length) return;
        $('#tc-refresh-templates').prop('disabled', true);
        $select.prop('disabled', true).html('<option value="">加载模板中…</option>');
        $.ajax({ url: API_ENDPOINTS.GET_MYSQL_TEMPLATE_PAGE, type: 'GET', data: { page: 1, rows: 200, status: 1 } }).done(function (response) {
            if (!response || response.code !== '0000') {
                state.templates = [];
                $select.html('<option value="">模板加载失败</option>');
                showToast(response && response.info ? response.info : '模板列表加载失败', false);
                return;
            }
            state.templates = templateList(response).map(function (item) {
                return $.extend({}, item, { id: String(item.protocolId || item.id || ''), version: item.version || item.protocolVersion || '1' });
            }).filter(function (item) { return item.id && (item.status === undefined || item.status === 1 || item.status === '1' || item.status === 'ENABLED'); });
            $select.empty().append('<option value="">请选择 SQL 模板</option>');
            state.templates.forEach(function (template) {
                const label = `${template.name || `protocol-${template.id}`} · ${template.id} · v${template.version}`;
                $select.append($('<option>').val(template.id).text(label));
            });
            $select.prop('disabled', !state.templates.length);
            if (state.templates.length) {
                const selectedId = state.template && state.templates.some(function (item) { return item.id === state.template.id; }) ? state.template.id : state.templates[0].id;
                $select.val(selectedId);
                selectTemplate(selectedId, false);
            } else {
                state.template = null;
                state.fields = [];
                updateTemplateMeta(null);
                renderGroups();
            }
        }).fail(function () {
            state.templates = [];
            state.template = null;
            $select.html('<option value="">模板接口不可用</option>');
            updateTemplateMeta(null);
            renderGroups();
            showToast('模板列表加载失败，请检查管理接口', false);
        }).always(function () { $('#tc-refresh-templates').prop('disabled', false); });
    }

    function updateTemplateMeta(template) {
        $('#tc-template-version').text(template && template.version ? template.version : '—');
        $('#tc-template-datasource').text(template && (template.datasourceRef || template.datasource) ? (template.datasourceRef || template.datasource) : '—');
        $('#tc-template-help').text(template ? `${template.name || `protocol-${template.id}`} · 已启用，可执行模板预检。` : '仅展示已启用、可执行的 SQL 模板。');
    }

    function selectTemplate(templateId, clearValues) {
        const template = state.templates.find(function (item) { return item.id === String(templateId); });
        state.template = template || null;
        state.fields = templateFields(template);
        if (clearValues !== false) { state.values = {}; state.args = {}; }
        $('#tc-raw-arguments').val(formatJSON(state.args));
        updateTemplateMeta(template);
        renderGroups();
        clearOutput();
    }

    function loadTools(gatewayId) {
        if (!gatewayId) { $('#tc-tool-name').prop('disabled', true).html('<option value="">请先选择 Gateway</option>'); return; }
        $('#tc-tool-name').prop('disabled', true).html('<option value="">发现 Tool 中…</option>');
        $.ajax({ url: API_ENDPOINTS.TEST_CENTER_TOOLS, type: 'GET', data: { gatewayId: gatewayId } }).done(function (response) { state.tools = response && response.code === '0000' && Array.isArray(response.data) ? response.data : []; const $select = $('#tc-tool-name').empty().append('<option value="">请选择 Tool</option>'); state.tools.forEach(function (tool) { $select.append($('<option>').val(tool.name).text(tool.name)); }); $select.prop('disabled', false); if (!state.tools.length) $('#tc-tool-desc').text('该 Gateway 当前没有可用 Tool。'); }).fail(function () { $('#tc-tool-name').html('<option value="">Tool 发现失败</option>'); showToast('Tool 列表加载失败', false); });
    }

    function setToolFields(tool) { state.values = {}; state.args = {}; state.fields = []; if (tool && tool.inputSchema) flattenSchema(tool.inputSchema, '', tool.name || 'Tool'); renderGroups(); }
    function flattenSchema(schema, prefix, group) { if (!schema) return; const properties = schema.properties || {}; Object.keys(properties).forEach(function (name) { const property = properties[name] || {}; const path = prefix ? prefix + '.' + name : name; if (property.properties && Object.keys(property.properties).length) flattenSchema(property, path, prefix ? group : name); else state.fields.push({ path: path, name: name, group: prefix ? group : '执行参数', type: normalizeType(property.type), required: Array.isArray(schema.required) && schema.required.indexOf(name) >= 0, description: property.description || '' }); }); }

    function visibleFields() { const query = ($('#tc-param-search').val() || '').trim().toLowerCase(); return state.fields.filter(function (field) { const value = valueAt(state.values, field.path); const invalid = field.invalid; const matchesFilter = state.filter === 'all' || (state.filter === 'unfilled' && isBlank(value)) || (state.filter === 'invalid' && invalid); const matchesSearch = !query || field.path.toLowerCase().indexOf(query) >= 0 || field.name.toLowerCase().indexOf(query) >= 0; return matchesFilter && matchesSearch; }); }
    function renderGroups() {
        if (state.raw || state.mode === 'agent') return;
        const fields = visibleFields(); const groups = {}; fields.forEach(function (field) { (groups[field.group] || (groups[field.group] = [])).push(field); }); const $container = $('#tc-param-groups').empty();
        Object.keys(groups).forEach(function (group) { const $group = $('<div class="tc-param-group">'); const $head = $('<button type="button" class="tc-group-head"><span><i class="bi bi-chevron-down me-2"></i>' + esc(group) + '</span><span>' + groups[group].length + ' 个参数</span></button>'); const $fields = $('<div class="tc-group-fields">'); groups[group].forEach(function (field) { $fields.append(renderField(field)); }); $group.append($head, $fields); $container.append($group); });
        if (!fields.length) $container.html('<div class="tc-help">' + (state.mode === 'template' && !state.template ? '请选择一个已启用的 SQL 模板。' : '没有匹配的参数。') + '</div>'); updateProgress();
    }
    function renderField(field) { const value = valueAt(state.values, field.path); const type = field.type; const inputType = type === 'integer' || type === 'number' ? 'number' : type === 'boolean' ? 'checkbox' : 'text'; const checked = type === 'boolean' && value === true ? ' checked' : ''; const val = type === 'boolean' ? '' : (value == null ? '' : ' value="' + esc(value) + '"'); const boolClass = type === 'boolean' ? ' tc-bool' : ''; const description = field.description ? '<div class="tc-help">' + esc(field.description) + '</div>' : ''; return '<div class="tc-param-field" data-path="' + esc(field.path) + '"><div class="tc-label-row"><label class="tc-label" for="tc-param-' + esc(field.path.replace(/[^a-zA-Z0-9_-]/g, '-')) + '">' + esc(field.name) + (field.required ? ' <span class="tc-required">*</span>' : '') + '</label><span class="tc-field-meta">' + esc(type) + '</span></div><input class="tc-control tc-param-input' + boolClass + '" id="tc-param-' + esc(field.path.replace(/[^a-zA-Z0-9_-]/g, '-')) + '" data-path="' + esc(field.path) + '" data-type="' + esc(type) + '" type="' + inputType + '"' + val + checked + ' aria-invalid="' + (field.invalid ? 'true' : 'false') + '"><div class="tc-field-error">' + esc(field.error || '') + '</div>' + description + '</div>'; }

    function updateField($input) { const field = state.fields.find(item => item.path === $input.data('path')); if (!field) return; let value = $input.attr('type') === 'checkbox' ? $input.is(':checked') : $input.val(); if (value !== '' && ($input.data('type') === 'integer' || $input.data('type') === 'number')) value = Number(value); if (value === '') value = undefined; setValue(state.values, field.path, value); validateField(field, value); state.args = buildArgs(); $('#tc-raw-arguments').val(formatJSON(state.args)); const $field = $input.closest('.tc-param-field'); $field.find('.tc-field-error').text(field.error || ''); $input.attr('aria-invalid', field.invalid ? 'true' : 'false'); updateProgress(); }
    function validateField(field, value) { field.invalid = false; field.error = ''; if (field.required && isBlank(value)) { field.invalid = true; field.error = '必填参数'; } else if (!isBlank(value) && field.type === 'integer' && !Number.isInteger(value)) { field.invalid = true; field.error = '需要整数'; } }
    function updateProgress() { const total = state.fields.length; const filled = state.fields.filter(field => !isBlank(valueAt(state.values, field.path))).length; $('#tc-param-count').text(total ? total + ' 个参数' : '暂无参数'); $('#tc-param-ratio').text(filled + ' / ' + total + ' 已填写'); $('#tc-progress-bar').css('width', total ? (filled / total * 100) + '%' : '0%'); }
    function buildArgs() { const result = {}; state.fields.forEach(function (field) { const value = valueAt(state.values, field.path); if (!isBlank(value)) setValue(result, field.path, value); }); return result; }
    function toggleRaw() { state.raw = !state.raw; $('#tc-raw-arguments').toggleClass('tc-hidden', !state.raw); $('#tc-param-groups,.tc-parameter-tools,.tc-progress').toggleClass('tc-hidden', state.raw); $('#tc-toggle-json').text(state.raw ? '切换到表单编辑' : '切换到 JSON 编辑'); if (state.raw) $('#tc-raw-arguments').val(formatJSON(buildArgs())); }
    function reset() { state.values = {}; state.args = {}; state.fields.forEach(field => { field.invalid = false; field.error = ''; }); $('#tc-json-error').text(''); $('#tc-agent-message').val(''); $('#tc-raw-arguments').val('{}'); renderGroups(); clearOutput(); }

    function run() {
        if (state.running) return; const request = collectRequest(); if (!request) return; state.request = request; state.running = true; setStatus('running'); $('#tc-run').prop('disabled', true); renderTrace(defaultSteps()); renderOutput(); const endpoint = state.mode === 'template' ? API_ENDPOINTS.TEST_MYSQL_TEMPLATE : state.mode === 'tool' ? API_ENDPOINTS.TEST_CENTER_TOOL_CALL : API_ENDPOINTS.TEST_AGENT_GATEWAY;
        state.cancelled = false; $('#tc-cancel').removeClass('tc-hidden'); state.xhr = $.ajax({ url: endpoint, type: 'POST', contentType: 'application/json', data: JSON.stringify(request) }).done(function (response) { state.report = response && response.data ? response.data : {}; state.report._envelope = response || {}; applyReport(state.report); }).fail(function (xhr) { if (state.cancelled) return; state.report = { success: false, errorCode: 'HTTP_REQUEST_FAILED', errorMessage: xhr.statusText || '请求失败', _envelope: { status: xhr.status, responseText: xhr.responseText || '' } }; applyReport(state.report); }).always(function () { state.running = false; state.xhr = null; $('#tc-run').prop('disabled', false); $('#tc-cancel').addClass('tc-hidden'); });
    }
    function cancelRun() { if (!state.running || !state.xhr) return; state.cancelled = true; state.xhr.abort(); state.running = false; $('#tc-run').prop('disabled', false); $('#tc-cancel').addClass('tc-hidden'); state.report = { success: false, errorCode: 'REQUEST_CANCELLED', errorMessage: '已取消本次测试', events: [], stages: [] }; applyReport(state.report); }
    function collectRequest() {
        if (state.raw) { try { state.args = JSON.parse($('#tc-raw-arguments').val() || '{}'); $('#tc-json-error').text(''); } catch (e) { $('#tc-json-error').text('JSON 格式错误，无法执行'); return null; } } else { state.args = buildArgs(); }
        if (state.mode === 'template') {
            const id = String($('#tc-template-ref').val() || '').trim();
            if (!id || !state.template) { showToast('请选择 SQL 模板', false); return null; }
            const invalid = state.fields.some(field => { validateField(field, valueAt(state.values, field.path)); return field.invalid; });
            if (invalid && !state.raw) { renderGroups(); showToast('请先补齐必填参数', false); return null; }
            return { id: id, version: state.template.version || null, parameters: state.args };
        }
        if (state.mode === 'tool') { const gatewayId = $('#tc-tool-gateway').val(); const toolName = $('#tc-tool-name').val(); if (!gatewayId || !toolName) { showToast('请选择 Gateway 和 Tool', false); return null; } const invalid = state.fields.some(field => { validateField(field, valueAt(state.values, field.path)); return field.invalid; }); if (invalid && !state.raw) { renderGroups(); showToast('请先补齐必填参数', false); return null; } return { gatewayId: gatewayId, toolName: toolName, arguments: state.args }; }
        const gatewayId = $('#tc-agent-gateway').val(); const message = $('#tc-agent-message').val().trim(); const authRequired = String($('#tc-agent-gateway option:selected').attr('data-auth')) === '1'; const authApiKey = $('#tc-agent-auth').val() || null; if (!gatewayId || !message) { showToast('请选择 Gateway 并填写自然语言请求', false); return null; } if (authRequired && !authApiKey) { showToast('当前 Gateway 已开启认证，请选择有效认证 Key', false); return null; } return { gatewayId: gatewayId, authApiKey: authApiKey, timeout: Math.min(120000, Math.max(1000, Number($('#tc-agent-timeout').val()) || 30000)), message: message, reload: true, mcpType: $('#tc-transport button.active').data('value') || 'sse' };
    }

    function defaultSteps() { if (state.mode === 'template') return ['PARAMETER_VALIDATION', 'TEMPLATE_RESOLUTION', 'SQL_BINDING', 'DATASOURCE_CONNECTION', 'SQL_EXECUTION', 'RESPONSE_ASSEMBLY']; if (state.mode === 'tool') return ['TOOLS_LIST', 'PARAMETER_VALIDATION', 'POLICY_VALIDATION', 'TOOL_EXECUTION', 'RESPONSE_ASSEMBLY']; return ['AGENT_REQUEST', 'TOOLS_LIST_RESPONSE', 'TOOL_CALL_REQUEST', 'TOOL_CALL_RESPONSE', 'AGENT_FINISHED']; }
    function renderTrace(steps, statuses) { const statusMap = statuses || {}; const $trace = $('#tc-trace').empty(); (steps || []).forEach(function (step) { const current = statusMap[step] || 'pending'; const name = String(step).replace(/_/g, ' '); $trace.append('<div class="tc-trace-step ' + current.toLowerCase() + '"><div class="tc-step-state">' + (current === 'succeeded' ? '✓' : current === 'failed' ? '!' : '') + '</div><div class="tc-step-name">' + esc(name) + '</div><div class="tc-step-detail">' + (current === 'pending' ? '等待执行' : current === 'running' ? '执行中…' : current === 'succeeded' ? '已完成' : '失败') + '</div></div>'); }); }
    function applyReport(report) { const success = report.success === true && (!report._envelope || report._envelope.code === '0000'); setStatus(success ? 'succeeded' : 'failed'); $('#tc-request-id').text(report.requestId || report.agentTestId || '—'); $('#tc-query-id').text(report.queryId || '—'); $('#tc-duration').text(report.durationMs == null ? '—' : report.durationMs); $('#tc-trace-note').text(success ? '链路完成' : (report.errorMessage || report._envelope && report._envelope.info || '链路失败')); let steps = defaultSteps(); const statuses = {}; if (state.mode !== 'agent' && Array.isArray(report.stages) && report.stages.length) { steps = report.stages.map(stage => stage.name); report.stages.forEach(stage => { statuses[stage.name] = String(stage.status || 'PENDING').toLowerCase(); }); } else { steps.forEach(step => statuses[step] = success ? 'succeeded' : 'failed'); if (state.mode === 'agent' && Array.isArray(report.events)) report.events.forEach(event => { statuses[event.type] = event.status === 'SUCCEEDED' ? 'succeeded' : event.status === 'FAILED' ? 'failed' : 'running'; }); } renderTrace(steps, statuses); state.output = 'response'; renderOutput(); if (success) showToast('测试完成'); else showToast(report.errorMessage || report._envelope && report._envelope.info || '测试失败', false); }
    function setStatus(status) { const text = { idle: '等待执行', running: '执行中', succeeded: '执行成功', failed: '执行失败' }[status]; $('#tc-head-status').removeClass('idle running succeeded failed').addClass(status).find('span:last-child').text(text); $('#tc-run-badge').removeClass('idle running succeeded failed').addClass(status).text(status.toUpperCase()); }
    function clearOutput() { $('#tc-output-code').addClass('tc-hidden').text(''); $('#tc-output-empty').removeClass('tc-hidden'); $('#tc-request-id,#tc-query-id,#tc-duration').text('—'); setStatus('idle'); }
    function renderOutput() { $('.tc-output-tab').removeClass('active').filter('[data-output="' + state.output + '"]').addClass('active'); const report = state.report; if (!report) { clearOutput(); return; } let content; if (state.output === 'request') content = report.requestJson || state.request; else if (state.output === 'trace') content = report.events || report.stages || []; else if (state.output === 'binding') content = { templateRef: report.templateRef || null, version: report.version || null, datasourceRef: report.datasourceRef || null, toolName: report.toolName || null }; else if (state.output === 'log') content = state.mode === 'agent' ? report.events || [] : report.stages || []; else content = report.responseJson || report.result || report._envelope || report; $('#tc-output-empty').addClass('tc-hidden'); $('#tc-output-code').removeClass('tc-hidden').text(formatJSON(content)); }
    function copyOutput() { const text = $('#tc-output-code').text(); if (!text) return; navigator.clipboard && navigator.clipboard.writeText(text).then(() => showToast('JSON 已复制')); }

    window.initTestCenter = init;
})(window, jQuery);
