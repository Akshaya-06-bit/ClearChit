const API = '/api';
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const state = { auth: readAuth(), groups: [], currentGroup: null, members: [], cycles: [], currentPage: 'dashboard' };
let toastTimer;

function readAuth() { try { return JSON.parse(localStorage.getItem('clearchit.auth') || 'null'); } catch { return null; } }
function saveAuth(data) { state.auth = data; localStorage.setItem('clearchit.auth', JSON.stringify(data)); }
function clearAuth() { state.auth = null; localStorage.removeItem('clearchit.auth'); }
function esc(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function money(value) { const n = Number(value || 0); return new Intl.NumberFormat('en-IN', { style:'currency', currency:'INR', maximumFractionDigits:2 }).format(n); }
function localDateString(d) { return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }
function shortDate(value) { if (!value) return '—'; const d = new Date(`${String(value).slice(0,10)}T00:00:00`); return Number.isNaN(d.getTime()) ? value : d.toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'}); }
function dt(value) { if (!value) return '—'; const d = new Date(value); return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleString('en-IN',{day:'numeric',month:'short',hour:'2-digit',minute:'2-digit'}); }
function notify(message, error = false) { const el = $('#toast'); el.textContent = message; el.classList.toggle('error', error); el.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 3600); }
function showInline(id, message) { const el = document.getElementById(id); if (!el) return; el.textContent = message; el.classList.remove('hidden'); }
function hideInline(id) { const el = document.getElementById(id); if (el) { el.textContent = ''; el.classList.add('hidden'); } }

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (state.auth?.token) headers.set('Authorization', `Bearer ${state.auth.token}`);
  let response;
  try { response = await fetch(`${API}${path}`, { ...options, headers }); }
  catch { throw new Error('Cannot reach ClearChit. Check that the backend is running and try again.'); }
  const text = await response.text();
  let body = null;
  if (text) { try { body = JSON.parse(text); } catch { body = text; } }
  if (response.status === 401) { if (state.auth && !path.startsWith('/auth/')) { clearAuth(); showAuth(); } throw new Error(typeof body === 'object' ? (body.message || 'Session expired. Sign in again.') : 'Session expired. Sign in again.'); }
  if (!response.ok) {
    const message = typeof body === 'object' && body ? (body.message || body.error || body.detail) : body;
    throw new Error(message || `Request failed (${response.status})`);
  }
  return body;
}

