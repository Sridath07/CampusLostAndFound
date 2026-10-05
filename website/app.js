(() => {
  const STORE_KEY = 'campusFoundReports.v1';
  const $ = (selector) => document.querySelector(selector);
  const grid = $('#report-grid');
  const form = $('#report-form');
  let reports = loadReports();
  let supabaseAvailable = false;
  let activeFilter = 'ALL';
  let suggestedMatchId = null;
  let toastTimer;

  function loadReports() {
    try {
      const value = JSON.parse(localStorage.getItem(STORE_KEY) || '[]');
      return Array.isArray(value) ? value.filter((r) => r && Number.isInteger(r.id)) : [];
    } catch { return []; }
  }
  function save() {
    if (!supabaseAvailable) localStorage.setItem(STORE_KEY, JSON.stringify(reports));
    render();
  }
  const SUPABASE = window.CAMPUS_SUPABASE;
  const PUBLIC_COLUMNS = 'id,type,title,description,category,location,report_date,reporter_name,status';
  function fromSupabase(row) {
    return {id: row.id, type: row.type, title: row.title, description: row.description,
      category: row.category, location: row.location, date: row.report_date,
      name: row.reporter_name, campusId: '', contact: '', status: row.status, claimedBy: ''};
  }
  async function supabaseRequest(path, {method = 'GET', body} = {}) {
    const response = await fetch(`${SUPABASE.url}/rest/v1/${path}`, {
      method,
      headers: {
        apikey: SUPABASE.publishableKey,
        Accept: 'application/json',
        'Content-Type': 'application/json',
        Prefer: 'return=representation'
      },
      body: body == null ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    let result = [];
    try { result = text ? JSON.parse(text) : []; } catch { result = []; }
    if (!response.ok) throw new Error(result.message || result.hint || result.error_description || `Supabase request failed (${response.status}).`);
    return result;
  }
  async function connectDatabase() {
    try {
      if (!SUPABASE?.url || !SUPABASE?.publishableKey) throw new Error('Supabase project settings are missing.');
      const rows = await supabaseRequest(`reports?select=${PUBLIC_COLUMNS}&order=id.desc`);
      reports = rows.map(fromSupabase);
      supabaseAvailable = true;
      $('#storage-mode').textContent = 'Reports are stored in the shared Supabase project.';
      render();
      showToast('Connected to the shared Supabase database.');
    } catch (error) {
      supabaseAvailable = false;
      reports = loadReports();
      $('#storage-mode').textContent = 'Supabase is not connected. Reports are currently saved in this browser only.';
      render();
      showToast(error.message || 'Could not connect to Supabase. Check the setup SQL and project settings.');
    }
  }
  function safe(value) {
    return String(value ?? '').replace(/[&<>"']/g, (ch) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  }
  function niceDate(value) {
    if (!value) return 'DATE UNKNOWN';
    const date = new Date(`${value}T12:00:00`);
    return Number.isNaN(date.getTime()) ? 'DATE UNKNOWN' : date.toLocaleDateString(undefined, {month:'short', day:'numeric'}).toUpperCase();
  }
  function iconFor(category) {
    return ({Electronics:'⌁',Keys:'⚿',Clothing:'◌',Bags:'▱',Books:'▤',Bottles:'◉',Other:'✳'})[category] || '✳';
  }
  function matchScore(a, b) {
    const words = `${a.title} ${a.category} ${a.description}`.toLowerCase().split(/\W+/).filter((w) => w.length > 2);
    const haystack = `${b.title} ${b.category} ${b.description}`.toLowerCase();
    let score = words.reduce((sum, word) => sum + (haystack.includes(word) ? 1 : 0), 0);
    if (b.location && b.location.toLowerCase() !== 'unknown') score += 1;
    return score;
  }
  function findMatches(report) {
    if (!report) return [];
    return reports.filter((candidate) => candidate.type !== report.type && candidate.status === 'OPEN')
      .map((candidate) => ({candidate, score: matchScore(report, candidate)}))
      .filter((entry) => entry.score > 0)
      .sort((a, b) => b.score - a.score || a.candidate.id - b.candidate.id)
      .slice(0, 5).map((entry) => entry.candidate);
  }
  let revealObserver = null;
  const prefersLessMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function animateNumber(element, target) {
    if (element._countFrame) cancelAnimationFrame(element._countFrame);
    const current = Number(element.textContent) || 0;
    if (current === target || prefersLessMotion) {
      element.textContent = target;
      element.dataset.current = target;
      return;
    }
    const start = performance.now();
    const duration = 520;
    const tick = (now) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      element.textContent = Math.round(current + (target - current) * eased);
      if (progress < 1) element._countFrame = requestAnimationFrame(tick);
      else { element.dataset.current = target; delete element._countFrame; }
    };
    element._countFrame = requestAnimationFrame(tick);
  }

  function observeReveals(root = document) {
    root.querySelectorAll('[data-reveal]:not([data-reveal-ready])').forEach((element) => {
      element.dataset.revealReady = 'true';
      if (revealObserver) revealObserver.observe(element);
      else element.classList.add('is-visible');
    });
  }

  function setupMotion() {
    if (prefersLessMotion || !('IntersectionObserver' in window)) return;
    document.documentElement.classList.add('motion-ready');
    revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -28px 0px' });
    document.querySelectorAll('.stats > div, .section-heading, .board-tools, .report-intro, .report-form, .how-heading, .how-steps article, .project-note > div, .project-caption').forEach((element, index) => {
      element.dataset.reveal = 'up';
      if (element.matches('.stats > div, .how-steps article')) element.style.setProperty('--reveal-delay', `${(index % 3) * 80}ms`);
    });
    observeReveals();
  }

  function updateStats() {
    const open = reports.filter((r) => r.status === 'OPEN');
    animateNumber($('#stat-open'), open.length);
    animateNumber($('#stat-lost'), open.filter((r) => r.type === 'LOST').length);
    animateNumber($('#stat-found'), open.filter((r) => r.type === 'FOUND').length);
  }
  function card(report, matched = false) {
    const isOpen = report.status === 'OPEN';
    const action = isOpen
      ? `<button class="card-action" data-action="claim" data-id="${report.id}">Claim this report ↗</button>`
      : report.status === 'CLAIMED'
        ? `<button class="card-action" data-action="return" data-id="${report.id}">Mark returned ✓</button>`
        : `<button class="card-action" disabled>Handoff complete ✓</button>`;
    const glyphClass = safe(report.category.toLowerCase());
    return `<article class="report-card" data-reveal="up" id="report-${report.id}">
      <div class="card-top"><span class="type-tag ${report.type === 'FOUND' ? 'found' : ''}">${report.type === 'LOST' ? 'LOST ITEM' : 'FOUND ITEM'}</span><span class="card-status">${safe(report.status)}</span><span class="item-glyph ${glyphClass}" aria-hidden="true">${iconFor(report.category)}</span></div>
      <div class="card-body"><div class="card-meta"><span>${safe(report.category.toUpperCase())}</span><span>${niceDate(report.date)}</span></div>
      <h3>${safe(report.title)}${matched ? '<span class="matched-badge">POSSIBLE MATCH</span>' : ''}</h3>
      <p>${safe(report.description)}</p><div class="card-location"><b>⌖</b>${safe(report.location)}</div>
      <div class="card-footer"><span class="reporter">Posted by ${safe(report.name)} · #${report.id}</span>${action}</div></div></article>`;
  }
  function render() {
    const query = $('#search').value.trim().toLowerCase();
    const category = $('#category').value.toLowerCase();
    const filtered = reports.filter((r) => {
      const matchesType = activeFilter === 'ALL' || r.type === activeFilter;
      const matchesCategory = !category || r.category.toLowerCase() === category;
      const text = `${r.title} ${r.description} ${r.category} ${r.location} ${r.name}`.toLowerCase();
      return matchesType && matchesCategory && (!query || text.includes(query));
    }).sort((a,b) => b.id - a.id);
    const pairedIds = new Set();
    for (const report of reports) {
      if (report.status !== 'OPEN') continue;
      const match = findMatches(report).find((candidate) => candidate.id !== report.id);
      if (match) { pairedIds.add(report.id); pairedIds.add(match.id); }
    }
    grid.innerHTML = filtered.map((r) => card(r, pairedIds.has(r.id))).join('');
    observeReveals(grid);
    const empty = filtered.length === 0;
    $('#empty-state').classList.toggle('visible', empty);
    $('#report-grid').style.display = empty ? 'none' : 'grid';
    $('#report-count').textContent = `${reports.length} ${reports.length === 1 ? 'report' : 'reports'}`;
    $('#showing-count').textContent = filtered.length;
    $('#count-all').textContent = reports.length;
    updateStats();
    renderMatchBanner();
  }
  function renderMatchBanner() {
    const banner = $('#match-banner');
    if (suggestedMatchId == null) { banner.hidden = true; return; }
    const source = reports.find((r) => r.id === suggestedMatchId);
    const match = source && findMatches(source)[0];
    if (!source || !match) { banner.hidden = true; suggestedMatchId = null; return; }
    banner.hidden = false;
    $('#match-message').textContent = `“${match.title}” may match report #${source.id} for “${source.title}”. Compare the details before contacting the reporter.`;
    $('#view-match').onclick = () => { activeFilter = 'ALL'; $('.filter.active')?.classList.remove('active'); $('[data-filter="ALL"]').classList.add('active'); $('#search').value = match.title; render(); $('#browse').scrollIntoView({behavior:'smooth'}); document.getElementById(`report-${match.id}`)?.classList.add('match-highlight'); };
  }
  function showToast(message) {
    const toast = $('#toast'); toast.textContent = message; toast.classList.add('show'); clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.classList.remove('show'), 3200);
  }
  function showSuggestedMatch(report) {
    const matches = findMatches(report);
    if (matches.length) { suggestedMatchId = report.id; render(); }
    else { suggestedMatchId = null; render(); }
  }
  document.querySelectorAll('.filter').forEach((button) => button.addEventListener('click', () => {
    activeFilter = button.dataset.filter;
    document.querySelector('.filter.active')?.classList.remove('active'); button.classList.add('active'); render();
  }));
  $('#search').addEventListener('input', render);
  $('#category').addEventListener('change', render);
  $('#clear-filters').addEventListener('click', () => { $('#search').value=''; $('#category').value=''; activeFilter='ALL'; document.querySelector('.filter.active')?.classList.remove('active'); $('[data-filter="ALL"]').classList.add('active'); render(); });
  $('#close-match').addEventListener('click', () => { suggestedMatchId=null; render(); });
  $('#empty-report').addEventListener('click', () => $('#report').scrollIntoView({behavior:'smooth'}));
  document.querySelectorAll('.type-option').forEach((button) => button.addEventListener('click', () => {
    document.querySelector('.type-option.selected')?.classList.remove('selected'); button.classList.add('selected'); $('#report-type').value=button.dataset.type;
  }));
  $('#item-description').addEventListener('input', (event) => { $('#char-count').textContent=event.target.value.length; });
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const report={id:reports.reduce((max,r)=>Math.max(max,r.id),0)+1,type:$('#report-type').value,title:$('#item-title').value.trim(),category:$('#item-category').value,description:$('#item-description').value.trim(),location:$('#item-location').value.trim(),date:$('#item-date').value,name:$('#person-name').value.trim(),campusId:$('#person-id').value.trim(),contact:$('#person-contact').value.trim(),status:'OPEN',claimedBy:''};
    const requiredFields = [report.title, report.category, report.description, report.location, report.date, report.name, report.campusId, report.contact];
    if (requiredFields.some((value) => !value.trim())) { $('#form-message').textContent='Please complete every field.'; $('#form-message').classList.add('error'); return; }
    try {
      let savedReport = report;
      if (supabaseAvailable) {
        const rows = await supabaseRequest(`reports?select=${PUBLIC_COLUMNS}`, {method: 'POST', body: {
          type: report.type, title: report.title, category: report.category, description: report.description,
          location: report.location, report_date: report.date, reporter_name: report.name,
          reporter_campus_id: report.campusId, reporter_contact: report.contact,
          status: 'OPEN', claimed_by: ''
        }});
        if (!rows.length) throw new Error('Supabase did not return the saved report.');
        savedReport = fromSupabase(rows[0]);
      }
      reports.push(savedReport);
      save();
      form.reset(); $('#report-type').value='LOST'; document.querySelectorAll('.type-option').forEach((b)=>b.classList.toggle('selected',b.dataset.type==='LOST'));
      $('#char-count').textContent='0'; $('#form-message').textContent='Your report is on the board. Thanks for helping!'; $('#form-message').classList.remove('error');
      showToast(`Report #${savedReport.id} added to ${supabaseAvailable ? 'the shared database' : 'this browser'}.`);
      showSuggestedMatch(savedReport); $('#browse').scrollIntoView({behavior:'smooth'});
    } catch (error) {
      $('#form-message').textContent=supabaseAvailable ? (error.message || 'Could not save to Supabase.') : 'Supabase is offline. This browser could not save the report; check the project connection and try again.';
      $('#form-message').classList.add('error');
    }
  });
  grid.addEventListener('click', async (event) => {
    const button=event.target.closest('[data-action]'); if(!button)return;
    const report=reports.find((r)=>r.id===Number(button.dataset.id)); if(!report)return;
    if(button.dataset.action==='claim'){
      const id=window.prompt('Enter the claimant’s campus ID to claim this report:');
      if(id==null)return;
      if(!id.trim()){showToast('A campus ID is required to claim a report.');return;}
      if(report.status!=='OPEN'){showToast('Only an open report can be claimed.');return;}
      try {
        if (supabaseAvailable) {
          const rows = await supabaseRequest(`reports?id=eq.${report.id}&status=eq.OPEN&select=${PUBLIC_COLUMNS}`, {
            method: 'PATCH', body: {status: 'CLAIMED', claimed_by: id.trim()}
          });
          if (!rows.length) throw new Error('This report was already claimed or cannot be updated.');
          Object.assign(report, fromSupabase(rows[0]), {campusId: id.trim(), claimedBy: id.trim()});
        }
        else { report.status='CLAIMED'; report.claimedBy=id.trim(); }
        save(); showToast(`Report #${report.id} is now claimed.`);
      } catch (error) { showToast(error.message || 'Could not update the report.'); }
    } else if(button.dataset.action==='return'){
      if(report.status!=='CLAIMED'){showToast('Claim the report before marking it returned.');return;}
      try {
        if (supabaseAvailable) {
          const rows = await supabaseRequest(`reports?id=eq.${report.id}&status=eq.CLAIMED&select=${PUBLIC_COLUMNS}`, {
            method: 'PATCH', body: {status: 'RETURNED'}
          });
          if (!rows.length) throw new Error('This report was already updated or cannot be returned.');
          Object.assign(report, fromSupabase(rows[0]));
        }
        else report.status='RETURNED';
        save(); showToast(`Report #${report.id} was marked returned.`);
      } catch (error) { showToast(error.message || 'Could not update the report.'); }
    }
  });
  const today=new Date();today.setMinutes(today.getMinutes()-today.getTimezoneOffset());$('#item-date').value=today.toISOString().slice(0,10);
  setupMotion();
  render();
  connectDatabase();
})();
