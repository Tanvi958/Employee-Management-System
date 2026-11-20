/* EmployeeHub: a vanilla-JS single-page dashboard for the Employee Management REST API. */
(() => {
  'use strict';

  const API = 'api/v1';
  const TOKEN_KEY = 'ems.token';

  // ---------------------------------------------------------------- helpers
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  const esc = (value) =>
    String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

  const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
  const compactMoney = (n) => (n >= 1e7 ? `₹${(n / 1e7).toFixed(2)} Cr` : n >= 1e5 ? `₹${(n / 1e5).toFixed(2)} L` : money.format(n));
  const dateFmt = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  const dateTimeFmt = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
  const fmtDate = (iso) => (iso ? dateFmt.format(new Date(iso + (iso.length === 10 ? 'T00:00:00' : ''))) : '—');
  const STATUS_LABEL = { ACTIVE: 'Active', ON_LEAVE: 'On leave', INACTIVE: 'Inactive' };
  const STATUS_COLOR = { ACTIVE: '#16a34a', ON_LEAVE: '#d97706', INACTIVE: '#9ca3af' };
  const ROLE_LABEL = { ADMIN: 'Admin', HR: 'HR', VIEWER: 'Viewer' };
  const ACTION_ICON = { CREATE: '+', UPDATE: '✎', DELETE: '−', IMPORT: '⇪' };
  const AVATAR_COLORS = ['#4f46e5', '#0891b2', '#059669', '#d97706', '#db2777', '#7c3aed', '#2563eb', '#dc2626'];

  const initials = (first, last) => ((first?.[0] ?? '') + (last?.[0] ?? '')).toUpperCase() || '?';
  const avatarColor = (id) => AVATAR_COLORS[Number(id) % AVATAR_COLORS.length];

  function timeAgo(iso) {
    const s = (Date.now() - new Date(iso).getTime()) / 1000;
    if (s < 60) return 'just now';
    if (s < 3600) return `${Math.floor(s / 60)} min ago`;
    if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
    return dateTimeFmt.format(new Date(iso));
  }

  function debounce(fn, ms) {
    let t;
    return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
  }

  // localStorage can throw (private mode, blocked storage): the app still works, you just sign in again.
  const store = {
    get: (k) => { try { return localStorage.getItem(k); } catch { return null; } },
    set: (k, v) => { try { localStorage.setItem(k, v); } catch { /* ignore */ } },
    del: (k) => { try { localStorage.removeItem(k); } catch { /* ignore */ } },
  };

  // ---------------------------------------------------------------- session + api
  const session = { token: store.get(TOKEN_KEY), user: null };
  const isAdmin = () => session.user?.role === 'ADMIN';
  const canEditEmployees = () => ['ADMIN', 'HR'].includes(session.user?.role);

  class ApiError extends Error {
    constructor(status, body) {
      super(body?.message || `Request failed (${status})`);
      this.status = status;
      this.fieldErrors = body?.fieldErrors || null;
    }
  }

  async function request(path, { method = 'GET', body, form, raw = false } = {}) {
    const headers = {};
    if (session.token) headers.Authorization = `Bearer ${session.token}`;
    if (body) headers['Content-Type'] = 'application/json';
    const res = await fetch(`${API}${path}`, { method, headers, body: form || (body ? JSON.stringify(body) : undefined) });
    if (res.status === 401 && session.token && !path.startsWith('/auth/login')) {
      signOut('Your session has expired. Please sign in again.');
      throw new ApiError(401, { message: 'Signed out' });
    }
    if (raw && res.ok) return res;
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = { message: text }; }
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }
  const api = request;

  function toast(message, type = 'ok') {
    const el = document.createElement('div');
    el.className = `toast ${type === 'error' ? 'error' : ''}`;
    el.textContent = message;
    $('#toasts').append(el);
    setTimeout(() => el.remove(), 4000);
  }

  // ---------------------------------------------------------------- sign in / out
  async function showLogin(message) {
    $('#app').hidden = true;
    $('#loginScreen').hidden = false;
    const err = $('#loginError');
    err.hidden = !message;
    err.textContent = message || '';
    try {
      const demo = await api('/auth/demo');
      $('.demo').hidden = !demo.enabled;
    } catch { $('.demo').hidden = true; }
    $('#loginForm').username.focus();
  }

  async function signIn(username, password) {
    const btn = $('#loginSubmit');
    btn.disabled = true;
    $('#loginError').hidden = true;
    try {
      const res = await api('/auth/login', { method: 'POST', body: { username, password } });
      session.token = res.token;
      session.user = res.user;
      store.set(TOKEN_KEY, res.token);
      $('#loginForm').reset();
      startApp();
    } catch (err) {
      const box = $('#loginError');
      box.textContent = err.fieldErrors ? Object.values(err.fieldErrors).join('. ') : err.message;
      box.hidden = false;
    } finally {
      btn.disabled = false;
    }
  }

  function signOut(message) {
    session.token = null;
    session.user = null;
    store.del(TOKEN_KEY);
    $$('dialog[open]').forEach((d) => d.close());
    showLogin(message);
  }

  $('#loginForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.target;
    if (!f.username.value.trim() || !f.password.value) {
      $('#loginError').textContent = 'Enter your username and password';
      $('#loginError').hidden = false;
      return;
    }
    signIn(f.username.value.trim(), f.password.value);
  });
  $$('[data-demo]').forEach((b) => b.addEventListener('click', () => signIn(b.dataset.demo, b.dataset.pass)));
  $('#logoutBtn').addEventListener('click', () => { signOut(); toast('Signed out'); });

  function startApp() {
    $('#loginScreen').hidden = true;
    $('#app').hidden = false;
    const u = session.user;
    $('#userName').textContent = u.fullName;
    $('#userRole').textContent = ROLE_LABEL[u.role];
    $('#userRole').className = `role-tag ${u.role}`;
    const words = u.fullName.split(/\s+/).filter((w) => /^\p{L}/u.test(w));
    $('#userAvatar').textContent = initials(words[0], words.length > 1 ? words[words.length - 1] : '');
    $$('[data-admin-only]').forEach((el) => (el.hidden = !isAdmin()));
    route();
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
    employees: { title: 'Employees', subtitle: 'Search, add, edit, import and export employee records', render: renderEmployees },
    departments: { title: 'Departments', subtitle: 'Teams that employees belong to', render: () => renderCatalog('departments') },
    roles: { title: 'Roles', subtitle: 'Job roles and designations', render: () => renderCatalog('roles') },
    users: { title: 'Users', subtitle: 'Who can sign in, and what they may do', render: renderUsers, admin: true },
    activity: { title: 'Activity log', subtitle: 'Every change, who made it and when', render: renderActivity, admin: true },
  };

  function route() {
    if (!session.user) return;
    let name = location.hash.replace(/^#\/?/, '').split('?')[0] || 'dashboard';
    if (!views[name] || (views[name].admin && !isAdmin())) name = 'dashboard';
    const view = views[name];
    $$('.nav a').forEach((a) => a.classList.toggle('active', a.dataset.route === name));
    $('#pageTitle').textContent = view.title;
    $('#pageSubtitle').textContent = view.subtitle;
    $('#topbarActions').innerHTML = '';
    document.title = `${view.title} · Employee Management System`;
    closeSidebar();
    view.render().catch((err) => {
      if (err.status === 401) return;
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
          <div class="hint">${s.hiresLast30Days} joined in the last 30 days</div></div>
        <div class="card stat"><div class="label">Monthly payroll</div><div class="value">${compactMoney(Number(s.monthlyPayroll))}</div>
          <div class="hint">active and on-leave staff</div></div>
        <div class="card stat"><div class="label">Average salary</div><div class="value">${s.averageSalary != null ? compactMoney(Number(s.averageSalary)) : '—'}</div>
          <div class="hint">per month</div></div>
        <div class="card stat"><div class="label">Departments · Roles</div><div class="value">${s.departments} · ${s.roles}</div>
          <div class="hint"><a href="#/departments">Departments</a> · <a href="#/roles">Roles</a></div></div>
      </div>

      <div class="grid-3">
        <section class="card">
          <div class="card-head"><h2>Hires per month</h2><span class="muted">last 12 months</span></div>
          <div class="card-body chart">${hiresChart(s.hiresByMonth)}</div>
        </section>
        <section class="card">
          <div class="card-head"><h2>Employment status</h2></div>
          <div class="card-body">${statusDonut(s)}</div>
        </section>
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
                ${d.averageSalary != null ? `<span class="avg">avg ${money.format(d.averageSalary)}/month</span>` : ''}
              </div>`).join('')}</div>` : '<p class="muted">No departments yet.</p>'}
          </div>
        </section>
        <section class="card">
          <div class="card-head"><h2>Recent activity</h2>${isAdmin() ? '<a href="#/activity">View all</a>' : ''}</div>
          <div class="card-body">${feedHtml(s.recentActivity, 'No changes recorded yet.')}</div>
        </section>
      </div>`;
  }

  function hiresChart(months) {
    const W = 520, H = 190, pad = { l: 26, r: 6, t: 16, b: 26 };
    const max = Math.max(4, ...months.map((m) => m.count));
    const step = (W - pad.l - pad.r) / months.length;
    const bw = Math.min(26, step * 0.6);
    const y = (v) => pad.t + (H - pad.t - pad.b) * (1 - v / max);
    const ticks = [...new Set([0, Math.ceil(max / 2), max])];
    return `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Employees hired per month over the last 12 months">
      ${ticks.map((t) => `<line class="gridline" x1="${pad.l}" x2="${W - pad.r}" y1="${y(t)}" y2="${y(t)}"/>
        <text class="axis" x="${pad.l - 6}" y="${y(t) + 3}" text-anchor="end">${t}</text>`).join('')}
      ${months.map((m, i) => {
        const x = pad.l + i * step + (step - bw) / 2;
        const top = y(m.count);
        return `<g><title>${esc(m.label)}: ${m.count} hired</title>
          <rect class="bar" x="${x}" y="${top}" width="${bw}" height="${Math.max(0, H - pad.b - top)}" rx="4"/>
          ${m.count ? `<text class="val" x="${x + bw / 2}" y="${top - 4}" text-anchor="middle">${m.count}</text>` : ''}
          <text class="axis" x="${x + bw / 2}" y="${H - 8}" text-anchor="middle">${esc(m.label.slice(0, 3))}</text></g>`;
      }).join('')}
    </svg>`;
  }

  function statusDonut(s) {
    const parts = [
      ['ACTIVE', s.activeEmployees], ['ON_LEAVE', s.onLeaveEmployees], ['INACTIVE', s.inactiveEmployees],
    ];
    const total = parts.reduce((a, [, n]) => a + n, 0);
    const r = 52, c = 2 * Math.PI * r;
    let offset = 0;
    const arcs = total === 0 ? '' : parts.filter(([, n]) => n > 0).map(([k, n]) => {
      const len = (n / total) * c;
      const arc = `<circle cx="70" cy="70" r="${r}" fill="none" stroke="${STATUS_COLOR[k]}" stroke-width="18"
        stroke-dasharray="${len} ${c - len}" stroke-dashoffset="${-offset}" transform="rotate(-90 70 70)"><title>${STATUS_LABEL[k]}: ${n}</title></circle>`;
      offset += len;
      return arc;
    }).join('');
    return `<div class="donut-wrap">
      <svg viewBox="0 0 140 140" role="img" aria-label="Employees by status">
        <circle cx="70" cy="70" r="${r}" fill="none" stroke="var(--surface-2)" stroke-width="18"/>
        ${arcs}
        <text class="donut-center" x="70" y="72" text-anchor="middle">${total}</text>
        <text class="donut-sub" x="70" y="88" text-anchor="middle">EMPLOYEES</text>
      </svg>
      <div class="legend">${parts.map(([k, n]) => `
        <div><span class="dot" style="background:${STATUS_COLOR[k]}"></span>${STATUS_LABEL[k]} <strong>${n}</strong>
          <span class="muted">${total ? Math.round((n / total) * 100) : 0}%</span></div>`).join('')}
      </div>
    </div>`;
  }

  function feedHtml(entries, emptyText) {
    if (!entries.length) return `<p class="muted">${esc(emptyText)}</p>`;
    return `<ul class="feed">${entries.map((a) => `
      <li>
        <span class="ico ${a.action}" aria-hidden="true">${ACTION_ICON[a.action]}</span>
        <div>
          <div class="what">${esc(a.summary)}</div>
          ${a.changes ? `<div class="changes">${esc(a.changes)}</div>` : ''}
          <div class="meta">${esc(a.actor)} · ${timeAgo(a.occurredAt)}</div>
        </div>
      </li>`).join('')}</ul>`;
  }

  const personCell = (e) => `
    <div class="person">
      <span class="avatar" style="background:${avatarColor(e.id)}">${esc(initials(e.firstName, e.lastName))}</span>
      <div><div class="name">${esc(e.fullName)}</div><div class="email">${esc(e.emailId)}</div></div>
    </div>`;

  // ---------------------------------------------------------------- employees
  const state = { q: '', departmentId: '', roleId: '', status: '', page: 0, size: 10, sort: 'id', direction: 'asc' };

  function filterParams({ paging = true } = {}) {
    const params = new URLSearchParams();
    Object.entries(state).forEach(([k, v]) => {
      if (!paging && (k === 'page' || k === 'size')) return;
      if (v !== '' && v !== null) params.set(k, v);
    });
    return params;
  }

  async function renderEmployees() {
    await loadLookups();
    $('#topbarActions').innerHTML = `
      <button class="btn ghost" id="exportCsv" title="Download the employees matching the filters">⇩ Export CSV</button>
      ${canEditEmployees() ? '<button class="btn ghost" id="importCsv">⇪ Import</button><button class="btn primary" id="addEmployee">+ Add employee</button>' : ''}`;
    $('#exportCsv').onclick = exportCsv;
    if (canEditEmployees()) {
      $('#addEmployee').onclick = () => openEmployeeForm();
      $('#importCsv').onclick = openImport;
    }

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

  const EDIT_ICON = '<svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75z"/></svg>';
  const DELETE_ICON = '<svg viewBox="0 0 24 24"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6zM19 4h-3.5l-1-1h-5l-1 1H5v2h14z"/></svg>';

  async function loadEmployees() {
    const table = $('#empTable');
    if (!table) return;
    table.innerHTML = `<div class="card-body">${'<div class="skeleton" style="margin:12px 0"></div>'.repeat(5)}</div>`;

    let page;
    try {
      page = await api(`/employees?${filterParams()}`);
    } catch (err) {
      table.innerHTML = `<div class="empty"><strong>Could not load employees</strong>${esc(err.message)}</div>`;
      return;
    }
    if (page.content.length === 0 && state.page > 0 && page.totalPages > 0) {
      state.page = page.totalPages - 1;
      return loadEmployees();
    }

    const filtered = state.q || state.departmentId || state.roleId || state.status;
    if (page.content.length === 0) {
      table.innerHTML = `<div class="empty"><strong>${filtered ? 'No matching employees' : 'No employees yet'}</strong>
        ${filtered ? 'Try a different search or clear the filters.' : (canEditEmployees() ? 'Add your first employee, or import a CSV.' : '')}</div>`;
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
          <tr class="clickable" data-open="${e.id}">
            <td>${personCell(e)}</td>
            <td>${e.department ? `<span class="chip">${esc(e.department.name)}</span>` : '<span class="muted">—</span>'}</td>
            <td class="hide-sm">${e.role ? esc(e.role.name) : '<span class="muted">—</span>'}</td>
            <td class="hide-sm">${e.salary != null ? money.format(e.salary) : '<span class="muted">—</span>'}</td>
            <td class="hide-sm">${fmtDate(e.joiningDate)}</td>
            <td><span class="badge ${e.status}">${STATUS_LABEL[e.status]}</span></td>
            <td class="actions">${canEditEmployees() ? `
              <button class="icon-btn" data-edit="${e.id}" aria-label="Edit ${esc(e.fullName)}" title="Edit">${EDIT_ICON}</button>
              <button class="icon-btn danger" data-delete="${e.id}" data-name="${esc(e.fullName)}" aria-label="Delete ${esc(e.fullName)}" title="Delete">${DELETE_ICON}</button>` : ''}
            </td>
          </tr>`).join('')}
        </tbody></table>`;

      $$('[data-sort]', table).forEach((b) => b.onclick = () => {
        const key = b.dataset.sort;
        state.direction = state.sort === key && state.direction === 'asc' ? 'desc' : 'asc';
        state.sort = key;
        loadEmployees();
      });
      $$('[data-open]', table).forEach((row) => row.addEventListener('click', (ev) => {
        if (ev.target.closest('button')) return;
        openProfile(row.dataset.open);
      }));
      $$('[data-edit]', table).forEach((b) => b.onclick = async () => {
        try { openEmployeeForm(await api(`/employees/${b.dataset.edit}`)); } catch (err) { toast(err.message, 'error'); }
      });
      $$('[data-delete]', table).forEach((b) => b.onclick = () => deleteEmployee(b.dataset.delete, b.dataset.name));
    }

    renderPager(page);
  }

  function deleteEmployee(id, name, after) {
    confirmDelete('Delete employee?', `${name} will be removed permanently.`, async () => {
      await api(`/employees/${id}`, { method: 'DELETE' });
      toast(`${name} deleted`);
      after?.();
      if ($('#empTable')) loadEmployees();
    });
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

  // ---------------------------------------------------------------- profile drawer
  async function openProfile(id) {
    const drawer = $('#profileDrawer');
    const body = $('#profileBody');
    body.innerHTML = '<div class="skeleton" style="margin:12px 0"></div>'.repeat(6);
    drawer.showModal();
    let e, history;
    try {
      [e, history] = await Promise.all([api(`/employees/${id}`), api(`/employees/${id}/history`)]);
    } catch (err) {
      body.innerHTML = `<div class="empty"><strong>Could not load this employee</strong>${esc(err.message)}</div>`;
      return;
    }
    const tenure = e.joiningDate ? (() => {
      const months = Math.max(0, Math.floor((Date.now() - new Date(e.joiningDate)) / (30.44 * 864e5)));
      return months >= 12 ? `${Math.floor(months / 12)} yr ${months % 12} mo` : `${months} mo`;
    })() : '—';

    body.innerHTML = `
      <div class="profile-head">
        <span class="avatar" style="background:${avatarColor(e.id)}">${esc(initials(e.firstName, e.lastName))}</span>
        <div>
          <h2 id="profileName">${esc(e.fullName)}</h2>
          <div class="muted">${esc(e.role?.name || 'No role')} · ${esc(e.department?.name || 'No department')}</div>
          <span class="badge ${e.status}" style="margin-top:6px">${STATUS_LABEL[e.status]}</span>
        </div>
        <button class="icon-btn close" data-close aria-label="Close">✕</button>
      </div>
      ${canEditEmployees() ? `<div class="drawer-actions">
        <button class="btn ghost sm" id="profileEdit">Edit</button>
        <button class="btn ghost sm" id="profileDelete">Delete</button>
      </div>` : ''}
      <dl class="facts">
        <div><dt>Email</dt><dd><a href="mailto:${esc(e.emailId)}">${esc(e.emailId)}</a></dd></div>
        <div><dt>Phone</dt><dd>${esc(e.phone || '—')}</dd></div>
        <div><dt>Monthly salary</dt><dd>${e.salary != null ? money.format(e.salary) : '—'}</dd></div>
        <div><dt>Joined</dt><dd>${fmtDate(e.joiningDate)}</dd></div>
        <div><dt>Tenure</dt><dd>${tenure}</dd></div>
        <div><dt>Employee ID</dt><dd>#${e.id}</dd></div>
      </dl>
      <h3>History</h3>
      ${feedHtml(history, 'No recorded changes. Sample records loaded at start-up have no history.')}`;

    $('[data-close]', body).onclick = () => drawer.close();
    if (canEditEmployees()) {
      $('#profileEdit').onclick = () => { drawer.close(); openEmployeeForm(e); };
      $('#profileDelete').onclick = () => deleteEmployee(e.id, e.fullName, () => drawer.close());
    }
  }

  // ---------------------------------------------------------------- CSV
  async function download(path, fallbackName) {
    try {
      const res = await request(path, { raw: true });
      const blob = await res.blob();
      const name = /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') || '')?.[1] || fallbackName;
      const url = URL.createObjectURL(blob);
      const a = Object.assign(document.createElement('a'), { href: url, download: name });
      document.body.append(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      return name;
    } catch (err) {
      if (err.status !== 401) toast(err.message, 'error');
      return null;
    }
  }

  async function exportCsv() {
    const name = await download(`/employees/export?${filterParams({ paging: false })}`, 'employees.csv');
    if (name) toast(`Downloaded ${name}`);
  }

  function openImport() {
    const form = $('#importForm');
    form.reset();
    clearErrors(form);
    $('#importResult').innerHTML = '';
    $('#fileLabel').innerHTML = '<strong>Choose a CSV file</strong> or drop it here (max 2 MB)';
    $('#importSubmit').disabled = false;
    $('#importDialog').showModal();
  }

  $('#templateLink').addEventListener('click', (e) => { e.preventDefault(); download('/employees/import/template', 'employees-template.csv'); });
  $('#importFile').addEventListener('change', (e) => {
    const f = e.target.files[0];
    $('#fileLabel').innerHTML = f ? `<strong>${esc(f.name)}</strong> (${Math.ceil(f.size / 1024)} KB)` : '<strong>Choose a CSV file</strong>';
    $('#importResult').innerHTML = '';
  });
  const drop = $('#fileDrop');
  ['dragenter', 'dragover'].forEach((t) => drop.addEventListener(t, (e) => { e.preventDefault(); drop.classList.add('drag'); }));
  ['dragleave', 'drop'].forEach((t) => drop.addEventListener(t, () => drop.classList.remove('drag')));
  drop.addEventListener('drop', (e) => {
    e.preventDefault();
    if (e.dataTransfer.files.length) {
      $('#importFile').files = e.dataTransfer.files;
      $('#importFile').dispatchEvent(new Event('change'));
    }
  });

  $('#importForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const form = ev.target;
    clearErrors(form);
    const file = $('#importFile').files[0];
    if (!file) return showErrors(form, { message: 'Choose a CSV file first' });
    const data = new FormData();
    data.append('file', file);
    const btn = $('#importSubmit');
    btn.disabled = true;
    try {
      const r = await api('/employees/import', { method: 'POST', form: data });
      $('#importResult').innerHTML = `
        <div class="import-summary ${r.failed ? 'partial' : ''}">${r.imported} imported${r.failed ? `, ${r.failed} skipped` : ''}</div>
        ${r.errors.length ? `<ul class="import-errors">${r.errors.map((x) => `<li><b>Line ${x.line}</b>${esc(x.message)}</li>`).join('')}</ul>` : ''}`;
      if (r.imported) {
        toast(`${r.imported} employee(s) imported`);
        loadEmployees();
      }
    } catch (err) {
      showErrors(form, err);
    } finally {
      btn.disabled = false;
    }
  });

  // ---------------------------------------------------------------- forms
  function clearErrors(form) {
    $$('.err', form).forEach((el) => (el.textContent = ''));
    $$('.field', form).forEach((el) => el.classList.remove('invalid'));
    const banner = $('.form-error', form);
    if (banner) { banner.hidden = true; banner.textContent = ''; }
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
    $('#employeeDialog').showModal();
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
      if ($('#empTable')) loadEmployees(); else route();
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
    if (isAdmin()) {
      $('#topbarActions').innerHTML = `<button class="btn primary" id="addItem">+ Add ${cfg.singular}</button>`;
      $('#addItem').onclick = () => openCatalogForm(kind);
    }
    $('#view').innerHTML = `<section class="card"><div class="card-body">${'<div class="skeleton" style="margin:12px 0"></div>'.repeat(4)}</div></section>`;

    const items = await api(cfg.path);
    $('#view').innerHTML = `<section class="card"><div class="table-wrap">${items.length ? `
      <table>
        <thead><tr><th>${cfg.heading}</th><th>Description</th><th>Employees</th><th aria-label="Actions"></th></tr></thead>
        <tbody>${items.map((i) => `
          <tr>
            <td><strong>${esc(i[cfg.label])}</strong></td>
            <td class="wrap muted">${esc(i.description || '—')}</td>
            <td><a href="#/employees" data-filter="${i.id}">${i.employeeCount}</a></td>
            <td class="actions">${isAdmin() ? `
              <button class="icon-btn" data-edit="${i.id}" aria-label="Edit ${esc(i[cfg.label])}" title="Edit">${EDIT_ICON}</button>
              <button class="icon-btn danger" data-delete="${i.id}" aria-label="Delete ${esc(i[cfg.label])}" title="Delete">${DELETE_ICON}</button>` : ''}
            </td>
          </tr>`).join('')}</tbody>
      </table>` : `<div class="empty"><strong>No ${kind} yet</strong>${isAdmin() ? 'Add one to start assigning employees.' : ''}</div>`}
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
      if (err.fieldErrors?.title) err.fieldErrors.name = err.fieldErrors.title;
      showErrors(form, err);
    }
  });

  // ---------------------------------------------------------------- users (admin)
  let editingUser = null;

  async function renderUsers() {
    $('#topbarActions').innerHTML = '<button class="btn primary" id="addUser">+ Add user</button>';
    $('#addUser').onclick = () => openUserForm();
    const users = await api('/users');
    $('#view').innerHTML = `<section class="card"><div class="table-wrap"><table>
      <thead><tr><th>User</th><th>Role</th><th class="hide-sm">Last sign-in</th><th class="hide-sm">Created</th><th aria-label="Actions"></th></tr></thead>
      <tbody>${users.map((u) => `
        <tr>
          <td><div class="person">
            <span class="avatar" style="background:${avatarColor(u.id + 3)}">${esc(initials(...u.fullName.split(/\s+/).filter((w) => /^\p{L}/u.test(w)).filter((w, i, a) => i === 0 || i === a.length - 1)))}</span>
            <div><div class="name">${esc(u.fullName)}${u.username === session.user.username ? ' <span class="muted">(you)</span>' : ''}</div>
            <div class="email">@${esc(u.username)}</div></div></div></td>
          <td><span class="role-tag ${u.role}">${ROLE_LABEL[u.role].toUpperCase()}</span></td>
          <td class="hide-sm">${u.lastLoginAt ? timeAgo(u.lastLoginAt) : '<span class="muted">never</span>'}</td>
          <td class="hide-sm">${fmtDate(u.createdAt.slice(0, 10))}</td>
          <td class="actions">
            <button class="icon-btn" data-edit="${u.id}" aria-label="Edit ${esc(u.username)}" title="Edit">${EDIT_ICON}</button>
            ${u.username === session.user.username ? '' : `<button class="icon-btn danger" data-delete="${u.id}" aria-label="Delete ${esc(u.username)}" title="Delete">${DELETE_ICON}</button>`}
          </td>
        </tr>`).join('')}</tbody></table></div></section>`;

    const byId = Object.fromEntries(users.map((u) => [String(u.id), u]));
    $$('[data-edit]').forEach((b) => b.onclick = () => openUserForm(byId[b.dataset.edit]));
    $$('[data-delete]').forEach((b) => b.onclick = () => {
      const u = byId[b.dataset.delete];
      confirmDelete('Delete user?', `@${u.username} will no longer be able to sign in.`, async () => {
        await api(`/users/${u.id}`, { method: 'DELETE' });
        toast(`@${u.username} deleted`);
        renderUsers();
      });
    });
  }

  function openUserForm(user = null) {
    const form = $('#userForm');
    form.reset();
    clearErrors(form);
    editingUser = user;
    $('#userDialogTitle').textContent = user ? `Edit @${user.username}` : 'Add user';
    form.username.value = user?.username ?? '';
    form.username.disabled = !!user;
    form.fullName.value = user?.fullName ?? '';
    form.role.value = user?.role ?? 'VIEWER';
    $('#userPasswordLabel').textContent = user ? 'New password (leave blank to keep)' : 'Password * (8+ characters)';
    $('#userDialog').showModal();
    (user ? form.fullName : form.username).focus();
  }

  $('#userForm').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const form = ev.target;
    clearErrors(form);
    const body = { fullName: form.fullName.value.trim(), role: form.role.value, password: form.password.value };
    try {
      if (editingUser) {
        await api(`/users/${editingUser.id}`, { method: 'PUT', body });
      } else {
        await api('/users', { method: 'POST', body: { ...body, username: form.username.value.trim() } });
      }
      $('#userDialog').close();
      toast(editingUser ? 'User updated' : 'User added');
      if (editingUser && editingUser.username === session.user.username) {
        session.user = await api('/auth/me');
        startApp();
      } else {
        renderUsers();
      }
    } catch (err) {
      showErrors(form, err);
    }
  });

  // ---------------------------------------------------------------- activity log (admin)
  const activity = { type: '', page: 0 };

  async function renderActivity() {
    const params = new URLSearchParams({ page: activity.page, size: 20 });
    if (activity.type) params.set('entityType', activity.type);
    const page = await api(`/audit?${params}`);
    $('#view').innerHTML = `
      <section class="card">
        <div class="toolbar">
          <div class="filters-inline">
            <label class="muted" for="actType">Show</label>
            <select id="actType">
              <option value="">Everything</option><option value="EMPLOYEE">Employees</option>
              <option value="DEPARTMENT">Departments</option><option value="ROLE">Roles</option><option value="USER">Users</option>
            </select>
          </div>
        </div>
        <div class="card-body">${feedHtml(page.content, 'Nothing recorded yet. Changes appear here as people make them.')}</div>
        <div class="pager">
          <span class="muted">${page.totalElements} entries</span>
          <div class="pages">
            <button class="btn ghost sm" id="actPrev" ${page.first ? 'disabled' : ''}>‹ Newer</button>
            <span class="muted">Page ${page.totalPages ? page.page + 1 : 0} of ${page.totalPages}</span>
            <button class="btn ghost sm" id="actNext" ${page.last ? 'disabled' : ''}>Older ›</button>
          </div>
        </div>
      </section>`;
    $('#actType').value = activity.type;
    $('#actType').onchange = (e) => { activity.type = e.target.value; activity.page = 0; renderActivity(); };
    $('#actPrev').onclick = () => { activity.page--; renderActivity(); };
    $('#actNext').onclick = () => { activity.page++; renderActivity(); };
  }

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
      if (err.status !== 401) toast(err.message, 'error');
    } finally {
      btn.disabled = false;
    }
  });

  document.addEventListener('click', (e) => {
    const closer = e.target.closest('[data-close]');
    if (closer) closer.closest('dialog')?.close();
  });
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

  // ---------------------------------------------------------------- boot
  window.addEventListener('hashchange', route);

  (async () => {
    if (!session.token) return showLogin();
    try {
      session.user = await api('/auth/me');
      startApp();
    } catch {
      signOut();
    }
  })();
})();
