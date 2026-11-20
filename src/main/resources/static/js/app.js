/* EmployeeHub: a small vanilla-JS single-page dashboard for the Employee Management REST API. */
(() => {
  'use strict';

  const API = 'api/v1';

  // ---------------------------------------------------------------- helpers
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  const esc = (value) =>
    String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

  const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
  const dateFmt = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  const fmtDate = (iso) => (iso ? dateFmt.format(new Date(iso + (iso.length === 10 ? 'T00:00:00' : ''))) : '—');
  const STATUS_LABEL = { ACTIVE: 'Active', ON_LEAVE: 'On leave', INACTIVE: 'Inactive' };
  const AVATAR_COLORS = ['#4f46e5', '#0891b2', '#059669', '#d97706', '#db2777', '#7c3aed', '#2563eb', '#dc2626'];

  const initials = (e) => ((e.firstName?.[0] ?? '') + (e.lastName?.[0] ?? '')).toUpperCase() || '?';
  const avatarColor = (id) => AVATAR_COLORS[Number(id) % AVATAR_COLORS.length];

  function debounce(fn, ms) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
  }

  class ApiError extends Error {
    constructor(status, body) {
      super(body?.message || `Request failed (${status})`);
      this.status = status;
      this.fieldErrors = body?.fieldErrors || null;
    }
  }

  async function api(path, { method = 'GET', body } = {}) {
    const res = await fetch(`${API}${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }

  function toast(message, type = 'ok') {
    const el = document.createElement('div');
    el.className = `toast ${type === 'error' ? 'error' : ''}`;
    el.textContent = message;
    $('#toasts').append(el);
    setTimeout(() => el.remove(), 3800);
  }

  // ---------------------------------------------------------------- lookups
  const lookups = { departments: [], roles: [] };

  async function loadLookups() {
    const [departments, roles] = await Promise.all([api('/departments'), api('/roles')]);
    lookups.departments = departments;
    lookups.roles = roles;
  }

  const optionsHtml = (items, label, placeholder) =>
    `<option value="">${esc(placeholder)}</option>` +
    items.map((i) => `<option value="${i.id}">${esc(i[label])}</option>`).join('');

  // ---------------------------------------------------------------- router
  const views = {
    dashboard: { title: 'Dashboard', subtitle: 'Overview of your organisation', render: renderDashboard },
    employees: { title: 'Employees', subtitle: 'Search, add, edit and remove employee records', render: renderEmployees },
    departments: { title: 'Departments', subtitle: 'Teams that employees belong to', render: () => renderCatalog('departments') },
    roles: { title: 'Roles', subtitle: 'Job roles and designations', render: () => renderCatalog('roles') },
  };

  function route() {
    const name = location.hash.replace(/^#\/?/, '').split('?')[0] || 'dashboard';
    const view = views[name] || views.dashboard;
    $$('.nav a').forEach((a) => a.classList.toggle('active', a.dataset.route === name));
    $('#pageTitle').textContent = view.title;
    $('#pageSubtitle').textContent = view.subtitle;
    $('#topbarActions').innerHTML = '';
    document.title = `${view.title} · Employee Management System`;
    closeSidebar();
    view.render().catch((err) => {
      $('#view').innerHTML = `<div class="card empty"><strong>Could not load data</strong>${esc(err.message)}</div>`;
    });
  }

  // ---------------------------------------------------------------- dashboard
  async function renderDashboard() {
    const view = $('#view');
    view.innerHTML = `<div class="stats">${'<div class="card stat"><div class="skeleton"></div></div>'.repeat(4)}</div>`;
    const s = await api('/dashboard');
    const max = Math.max(1, ...s.perDepartment.map((d) => d.count));

    view.innerHTML = `
      <div class="stats">
        <div class="card stat"><div class="label">Total employees</div><div class="value">${s.totalEmployees}</div>
          <div class="hint">${s.activeEmployees} active</div></div>
        <div class="card stat"><div class="label">On leave</div><div class="value">${s.onLeaveEmployees}</div>
          <div class="hint">${s.inactiveEmployees} inactive</div></div>
        <div class="card stat"><div class="label">Departments</div><div class="value">${s.departments}</div>
          <div class="hint"><a href="#/departments">Manage</a></div></div>
        <div class="card stat"><div class="label">Roles</div><div class="value">${s.roles}</div>
          <div class="hint"><a href="#/roles">Manage</a></div></div>
      </div>
      <div class="grid-2">
        <section class="card">
          <div class="card-head"><h2>Headcount by department</h2></div>
          <div class="card-body">
            ${s.perDepartment.length ? `<div class="bars">${s.perDepartment.map((d) => `
              <div class="bar-row">
                <span title="${esc(d.name)}">${esc(d.name)}</span>
                <div class="bar-track"><div class="bar-fill" style="width:${(d.count / max) * 100}%"></div></div>
                <span class="n">${d.count}</span>
              </div>`).join('')}</div>` : '<p class="muted">No departments yet.</p>'}
          </div>
        </section>
        <section class="card">
          <div class="card-head"><h2>Recently added</h2><a href="#/employees">View all</a></div>
          <div class="table-wrap">
            ${s.recentlyAdded.length ? `<table><tbody>${s.recentlyAdded.map((e) => `
              <tr>
                <td>${personCell(e)}</td>
                <td class="hide-sm">${e.department ? `<span class="chip">${esc(e.department.name)}</span>` : '—'}</td>
                <td><span class="badge ${e.status}">${STATUS_LABEL[e.status]}</span></td>
              </tr>`).join('')}</tbody></table>` : '<div class="empty">No employees yet.</div>'}
          </div>
        </section>
      </div>`;
  }

  const personCell = (e) => `
    <div class="person">
      <span class="avatar" style="background:${avatarColor(e.id)}">${esc(initials(e))}</span>
      <div><div class="name">${esc(e.fullName)}</div><div class="email">${esc(e.emailId)}</div></div>
    </div>`;

  // ---------------------------------------------------------------- employees
  const state = { q: '', departmentId: '', roleId: '', status: '', page: 0, size: 10, sort: 'id', direction: 'asc' };

  async function renderEmployees() {
    await loadLookups();
    $('#topbarActions').innerHTML = '<button class="btn primary" id="addEmployee">+ Add employee</button>';
    $('#addEmployee').onclick = () => openEmployeeForm();

    $('#view').innerHTML = `
      <section class="card">
        <div class="toolbar">
          <label class="search">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15.5 14h-.79l-.28-.27A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14"/></svg>
            <input id="q" type="search" placeholder="Search by name or email" aria-label="Search employees" value="${esc(state.q)}">
          </label>
          <select id="fDept" aria-label="Filter by department">${optionsHtml(lookups.departments, 'name', 'All departments')}</select>
          <select id="fRole" aria-label="Filter by role">${optionsHtml(lookups.roles, 'title', 'All roles')}</select>
          <select id="fStatus" aria-label="Filter by status">
            <option value="">All statuses</option><option value="ACTIVE">Active</option>
            <option value="ON_LEAVE">On leave</option><option value="INACTIVE">Inactive</option>
          </select>
        </div>
        <div class="table-wrap" id="empTable"></div>
        <div class="pager" id="pager"></div>
      </section>`;

    $('#fDept').value = state.departmentId;
    $('#fRole').value = state.roleId;
    $('#fStatus').value = state.status;

    $('#q').addEventListener('input', debounce((e) => { state.q = e.target.value; state.page = 0; loadEmployees(); }, 250));
    $('#fDept').onchange = (e) => { state.departmentId = e.target.value; state.page = 0; loadEmployees(); };
    $('#fRole').onchange = (e) => { state.roleId = e.target.value; state.page = 0; loadEmployees(); };
    $('#fStatus').onchange = (e) => { state.status = e.target.value; state.page = 0; loadEmployees(); };

    await loadEmployees();
  }

  const COLUMNS = [
    { key: 'firstName', label: 'Employee' },
    { key: null, label: 'Department' },
    { key: null, label: 'Role', cls: 'hide-sm' },
    { key: 'salary', label: 'Salary', cls: 'hide-sm' },
    { key: 'joiningDate', label: 'Joined', cls: 'hide-sm' },
    { key: 'status', label: 'Status' },
  ];

  async function loadEmployees() {
    const params = new URLSearchParams();
    Object.entries(state).forEach(([k, v]) => { if (v !== '' && v !== null) params.set(k, v); });
    const table = $('#empTable');
    if (!table) return;
    table.innerHTML = `<div class="card-body">${'<div class="skeleton" style="margin:12px 0"></div>'.repeat(5)}</div>`;

    let page;
    try {
      page = await api(`/employees?${params}`);
    } catch (err) {
      table.innerHTML = `<div class="empty"><strong>Could not load employees</strong>${esc(err.message)}</div>`;
      return;
    }
    // Deleting the last row on a page leaves us past the end: step back.
    if (page.content.length === 0 && state.page > 0 && page.totalPages > 0) {
      state.page = page.totalPages - 1;
      return loadEmployees();
    }

    const filtered = state.q || state.departmentId || state.roleId || state.status;
    if (page.content.length === 0) {
      table.innerHTML = `<div class="empty"><strong>${filtered ? 'No matching employees' : 'No employees yet'}</strong>
        ${filtered ? 'Try a different search or clear the filters.' : 'Add your first employee to get started.'}</div>`;
    } else {
      const head = COLUMNS.map((c) => {
        if (!c.key) return `<th class="${c.cls || ''}">${c.label}</th>`;
        const active = state.sort === c.key;
        const arrow = active ? (state.direction === 'asc' ? '▲' : '▼') : '';
        const aria = active ? (state.direction === 'asc' ? 'ascending' : 'descending') : 'none';
        return `<th class="${c.cls || ''}" aria-sort="${aria}"><button data-sort="${c.key}">${c.label} <span class="arrow">${arrow}</span></button></th>`;
      }).join('');

      table.innerHTML = `<table>
        <thead><tr>${head}<th aria-label="Actions"></th></tr></thead>
        <tbody>${page.content.map((e) => `
          <tr>
            <td>${personCell(e)}</td>
            <td>${e.department ? `<span class="chip">${esc(e.department.name)}</span>` : '<span class="muted">—</span>'}</td>
            <td class="hide-sm">${e.role ? esc(e.role.name) : '<span class="muted">—</span>'}</td>
            <td class="hide-sm">${e.salary != null ? money.format(e.salary) : '<span class="muted">—</span>'}</td>
            <td class="hide-sm">${fmtDate(e.joiningDate)}</td>
            <td><span class="badge ${e.status}">${STATUS_LABEL[e.status]}</span></td>
            <td class="actions">
              <button class="icon-btn" data-edit="${e.id}" aria-label="Edit ${esc(e.fullName)}" title="Edit">
                <svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75z"/></svg>
              </button>
              <button class="icon-btn danger" data-delete="${e.id}" data-name="${esc(e.fullName)}" aria-label="Delete ${esc(e.fullName)}" title="Delete">
                <svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z"/></svg>
              </button>
            </td>
          </tr>`).join('')}
        </tbody></table>`;

      $$('[data-sort]', table).forEach((b) => b.onclick = () => {
        const key = b.dataset.sort;
        state.direction = state.sort === key && state.direction === 'asc' ? 'desc' : 'asc';
        state.sort = key;
        loadEmployees();
      });
      $$('[data-edit]', table).forEach((b) => b.onclick = async () => {
        try { openEmployeeForm(await api(`/employees/${b.dataset.edit}`)); } catch (err) { toast(err.message, 'error'); }
      });
      $$('[data-delete]', table).forEach((b) => b.onclick = () => confirmDelete(
        'Delete employee?',
        `${b.dataset.name} will be removed permanently.`,
        async () => {
          await api(`/employees/${b.dataset.delete}`, { method: 'DELETE' });
          toast(`${b.dataset.name} deleted`);
          loadEmployees();
        }));
    }

    renderPager(page);
  }

  function renderPager(page) {
    const pager = $('#pager');
    const from = page.totalElements === 0 ? 0 : page.page * page.size + 1;
    const to = page.page * page.size + page.content.length;
    pager.innerHTML = `
      <span class="muted">Showing ${from}–${to} of ${page.totalElements}</span>
      <div class="pages">
        <label class="muted" for="pageSize">Rows</label>
        <select id="pageSize">${[5, 10, 20, 50].map((n) => `<option ${n === state.size ? 'selected' : ''}>${n}</option>`).join('')}</select>
        <button class="btn ghost sm" id="prevPage" ${page.first ? 'disabled' : ''}>‹ Prev</button>
        <span class="muted">Page ${page.totalPages ? page.page + 1 : 0} of ${page.totalPages}</span>
        <button class="btn ghost sm" id="nextPage" ${page.last ? 'disabled' : ''}>Next ›</button>
      </div>`;
    $('#prevPage').onclick = () => { state.page--; loadEmployees(); };
    $('#nextPage').onclick = () => { state.page++; loadEmployees(); };
    $('#pageSize').onchange = (e) => { state.size = Number(e.target.value); state.page = 0; loadEmployees(); };
  }

  // ---------------------------------------------------------------- forms
  function clearErrors(form) {
    $$('.err', form).forEach((el) => (el.textContent = ''));
    $$('.field', form).forEach((el) => el.classList.remove('invalid'));
    const banner = $('.form-error', form);
    banner.hidden = true;
    banner.textContent = '';
  }

  function showErrors(form, err) {
    if (err.fieldErrors) {
      Object.entries(err.fieldErrors).forEach(([field, msg]) => {
        const slot = $(`.err[data-for="${field}"]`, form);
        if (slot) {
          slot.textContent = msg;
          slot.closest('.field').classList.add('invalid');
        }
      });
      $('.field.invalid input, .field.invalid select, .field.invalid textarea', form)?.focus();
    }
    if (!err.fieldErrors || err.status !== 400) {
      const banner = $('.form-error', form);
      banner.textContent = err.message;
      banner.hidden = false;
    }
  }

  /** Client-side checks that mirror the server's validation, so most mistakes are caught instantly. */
  function validateEmployee(data) {
    const errors = {};
    if (!data.firstName) errors.firstName = 'First name is required';
    if (!data.emailId) errors.emailId = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.emailId)) errors.emailId = 'Email must be a valid address';
    if (data.phone && !/^[+]?[0-9 ()-]{7,20}$/.test(data.phone)) errors.phone = 'Phone must be 7-20 digits';
    if (data.salary !== null && data.salary < 0) errors.salary = 'Salary cannot be negative';
    if (data.joiningDate && data.joiningDate > new Date().toISOString().slice(0, 10)) errors.joiningDate = 'Joining date cannot be in the future';
    return Object.keys(errors).length ? errors : null;
  }

  let editingEmployeeId = null;

  async function openEmployeeForm(employee = null) {
    if (!lookups.departments.length && !lookups.roles.length) await loadLookups();
    const dialog = $('#employeeDialog');
    const form = $('#employeeForm');
    form.reset();
    clearErrors(form);
    form.departmentId.innerHTML = optionsHtml(lookups.departments, 'name', '— None —');
    form.roleId.innerHTML = optionsHtml(lookups.roles, 'title', '— None —');
    form.joiningDate.max = new Date().toISOString().slice(0, 10);

    editingEmployeeId = employee?.id ?? null;
    $('#employeeDialogTitle').textContent = employee ? `Edit ${employee.fullName}` : 'Add employee';
    $('#employeeSubmit').textContent = employee ? 'Save changes' : 'Add employee';
    if (employee) {
      form.firstName.value = employee.firstName ?? '';
      form.lastName.value = employee.lastName ?? '';
      form.emailId.value = employee.emailId ?? '';
      form.phone.value = employee.phone ?? '';
      form.status.value = employee.status;
      form.departmentId.value = employee.department?.id ?? '';
      form.roleId.value = employee.role?.id ?? '';
      form.salary.value = employee.salary ?? '';
      form.joiningDate.value = employee.joiningDate ?? '';
    } else {
      form.joiningDate.value = new Date().toISOString().slice(0, 10);
    }
    dialog.showModal();
    form.firstName.focus();
  }

  $('#employeeForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const form = ev.target;
    clearErrors(form);
    const f = new FormData(form);
    const str = (k) => (f.get(k) || '').toString().trim();
    const data = {
      firstName: str('firstName'),
      lastName: str('lastName') || null,
      emailId: str('emailId'),
      phone: str('phone') || null,
      status: str('status') || 'ACTIVE',
      departmentId: str('departmentId') ? Number(str('departmentId')) : null,
      roleId: str('roleId') ? Number(str('roleId')) : null,
      salary: str('salary') ? Number(str('salary')) : null,
      joiningDate: str('joiningDate') || null,
    };
    const clientErrors = validateEmployee(data);
    if (clientErrors) return showErrors(form, { status: 400, fieldErrors: clientErrors });

    const submit = $('#employeeSubmit');
    submit.disabled = true;
    try {
      const saved = editingEmployeeId
        ? await api(`/employees/${editingEmployeeId}`, { method: 'PUT', body: data })
        : await api('/employees', { method: 'POST', body: data });
      $('#employeeDialog').close();
      toast(editingEmployeeId ? `${saved.fullName} updated` : `${saved.fullName} added`);
      if (location.hash.startsWith('#/employees')) loadEmployees(); else route();
    } catch (err) {
      showErrors(form, err);
    } finally {
      submit.disabled = false;
    }
  });

  // ---------------------------------------------------------------- departments + roles
  const CATALOG = {
    departments: { path: '/departments', label: 'name', singular: 'department', heading: 'Department' },
    roles: { path: '/roles', label: 'title', singular: 'role', heading: 'Role' },
  };
  let catalogCtx = null;

  async function renderCatalog(kind) {
    const cfg = CATALOG[kind];
    $('#topbarActions').innerHTML = `<button class="btn primary" id="addItem">+ Add ${cfg.singular}</button>`;
    $('#addItem').onclick = () => openCatalogForm(kind);
    $('#view').innerHTML = `<section class="card"><div class="card-body">${'<div class="skeleton" style="margin:12px 0"></div>'.repeat(4)}</div></section>`;

    const items = await api(cfg.path);
    $('#view').innerHTML = `<section class="card"><div class="table-wrap">${items.length ? `
      <table>
        <thead><tr><th>${cfg.heading}</th><th>Description</th><th>Employees</th><th></th></tr></thead>
        <tbody>${items.map((i) => `
          <tr>
            <td><strong>${esc(i[cfg.label])}</strong></td>
            <td class="wrap muted">${esc(i.description || '—')}</td>
            <td><a href="#/employees" data-filter="${i.id}">${i.employeeCount}</a></td>
            <td class="actions">
              <button class="icon-btn" data-edit="${i.id}" aria-label="Edit ${esc(i[cfg.label])}" title="Edit">
                <svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75z"/></svg>
              </button>
              <button class="icon-btn danger" data-delete="${i.id}" aria-label="Delete ${esc(i[cfg.label])}" title="Delete">
                <svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z"/></svg>
              </button>
            </td>
          </tr>`).join('')}</tbody>
      </table>` : `<div class="empty"><strong>No ${kind} yet</strong>Add one to start assigning employees.</div>`}
    </div></section>`;

    const byId = Object.fromEntries(items.map((i) => [String(i.id), i]));
    $$('[data-filter]').forEach((a) => a.onclick = () => {
      Object.assign(state, { q: '', departmentId: '', roleId: '', status: '', page: 0 });
      state[kind === 'departments' ? 'departmentId' : 'roleId'] = a.dataset.filter;
    });
    $$('[data-edit]').forEach((b) => b.onclick = () => openCatalogForm(kind, byId[b.dataset.edit]));
    $$('[data-delete]').forEach((b) => b.onclick = () => {
      const item = byId[b.dataset.delete];
      confirmDelete(`Delete ${cfg.singular}?`, `"${item[cfg.label]}" will be removed permanently.`, async () => {
        await api(`${cfg.path}/${item.id}`, { method: 'DELETE' });
        toast(`${cfg.heading} "${item[cfg.label]}" deleted`);
        renderCatalog(kind);
      });
    });
  }

  function openCatalogForm(kind, item = null) {
    const cfg = CATALOG[kind];
    const form = $('#catalogForm');
    form.reset();
    clearErrors(form);
    catalogCtx = { kind, id: item?.id ?? null };
    $('#catalogDialogTitle').textContent = item ? `Edit ${cfg.singular}` : `Add ${cfg.singular}`;
    $('#catalogNameLabel').textContent = `${cfg.heading} ${kind === 'roles' ? 'title' : 'name'} *`;
    form.name.value = item?.[cfg.label] ?? '';
    form.description.value = item?.description ?? '';
    $('#catalogDialog').showModal();
    form.name.focus();
  }

  $('#catalogForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const form = ev.target;
    clearErrors(form);
    const { kind, id } = catalogCtx;
    const cfg = CATALOG[kind];
    const name = form.name.value.trim();
    if (!name) return showErrors(form, { status: 400, fieldErrors: { name: `${cfg.heading} ${kind === 'roles' ? 'title' : 'name'} is required` } });
    const body = { [cfg.label]: name, description: form.description.value.trim() || null };
    try {
      await api(id ? `${cfg.path}/${id}` : cfg.path, { method: id ? 'PUT' : 'POST', body });
      $('#catalogDialog').close();
      toast(`${cfg.heading} "${name}" ${id ? 'updated' : 'added'}`);
      renderCatalog(kind);
    } catch (err) {
      // The API reports the field as "name" or "title"; show both in the one input slot.
      if (err.fieldErrors?.title) err.fieldErrors.name = err.fieldErrors.title;
      showErrors(form, err);
    }
  });

  // ---------------------------------------------------------------- confirm + dialogs
  let confirmAction = null;

  function confirmDelete(title, text, action) {
    $('#confirmTitle').textContent = title;
    $('#confirmText').textContent = text;
    confirmAction = action;
    $('#confirmDialog').showModal();
  }

  $('#confirmOk').addEventListener('click', async () => {
    const btn = $('#confirmOk');
    btn.disabled = true;
    try {
      await confirmAction?.();
      $('#confirmDialog').close();
    } catch (err) {
      $('#confirmDialog').close();
      toast(err.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });

  $$('[data-close]').forEach((b) => b.addEventListener('click', () => b.closest('dialog').close()));
  $$('dialog').forEach((d) => d.addEventListener('click', (e) => { if (e.target === d) d.close(); }));

  // ---------------------------------------------------------------- mobile sidebar
  function closeSidebar() {
    $('#sidebar').classList.remove('open');
    $('#menuBtn').setAttribute('aria-expanded', 'false');
  }
  $('#menuBtn').addEventListener('click', () => {
    const open = $('#sidebar').classList.toggle('open');
    $('#menuBtn').setAttribute('aria-expanded', String(open));
  });

  window.addEventListener('hashchange', route);
  route();
})();
