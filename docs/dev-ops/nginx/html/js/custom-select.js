(function (window, document, $) {
    'use strict';

    if (!$) return;

    let menuSequence = 0;
    const instances = new Set();
    const originalVal = $.fn.val;
    const originalProp = $.fn.prop;

    function escapeSelector(value) {
        return String(value || '').replace(/[^a-zA-Z0-9_-]/g, '-');
    }

    function optionParts(option) {
        const text = option.textContent.trim();
        const explicitLabel = option.getAttribute('data-label') || option.getAttribute('data-name');
        const explicitMeta = option.getAttribute('data-subtitle') || option.getAttribute('data-id') || option.getAttribute('data-desc');
        const parts = text.split(/\s*[·|｜]\s*/).map(part => part.trim()).filter(Boolean);
        const parenthesizedId = !explicitMeta && parts.length === 1 && parts[0].match(/^(.*?)\s*[（(]([^)）]+)[）)]$/);
        const idIndex = parts.findIndex((part, index) => index > 0 && part === option.value);
        const label = explicitLabel || (parenthesizedId ? parenthesizedId[1] : idIndex > 0 ? parts.slice(0, idIndex).join(' · ') : parts.shift()) || text || '未命名选项';
        const selectId = option.parentElement && option.parentElement.id || '';
        const showValueAsMeta = option.value && option.value !== text && /gateway|datasource|template|protocol/i.test(selectId) && !/sort|status|auth|execution-mode|toolType|protocolType|httpMethod|readonly/i.test(selectId);
        const resourceId = showValueAsMeta ? option.value : '';
        const meta = explicitMeta || (parenthesizedId ? parenthesizedId[2] : idIndex > 0 ? parts.slice(idIndex).join(' · ') : parts.join(' · ')) || resourceId;
        return { label, meta, search: `${label} ${meta} ${option.value}`.toLowerCase() };
    }

    function getLabel(select) {
        if (select.getAttribute('aria-label')) return select.getAttribute('aria-label');
        const label = select.labels && select.labels[0];
        return label ? label.textContent.replace(/[*＊]/g, '').trim() : '选择一项';
    }

    function setLabelId(select) {
        const label = select.labels && select.labels[0];
        if (!label) return null;
        if (!label.id) label.id = `custom-select-label-${++menuSequence}`;
        return label.id;
    }

    function closeAll(except) {
        instances.forEach(instance => {
            if (instance !== except) instance.close();
        });
    }

    function createOption(instance, option, index) {
        const parts = optionParts(option);
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'custom-select-option';
        item.dataset.value = option.value;
        item.dataset.index = String(index);
        item.setAttribute('role', 'option');
        item.setAttribute('aria-selected', option.selected ? 'true' : 'false');
        item.disabled = option.disabled;
        if (option.hidden) item.hidden = true;

        const copy = document.createElement('span');
        copy.className = 'custom-select-option-copy';
        const label = document.createElement('span');
        label.className = 'custom-select-option-label';
        label.textContent = parts.label;
        copy.appendChild(label);
        if (parts.meta) {
            const meta = document.createElement('span');
            meta.className = 'custom-select-option-meta';
            meta.textContent = parts.meta;
            copy.appendChild(meta);
        }
        const check = document.createElement('i');
        check.className = 'bi bi-check2 custom-select-option-check';
        check.setAttribute('aria-hidden', 'true');
        item.append(copy, check);
        item.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            instance.choose(index);
        });
        return { item, parts };
    }

    function createInstance(select) {
        if (select._customSelect) return select._customSelect;

        const shell = document.createElement('div');
        shell.className = 'custom-select-shell';
        shell.dataset.customSelectFor = select.id || '';

        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'custom-select-trigger';
        trigger.setAttribute('role', 'combobox');
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');
        trigger.setAttribute('aria-label', getLabel(select));
        const labelledBy = setLabelId(select);
        if (labelledBy) trigger.setAttribute('aria-labelledby', labelledBy);

        const value = document.createElement('span');
        value.className = 'custom-select-trigger-value';
        const meta = document.createElement('span');
        meta.className = 'custom-select-trigger-meta';
        const arrow = document.createElement('i');
        arrow.className = 'bi bi-chevron-down custom-select-trigger-icon';
        arrow.setAttribute('aria-hidden', 'true');
        trigger.append(value, meta, arrow);

        const menu = document.createElement('div');
        menu.className = 'custom-select-menu';
        menu.hidden = true;
        const menuId = `${escapeSelector(select.id || 'select')}-custom-menu-${++menuSequence}`;
        menu.id = menuId;

        const searchWrap = document.createElement('div');
        searchWrap.className = 'custom-select-search-wrap';
        const searchIcon = document.createElement('i');
        searchIcon.className = 'bi bi-search';
        searchIcon.setAttribute('aria-hidden', 'true');
        const search = document.createElement('input');
        search.type = 'search';
        search.className = 'custom-select-search';
        search.placeholder = select.dataset.searchPlaceholder || '搜索名称或 ID...';
        search.setAttribute('aria-label', `${getLabel(select)}搜索`);
        searchWrap.append(searchIcon, search);
        const options = document.createElement('div');
        options.className = 'custom-select-options';
        options.id = `${menuId}-options`;
        options.setAttribute('role', 'listbox');
        options.setAttribute('aria-label', getLabel(select));
        trigger.setAttribute('aria-controls', options.id);
        const empty = document.createElement('div');
        empty.className = 'custom-select-empty';
        empty.textContent = '无匹配项';
        empty.hidden = true;
        menu.append(searchWrap, options, empty);

        select.classList.add('custom-select-native');
        select.setAttribute('aria-hidden', 'true');
        select.tabIndex = -1;
        select.parentNode.insertBefore(shell, select);
        shell.append(trigger, menu, select);

        const instance = {
            select,
            shell,
            trigger,
            menu,
            search,
            options,
            empty,
            optionItems: [],
            activeIndex: -1,
            open(focusSearch) {
                if (select.disabled) return;
                closeAll(instance);
                instance.sync();
                menu.hidden = false;
                shell.classList.add('is-open');
                trigger.setAttribute('aria-expanded', 'true');
                instance.positionMenu();
                search.value = '';
                instance.filterOptions();
                if (focusSearch !== false) window.requestAnimationFrame(() => { if (!menu.hidden) search.focus(); });
            },
            close(focusTrigger) {
                if (menu.hidden) return;
                menu.hidden = true;
                shell.classList.remove('is-open', 'is-upward');
                trigger.setAttribute('aria-expanded', 'false');
                instance.activeIndex = -1;
                if (focusTrigger) trigger.focus();
            },
            positionMenu() {
                shell.classList.remove('is-upward');
                const rect = shell.getBoundingClientRect();
                const menuWidth = Math.max(rect.width, shell.closest('.filter-control') ? 220 : 0);
                const menuHeight = Math.min(menu.scrollHeight || 300, Math.max(220, window.innerHeight * 0.48));
                const above = rect.bottom + menuHeight + 8 > window.innerHeight && rect.top > menuHeight + 8;
                if (above) shell.classList.add('is-upward');
                menu.style.width = `${Math.min(menuWidth, window.innerWidth - 16)}px`;
                menu.style.left = `${Math.max(8, Math.min(rect.left, window.innerWidth - menuWidth - 8))}px`;
                menu.style.top = above ? 'auto' : `${rect.bottom + 5}px`;
                menu.style.bottom = above ? `${window.innerHeight - rect.top + 5}px` : 'auto';
            },
            renderOptions() {
                options.textContent = '';
                instance.optionItems = [];
                Array.from(select.options).forEach((option, index) => {
                    const created = createOption(instance, option, index);
                    options.appendChild(created.item);
                    instance.optionItems.push({ option, item: created.item, parts: created.parts });
                });
                instance.sync();
                if (!menu.hidden) instance.filterOptions();
            },
            sync() {
                const selected = select.options[select.selectedIndex] || select.options[0];
                const parts = selected ? optionParts(selected) : { label: '', meta: '' };
                value.textContent = parts.label || select.dataset.placeholder || '请选择';
                meta.textContent = parts.meta || '';
                meta.hidden = !parts.meta;
                trigger.disabled = Boolean(select.disabled);
                if (select.required) trigger.setAttribute('aria-required', 'true');
                else trigger.removeAttribute('aria-required');
                if (select.value) shell.classList.remove('is-invalid');
                shell.classList.toggle('is-disabled', Boolean(select.disabled));
                instance.optionItems.forEach(entry => {
                    const selectedState = selected && entry.option === selected;
                    entry.item.classList.toggle('is-selected', Boolean(selectedState));
                    entry.item.setAttribute('aria-selected', selectedState ? 'true' : 'false');
                });
            },
            choose(index) {
                const entry = instance.optionItems[index];
                if (!entry || entry.option.disabled || select.disabled) return;
                const previous = select.value;
                select.value = entry.option.value;
                instance.sync();
                instance.close(true);
                if (previous !== select.value) $(select).trigger('change');
            },
            filterOptions() {
                const query = search.value.trim().toLowerCase();
                let visible = 0;
                instance.optionItems.forEach(entry => {
                    const show = !query || entry.parts.search.indexOf(query) >= 0;
                    entry.item.hidden = !show;
                    if (show && !entry.option.disabled) visible += 1;
                });
                empty.hidden = visible > 0;
                if (instance.activeIndex >= 0 && (!instance.optionItems[instance.activeIndex] || instance.optionItems[instance.activeIndex].item.hidden)) instance.activeIndex = -1;
            },
            move(step) {
                const available = instance.optionItems.map((entry, index) => ({ entry, index })).filter(({ entry }) => !entry.item.hidden && !entry.option.disabled);
                if (!available.length) return;
                let position = available.findIndex(item => item.index === instance.activeIndex);
                position = position < 0 ? (step > 0 ? 0 : available.length - 1) : (position + step + available.length) % available.length;
                instance.activeIndex = available[position].index;
                instance.optionItems[instance.activeIndex].item.focus();
            }
        };

        trigger.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            if (menu.hidden) instance.open(); else instance.close();
        });
        trigger.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                if (menu.hidden) instance.open(false);
                instance.move(event.key === 'ArrowDown' ? 1 : -1);
            } else if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                if (menu.hidden) instance.open(); else if (instance.activeIndex >= 0) instance.choose(instance.activeIndex);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                instance.close(true);
            }
        });
        trigger.addEventListener('focus', () => shell.classList.add('is-focused'));
        trigger.addEventListener('blur', () => shell.classList.remove('is-focused'));
        search.addEventListener('input', instance.filterOptions);
        search.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                instance.move(event.key === 'ArrowDown' ? 1 : -1);
            } else if (event.key === 'Enter') {
                event.preventDefault();
                if (instance.activeIndex < 0) instance.move(1);
                if (instance.activeIndex >= 0) instance.choose(instance.activeIndex);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                instance.close(true);
            }
        });
        options.addEventListener('keydown', event => {
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                instance.move(event.key === 'ArrowDown' ? 1 : -1);
            } else if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                instance.choose(instance.activeIndex);
            } else if (event.key === 'Escape') {
                event.preventDefault();
                instance.close(true);
            }
        });
        menu.addEventListener('keydown', event => {
            if (event.key === 'Tab') instance.close();
        });
        select.addEventListener('change', () => instance.sync());
        select.addEventListener('focus', () => trigger.focus());
        select.addEventListener('invalid', event => {
            event.preventDefault();
            shell.classList.add('is-invalid');
            trigger.focus();
        });
        trigger.addEventListener('click', () => shell.classList.remove('is-invalid'));
        if (select.form) select.form.addEventListener('reset', () => window.requestAnimationFrame(() => instance.sync()));
        const observer = new MutationObserver(() => instance.renderOptions());
        observer.observe(select, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled', 'hidden', 'selected', 'data-label', 'data-name', 'data-subtitle', 'data-id', 'data-desc'] });
        instance.destroyObserver = () => observer.disconnect();
        select._customSelect = instance;
        instances.add(instance);
        instance.renderOptions();
        return instance;
    }

    function initCustomSelects(root) {
        const node = root && root.jquery ? root[0] : root || document;
        if (!node) return;
        const selects = node.matches && node.matches('select') ? [node] : Array.from(node.querySelectorAll ? node.querySelectorAll('select') : []);
        selects.forEach(createInstance);
    }

    $.fn.val = function () {
        const result = originalVal.apply(this, arguments);
        if (arguments.length) this.each(function () { if (this._customSelect) this._customSelect.sync(); });
        return result;
    };
    $.fn.prop = function (name, value) {
        const result = originalProp.apply(this, arguments);
        if (arguments.length > 1 && (name === 'disabled' || name === 'required')) this.each(function () { if (this._customSelect) this._customSelect.sync(); });
        return result;
    };

    document.addEventListener('click', event => {
        instances.forEach(instance => {
            if (!instance.shell.contains(event.target)) instance.close();
        });
    });
    window.addEventListener('resize', () => instances.forEach(instance => { if (!instance.menu.hidden) instance.positionMenu(); }));
    window.addEventListener('scroll', () => instances.forEach(instance => { if (!instance.menu.hidden) instance.positionMenu(); }), true);
    const pageObserver = new MutationObserver(records => {
        records.forEach(record => Array.from(record.addedNodes).forEach(node => {
            if (node.nodeType === 1) initCustomSelects(node);
        }));
        instances.forEach(instance => {
            if (!instance.shell.isConnected) {
                instance.close();
                instance.destroyObserver();
                delete instance.select._customSelect;
                instances.delete(instance);
            }
        });
    });
    pageObserver.observe(document.documentElement, { childList: true, subtree: true });

    window.initCustomSelects = initCustomSelects;
    $(function () { initCustomSelects(document); });
}(window, document, window.jQuery));