function showAuth(tab = 'login') {
  $('#auth-screen').classList.remove('hidden'); $('#app-screen').classList.add('hidden');
  setAuthTab(tab);
}
function showApp() {
  $('#auth-screen').classList.add('hidden'); $('#app-screen').classList.remove('hidden');
  const a = state.auth || {};
  const name = a.name || (a.email || 'User').split('@')[0];
  $('#user-name').textContent = name; $('#user-email').textContent = a.email || ''; $('#user-avatar').textContent = name.trim().charAt(0).toUpperCase() || 'U';
  $('#user-role').textContent = a.role || 'MEMBER'; $('#welcome-name').textContent = name.split(' ')[0]; $('#stat-member').textContent = a.memberId ?? '—'; $('#wallet-member-id').textContent = a.memberId ?? '—';
  navigate('dashboard'); refreshGroups().catch(e => notify(e.message, true));
}
function setAuthTab(tab) {
  $$('.auth-tab').forEach(el => el.classList.toggle('active', el.dataset.authTab === tab));
  $('#login-form').classList.toggle('hidden', tab !== 'login'); $('#register-form').classList.toggle('hidden', tab !== 'register'); hideInline('auth-error');
}
function navigate(page) {
  state.currentPage = page;
  $$('.page').forEach(el => el.classList.add('hidden'));
  const el = $(`#page-${page}`); if (el) el.classList.remove('hidden');
  $$('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.page === page));
  const labels = { dashboard:'Overview', groups:'Chit groups', wallet:'My wallet', detail: state.currentGroup?.name || 'Group details' };
  $('#breadcrumb-current').textContent = labels[page] || 'Overview';
  if (page === 'wallet') refreshWallet().catch(e => notify(e.message, true));
  if (page === 'groups') renderGroups($('#group-search').value || '');
  window.scrollTo({top:0,behavior:'smooth'});
}

async function refreshGroups() {
  const result = await api('/chit-groups');
  state.groups = Array.isArray(result) ? result : [];
  $('#stat-groups').textContent = state.groups.length;
  $('#stat-active').textContent = state.groups.filter(g => String(g.status).toUpperCase() === 'ACTIVE').length;
  renderGroups($('#group-search')?.value || '');
}
function statusClass(status) { const s = String(status || 'DRAFT').toLowerCase(); return ['active','completed','cancelled','draft'].includes(s) ? s : ''; }
function groupCard(g) {
  const status = String(g.status || 'DRAFT').toUpperCase();
  const members = Array.isArray(g.members) ? g.members.length : null;
  const id = Number(g.id);
  return `<article class="group-card"><div class="group-card-top"><div class="group-avatar">${esc((g.name || 'C').trim().charAt(0).toUpperCase())}</div><span class="status-pill ${statusClass(status)}">${esc(status)}</span></div><h3>${esc(g.name || `Chit group #${id}`)}</h3><div class="group-subtitle">Group #${id} · Starts ${esc(shortDate(g.startDate))}</div><div class="group-metrics"><div><span class="metric-label">Chit value</span><strong class="metric-value">${money(g.chitValue)}</strong></div><div><span class="metric-label">Monthly contribution</span><strong class="metric-value">${money(g.monthlyContribution)}</strong></div></div><div class="group-card-bottom"><span class="group-members-count">${members === null ? `Capacity: ${esc(g.maxMembers ?? '—')}` : `${members} / ${esc(g.maxMembers ?? '—')} members`}</span><button class="open-group" data-open-group="${id}">Open group →</button></div></article>`;
}
function renderGroups(search = '') {
  const filtered = state.groups.filter(g => String(g.name || '').toLowerCase().includes(search.toLowerCase()));
  const cards = filtered.length ? filtered.map(groupCard).join('') : `<div class="empty-state"><strong>${search ? 'No matching groups' : 'Your workspace is ready'}</strong>${search ? 'Try a different search.' : 'Create a chit group to get started, or register another user and add them by member ID.'}<br>${!search ? '<button class="btn btn-primary" data-action="new-group">＋ Create your first group</button>' : ''}</div>`;
  $('#dashboard-groups').innerHTML = state.groups.length ? state.groups.slice(0,4).map(groupCard).join('') : `<div class="empty-state"><strong>No groups yet</strong>Create your first group to start managing contributions transparently.<br><button class="btn btn-primary" data-action="new-group">＋ Create a group</button></div>`;
  $('#all-groups').innerHTML = cards; $('#group-count').textContent = `${filtered.length} group${filtered.length === 1 ? '' : 's'}`;
}

async function openGroup(id) {
  try {
    navigate('detail'); $('#group-detail-content').innerHTML = '<div class="loading-card">Loading group details…</div>';
    const [group, members, cycles] = await Promise.all([
      api(`/chit-groups/${id}`), api(`/chit-groups/${id}/members`), api(`/chit-groups/${id}/cycles`)
    ]);
    state.currentGroup = group; state.members = Array.isArray(members) ? members : []; state.cycles = Array.isArray(cycles) ? cycles : [];
    $('#breadcrumb-current').textContent = group.name || `Group #${id}`;
    renderGroupDetail();
  } catch (e) { notify(e.message, true); if (state.currentPage !== 'groups') navigate('groups'); }
}
function memberDetails(record) { return record?.member || record || {}; }
function renderGroupDetail() {
  const g = state.currentGroup; if (!g) return;
  const isOwner = !!g.owner && Number(g.owner.id) === Number(state.auth?.memberId);
  const ownerOnly = html => isOwner ? html : '';
  const isMember = state.members.some(r => Number(memberDetails(r).id) === Number(state.auth?.memberId));
  const canParticipate = isMember && !isOwner; // only real members bid and pay, never the owner
  const membershipOpen = String(g.status || 'DRAFT').toUpperCase() === 'DRAFT';
  const memberRows = state.members.length ? state.members.map(record => { const m = memberDetails(record); return `<div class="list-row"><div><strong>${esc(m.name || `Member #${m.id ?? '—'}`)}</strong><small>${esc(m.email || '')} · Member ID ${esc(m.id ?? '—')}</small></div>${isOwner && membershipOpen ? `<button class="row-action" data-remove-member="${esc(m.id)}">Remove</button>` : '<span class="status-pill">MEMBER</span>'}</div>`; }).join('') : '<div class="mini-empty">No members yet. Add members using their member IDs.</div>';
  const cycleRows = state.cycles.length ? state.cycles.map(c => `<div class="list-row cycle-row"><div><strong>Cycle ${esc(c.cycleNumber ?? c.id)}</strong><small>${esc(shortDate(c.cycleDate))} · ${esc(c.status || 'SCHEDULED')}</small><small>Bidding: ${esc(dt(c.biddingStartTime))} → ${esc(dt(c.biddingEndTime))}</small>${c.status === 'BIDDING_OPEN' ? `<small class="cycle-note">Bidding is open until ${esc(dt(c.biddingEndTime))}. You can change your bid until then.</small>` : ''}${c.status === 'UPCOMING' ? `<small class="cycle-note">Bidding opens ${esc(dt(c.biddingStartTime))}.</small>` : ''}${c.winner ? `<small>Winner: ${esc(c.winner.name || c.winner.email || c.winner.id)} · Payout ${money(c.payout)} · ${c.payoutReleased ? 'Payout and dividends distributed ✓' : (isOwner ? 'Ready for you to distribute once every member has paid' : 'Waiting for the owner to distribute the payout')}</small>` : ''}</div><div class="cycle-actions"><button class="row-action" data-cycle-action="bids" data-cycle-id="${c.id}">Bids</button><button class="row-action" data-cycle-action="summary" data-cycle-id="${c.id}">Payments</button>${canParticipate ? `<button class="row-action" data-cycle-action="pay" data-cycle-id="${c.id}">Pay</button>` : ''}${canParticipate && c.status === 'BIDDING_OPEN' ? `<button class="row-action" data-cycle-action="bid" data-cycle-id="${c.id}">Place / edit bid</button>` : ''}${isOwner && c.status === 'COMPLETED' && c.winner && !c.payoutReleased ? `<button class="row-action primary" data-cycle-action="distribute" data-cycle-id="${c.id}">Distribute payout</button>` : ''}</div></div>`).join('') : '<div class="mini-empty">No cycles created yet.</div>';
  $('#group-detail-content').innerHTML = `
    <div class="detail-hero"><div><span class="status-pill ${statusClass(g.status)}">${esc(g.status || 'DRAFT')}</span><h1>${esc(g.name || `Group #${g.id}`)}</h1><p>Group #${esc(g.id)} · Owner: ${esc(g.owner?.name || '—')}${isOwner ? ' (you) · you organise this group, so you do not bid or pay' : ' · only the owner can add members or create cycles'}${membershipOpen ? '' : ' · members are locked'}</p></div><div class="detail-actions"><button class="btn btn-outline" data-group-action="ledger">View ledger</button><button class="btn btn-outline" data-group-action="audit">Audit trail</button>${ownerOnly(membershipOpen ? '<button class="btn btn-primary" data-action="add-member">＋ Add member</button>' : '')}</div></div>
    <div class="detail-metrics"><div class="detail-metric"><small>Chit value</small><strong>${money(g.chitValue)}</strong></div><div class="detail-metric"><small>Monthly contribution</small><strong>${money(g.monthlyContribution)}</strong></div><div class="detail-metric"><small>Member capacity</small><strong>${state.members.length} / ${esc(g.maxMembers ?? '—')}</strong></div><div class="detail-metric"><small>Duration</small><strong>${esc(g.durationMonths ?? '—')} months</strong></div></div>
    <div class="detail-columns"><section class="panel"><div class="panel-heading"><div><h3>Group members</h3><p class="subtle">${state.members.length} registered member${state.members.length===1?'':'s'}</p></div>${ownerOnly(membershipOpen ? '<button class="mini-action" data-action="add-member">＋ Add</button>' : '<span class="subtle">Members locked</span>')}</div>${memberRows}</section><section class="panel"><div class="panel-heading"><div><h3>Monthly cycles</h3><p class="subtle">Bidding, contributions and settlements.</p></div>${ownerOnly('<button class="mini-action" data-action="create-cycle">＋ Cycle</button>')}</div>${cycleRows}</section></div>
    <div id="detail-extra"></div>`;
}

function openGroupDialog() {
  hideInline('group-form-error');
  const form = $('#group-form'); form.reset();
  form.elements.organiserCommissionRate.value = '5'; form.elements.maxMembers.value = '20'; form.elements.durationMonths.value = '20';
  const date = new Date(); date.setDate(date.getDate()+7); form.elements.startDate.value = localDateString(date);
  $('#group-dialog').showModal();
}
function openActionDialog({type,title,description,fields,submit='Continue',handler}) {
  const dialog = $('#action-dialog'), form = $('#action-form');
  hideInline('action-error'); form.dataset.actionType = type; form.dataset.handler = type;
  $('#action-title').textContent = title; $('#action-description').textContent = description || ''; $('#action-submit').textContent = submit;
  $('#action-fields').innerHTML = fields.map(f => `<label>${esc(f.label)}${f.type === 'select' ? `<select name="${esc(f.name)}" ${f.required?'required':''}>${f.options.map(o=>`<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('')}</select>` : `<input name="${esc(f.name)}" type="${esc(f.type || 'text')}" ${f.min!==undefined?`min="${esc(f.min)}"`:''} ${f.step?`step="${esc(f.step)}"`:''} ${f.required?'required':''} ${f.value!==undefined?`value="${esc(f.value)}"`:''} ${f.placeholder?`placeholder="${esc(f.placeholder)}"`:''}>`}</label>`).join('');
  form._handler = handler; dialog.showModal();
}
async function handleActionSubmit(form) {
  const type = form.dataset.actionType, fd = new FormData(form), v = Object.fromEntries(fd.entries());
  try {
    if (type === 'add-member') {
      await api(`/chit-groups/${state.currentGroup.id}/members/${encodeURIComponent(v.memberId)}`, {method:'POST'}); notify('Member added to the group.'); $('#action-dialog').close(); await openGroup(state.currentGroup.id);
    } else if (type === 'create-cycle') {
      const q = new URLSearchParams({cycleNumber:v.cycleNumber,cycleDate:v.cycleDate,biddingStartTime:v.biddingStartTime,biddingEndTime:v.biddingEndTime});
      await api(`/chit-groups/${state.currentGroup.id}/cycles?${q}`, {method:'POST'}); notify('Cycle created.'); $('#action-dialog').close(); await openGroup(state.currentGroup.id);
    } else if (type === 'place-bid') {
      await api(`/cycles/${v.cycleId}/bids`, {method:'POST',body:JSON.stringify({memberId:Number(v.memberId),bidAmount:Number(v.bidAmount)})}); notify('Bid saved. You can change it until bidding closes.'); $('#action-dialog').close(); await showCycleData('bids',v.cycleId);
    } else if (type === 'pay-contribution') {
      await api(`/cycles/${v.cycleId}/payments/${v.memberId}`, {method:'POST'}); notify('Contribution payment recorded.'); $('#action-dialog').close(); await showCycleData('summary',v.cycleId);
    }
  } catch(e) { showInline('action-error',e.message); }
}

async function showCycleData(kind, cycleId) {
  const extra = $('#detail-extra'); if (!extra) return;
  extra.innerHTML = '<section class="panel"><div class="loading-card">Loading cycle data…</div></section>';
  try {
    if (kind === 'bids') {
      const bids = await api(`/cycles/${cycleId}/bids`);
      extra.innerHTML = `<section class="panel"><div class="panel-heading"><div><h3>Bids for cycle #${esc(cycleId)}</h3><p class="subtle">Lowest valid bid wins under the configured rules.</p></div><button class="mini-action" data-close-extra>Close</button></div>${Array.isArray(bids)&&bids.length?renderTable(['Bid ID','Member','Amount','Status','Placed'],bids.map(b=>[b.id,b.memberName || b.member?.name || b.memberId,money(b.bidAmount),b.status,b.createdAt ? new Date(b.createdAt).toLocaleString() : '—'])):'<div class="mini-empty">No bids have been placed for this cycle.</div>'}</section>`;
    } else if (kind === 'summary') {
      const summary = await api(`/cycles/${cycleId}/payment-summary`);
      extra.innerHTML = `<section class="panel"><div class="panel-heading"><div><h3>Payment summary · Cycle #${esc(cycleId)}</h3><p class="subtle">Contribution status overview.</p></div><button class="mini-action" data-close-extra>Close</button></div>${renderObject(summary)}</section>`;
    }
    extra.scrollIntoView({behavior:'smooth',block:'start'});
  } catch(e) { extra.innerHTML = `<section class="panel"><div class="inline-error">${esc(e.message)}</div><button class="mini-action" data-close-extra>Close</button></section>`; }
}
function renderTable(headers, rows) { return `<div class="table-wrap"><table class="data-table"><thead><tr>${headers.map(h=>`<th>${esc(h)}</th>`).join('')}</tr></thead><tbody>${rows.map(row=>`<tr>${row.map(cell=>`<td>${esc(cell ?? '—')}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`; }
function renderObject(obj) {
  if (!obj || typeof obj !== 'object') return `<pre>${esc(obj)}</pre>`;
  const entries = Object.entries(obj).filter(([k]) => !['hibernateLazyInitializer','handler'].includes(k));
  return `<div class="detail-metrics">${entries.map(([k,v])=>`<div class="detail-metric"><small>${esc(k.replace(/([A-Z])/g,' $1').replace(/^./,c=>c.toUpperCase()))}</small><strong>${esc(v && typeof v==='object' ? (v.name || v.id || JSON.stringify(v)) : v ?? '—')}</strong></div>`).join('')}</div>`;
}
async function showGroupData(kind) {
  const extra = $('#detail-extra'); if (!extra || !state.currentGroup) return;
  extra.innerHTML = '<section class="panel"><div class="loading-card">Loading records…</div></section>';
  try {
    const data = await api(`/chit-groups/${state.currentGroup.id}/${kind === 'audit' ? 'audit-logs' : 'ledger'}`);
    const title = kind === 'audit' ? 'Audit trail' : 'Group ledger';
    if (!Array.isArray(data) || !data.length) extra.innerHTML = `<section class="panel"><div class="panel-heading"><h3>${title}</h3><button class="mini-action" data-close-extra>Close</button></div><div class="mini-empty">No records found yet.</div></section>`;
    else {
      const keys = Object.keys(data[0]).filter(k=>!['handler','hibernateLazyInitializer'].includes(k) && typeof data[0][k] !== 'object').slice(0,6);
      extra.innerHTML = `<section class="panel"><div class="panel-heading"><div><h3>${title}</h3><p class="subtle">${data.length} record${data.length===1?'':'s'} returned.</p></div><button class="mini-action" data-close-extra>Close</button></div>${renderTable(keys.map(k=>k.replace(/([A-Z])/g,' $1')),data.map(row=>keys.map(k=>row[k])))}</section>`;
    }
    extra.scrollIntoView({behavior:'smooth',block:'start'});
  } catch(e) { extra.innerHTML = `<section class="panel"><div class="inline-error">${esc(e.message)}</div><button class="mini-action" data-close-extra>Close</button></section>`; }
}

async function refreshWallet() {
  const memberId = state.auth?.memberId;
  if (!memberId) { $('#wallet-balance').textContent = 'Unavailable'; $('#wallet-transactions').innerHTML = '<div class="empty-state">Your account response did not include a member ID.</div>'; return; }
  const [wallet, transactions] = await Promise.all([api(`/members/${memberId}/wallet`), api(`/members/${memberId}/wallet/transactions`)]);
  $('#wallet-balance').textContent = money(wallet.balance ?? wallet.availableBalance ?? wallet.amount ?? 0);
  if (!Array.isArray(transactions) || !transactions.length) $('#wallet-transactions').innerHTML = '<div class="empty-state">No transactions yet. Add demo funds to try the wallet flow.</div>';
  else $('#wallet-transactions').innerHTML = renderTable(['Type','Amount','Balance after','Group / cycle','Date'],transactions.map(t=>[t.type || t.transactionType, money(t.amount), money(t.balanceAfter), t.cycle?.cycleNumber ? `Cycle ${t.cycle.cycleNumber}` : (t.cycleId || '—'), t.createdAt ? new Date(t.createdAt).toLocaleString() : '—']));
}

// Auth flows
$$('[data-auth-tab]').forEach(btn => btn.addEventListener('click', () => setAuthTab(btn.dataset.authTab)));
$('#login-form').addEventListener('submit', async e => {
  e.preventDefault(); hideInline('auth-error'); const fd = new FormData(e.currentTarget);
  try { const data = await api('/auth/login',{method:'POST',body:JSON.stringify(Object.fromEntries(fd.entries()))}); saveAuth(data); showApp(); notify('Signed in successfully.'); }
  catch(err) { showInline('auth-error',err.message); }
});
$('#register-form').addEventListener('submit', async e => {
  e.preventDefault(); hideInline('auth-error'); const fd = new FormData(e.currentTarget); const form = Object.fromEntries(fd.entries());
  try { const data = await api('/auth/register',{method:'POST',body:JSON.stringify(form)}); saveAuth({...data,name:form.name}); showApp(); notify('Account created. Welcome to ClearChit!'); }
  catch(err) { showInline('auth-error',err.message); }
});
$('#logout-btn').addEventListener('click', () => { clearAuth(); showAuth(); notify('You have signed out.'); });
$$('[data-page]').forEach(btn => btn.addEventListener('click', () => navigate(btn.dataset.page)));
$('#group-search').addEventListener('input', e => renderGroups(e.target.value));
$('#back-to-groups').addEventListener('click', () => navigate('groups'));
$('#refresh-wallet').addEventListener('click', () => refreshWallet().then(()=>notify('Wallet refreshed.')).catch(e=>notify(e.message,true)));

document.addEventListener('click', async e => {
  const open = e.target.closest('[data-open-group]'); if (open) return openGroup(open.dataset.openGroup);
  const newGroup = e.target.closest('[data-action="new-group"]'); if (newGroup) return openGroupDialog();
  const addMember = e.target.closest('[data-action="add-member"]'); if (addMember) return openActionDialog({type:'add-member',title:'Add a group member',description:'Enter the member ID of a registered participant. As the owner you organise the group, so do not add yourself. Members can only be added while the group is in DRAFT.',fields:[{name:'memberId',label:'Member ID',type:'number',min:1,required:true,placeholder:'e.g. 16'}],submit:'Add member'});
  const createCycle = e.target.closest('[data-action="create-cycle"]'); if (createCycle) {
    const now = new Date(), start = new Date(now.getTime()+60*60*1000), end = new Date(now.getTime()+2*60*60*1000);
    const localDateTime = d => `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:00`;
    return openActionDialog({type:'create-cycle',title:'Create a monthly cycle',description:'Set when bidding opens and closes. When the start time arrives the group becomes ACTIVE and members can bid; when it ends the winner is decided and you distribute the payout.',fields:[{name:'cycleNumber',label:'Cycle number',type:'number',min:1,required:true,value:Math.max(1,...state.cycles.map(c=>Number(c.cycleNumber)||0))+1},{name:'cycleDate',label:'Cycle date',type:'date',required:true,value:localDateString(now)},{name:'biddingStartTime',label:'Bidding starts',type:'datetime-local',required:true,value:localDateTime(start).slice(0,16)},{name:'biddingEndTime',label:'Bidding ends',type:'datetime-local',required:true,value:localDateTime(end).slice(0,16)}],submit:'Create cycle'});
  }
  const removeMember = e.target.closest('[data-remove-member]'); if (removeMember) {
    if (!confirm('Remove this member from the group?')) return;
    try { await api(`/chit-groups/${state.currentGroup.id}/members/${encodeURIComponent(removeMember.dataset.removeMember)}`, {method:'DELETE'}); notify('Member removed.'); await openGroup(state.currentGroup.id); }
    catch (err) { notify(err.message, true); }
    return;
  }
  const cycle = e.target.closest('[data-cycle-action]'); if (cycle) {
    const id = cycle.dataset.cycleId, kind = cycle.dataset.cycleAction;
    if (kind === 'bids') return showCycleData('bids',id);
    if (kind === 'summary') return showCycleData('summary',id);
    if (kind === 'bid') {
      let mine = null;
      try { const bids = await api(`/cycles/${id}/bids`); mine = (Array.isArray(bids) ? bids : []).find(b => Number(b.memberId) === Number(state.auth?.memberId)) || null; } catch { /* dialog still works without it */ }
      return openActionDialog({type:'place-bid',title:`${mine ? 'Edit your bid' : 'Place a bid'} · Cycle #${id}`,description:mine ? `Your current bid is ${money(mine.bidAmount)}. You can change it until bidding closes; the lowest bid wins.` : 'Enter the payout amount you are willing to accept. The lowest bid wins. You can change your bid until bidding closes.',fields:[{name:'cycleId',label:'Cycle ID',type:'number',required:true,value:id},{name:'memberId',label:'Your member ID',type:'number',required:true,value:state.auth?.memberId},{name:'bidAmount',label:'Bid amount (₹)',type:'number',min:0.01,step:'0.01',required:true,placeholder:'90000',value:mine ? mine.bidAmount : undefined}],submit:mine ? 'Update bid' : 'Submit bid'});
    }
    if (kind === 'distribute') {
      if (!confirm('Distribute the payout, dividends and your commission for this cycle now? This cannot be undone.')) return;
      try { await api(`/cycles/${id}/distribute-payout`, {method:'POST'}); notify('Payout and dividends distributed.'); await openGroup(state.currentGroup.id); }
      catch (err) { notify(err.message, true); }
      return;
    }
    if (kind === 'pay') return openActionDialog({type:'pay-contribution',title:`Pay contribution · Cycle #${id}`,description:'This records a contribution against your demo wallet. Ensure your wallet has sufficient funds.',fields:[{name:'cycleId',label:'Cycle ID',type:'number',required:true,value:id},{name:'memberId',label:'Member ID',type:'number',required:true,value:state.auth?.memberId}],submit:'Pay contribution'});
  }
  const groupAction = e.target.closest('[data-group-action]'); if (groupAction) return showGroupData(groupAction.dataset.groupAction);
  if (e.target.closest('[data-close-dialog]')) { e.target.closest('dialog')?.close(); return; }
  if (e.target.closest('[data-close-extra]')) { $('#detail-extra').innerHTML = ''; return; }
});

$('#group-form').addEventListener('submit', async e => {
  e.preventDefault(); hideInline('group-form-error'); const fd = new FormData(e.currentTarget); const values = Object.fromEntries(fd.entries());
  ['chitValue','monthlyContribution','organiserCommissionRate'].forEach(k=>values[k]=Number(values[k])); ['maxMembers','durationMonths'].forEach(k=>values[k]=Number(values[k]));
  try { const group = await api('/chit-groups',{method:'POST',body:JSON.stringify(values)}); $('#group-dialog').close(); notify(`Group created${group?.id ? ` (ID ${group.id})` : ''}. Add registered members by their member IDs.`); await refreshGroups(); if (group?.id) await openGroup(group.id); else navigate('groups'); }
  catch(err) { showInline('group-form-error',err.message); }
});
$('#action-form').addEventListener('submit', async e => { e.preventDefault(); await handleActionSubmit(e.currentTarget); });
$('#recharge-form').addEventListener('submit', async e => { e.preventDefault(); const form = e.currentTarget; const amount = Number(new FormData(form).get('amount')); try { await api(`/members/${state.auth.memberId}/wallet/recharge?amount=${encodeURIComponent(amount)}`,{method:'POST'}); form.reset(); await refreshWallet(); notify('Demo funds added to your wallet.'); } catch(err) { notify(err.message,true); } });
$('#withdraw-form').addEventListener('submit', async e => { e.preventDefault(); const form = e.currentTarget; const amount = Number(new FormData(form).get('amount')); try { await api(`/members/${state.auth.memberId}/wallet/withdraw?amount=${encodeURIComponent(amount)}`,{method:'POST'}); form.reset(); await refreshWallet(); notify('Wallet withdrawal recorded.'); } catch(err) { notify(err.message,true); } });

// Start from the saved session when one exists.
if (state.auth?.token) showApp(); else showAuth();

// Keep the open group's cycles fresh so bidding opening/closing shows up on its own.
setInterval(async () => {
  if (document.hidden || !state.auth || !state.currentGroup || state.currentPage !== 'detail') return;
  if (document.querySelector('dialog[open]')) return;
  const id = state.currentGroup.id;
  try {
    const [group, cycles] = await Promise.all([api(`/chit-groups/${id}`), api(`/chit-groups/${id}/cycles`)]);
    if (!state.currentGroup || state.currentGroup.id !== id || state.currentPage !== 'detail') return;
    const keep = $('#detail-extra')?.innerHTML || '';
    state.currentGroup = group; state.cycles = Array.isArray(cycles) ? cycles : [];
    renderGroupDetail();
    if (keep && $('#detail-extra')) $('#detail-extra').innerHTML = keep;
  } catch { /* ignore transient errors */ }
}, 10000);
