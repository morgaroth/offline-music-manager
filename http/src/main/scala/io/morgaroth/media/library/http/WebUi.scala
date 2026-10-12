package io.morgaroth.media.library.http

/** Minimal self-contained web UI served at GET /.
  *
  * Two tabs mirroring the old ScalaFX app: a Search tab (search / add / browse)
  * and an Edit tab (full single-track editor covering every property, with a
  * Fetch action and job status). Vanilla JS + inline CSS, no build step or CDN,
  * so it works offline inside the container.
  */
object WebUi:

  val page: String =
    """<!doctype html>
      |<html lang="en">
      |<head>
      |  <meta charset="utf-8" />
      |  <meta name="viewport" content="width=device-width, initial-scale=1" />
      |  <title>Offline Media Manager</title>
      |  <style>
      |    :root { color-scheme: light dark; }
      |    * { box-sizing: border-box; }
      |    body { font-family: system-ui, -apple-system, sans-serif; margin: 0; padding: 1.5rem;
      |           max-width: 1100px; margin-inline: auto; }
      |    h1 { font-size: 1.25rem; margin: 0 0 1rem; }
      |    .tabs { display: flex; gap: .25rem; border-bottom: 2px solid #8884; margin-bottom: 1rem; }
      |    .tab { padding: .5rem 1rem; cursor: pointer; border: none; background: transparent;
      |           color: inherit; font-size: .95rem; border-bottom: 2px solid transparent; margin-bottom: -2px; }
      |    .tab.active { border-bottom-color: #3b82f6; font-weight: 600; }
      |    .panel { display: none; }
      |    .panel.active { display: block; }
      |    .bar { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: 1rem; align-items: center; }
      |    input, select, button, textarea { padding: .5rem .6rem; font-size: .95rem; border-radius: .4rem;
      |           border: 1px solid #8884; background: transparent; color: inherit; }
      |    button { cursor: pointer; }
      |    button.primary { background: #3b82f6; color: #fff; border-color: #3b82f6; }
      |    button:disabled { opacity: .4; cursor: not-allowed; }
      |    .id-readonly { font-family: monospace; font-size: .8rem; opacity: .55; align-self: center;
      |                   word-break: break-all; }
      |    .saving { outline: 2px solid #ef4444; outline-offset: 0; }
      |    .saved  { outline: 2px solid #22c55e; outline-offset: 0; }
      |    button.danger { background: #ef4444; color: #fff; border-color: #ef4444; }
      |    button.icon-btn { padding: .25rem .5rem; font-size: 1rem; line-height: 1; border: 1px solid #8884;
      |                      background: transparent; border-radius: .4rem; }
      |    button.icon-btn:hover { background: #8882; }
      |    table { width: 100%; border-collapse: collapse; font-size: .9rem; }
      |    th, td { text-align: left; padding: .4rem .5rem; border-bottom: 1px solid #8883; vertical-align: top; }
      |    th { position: sticky; top: 0; background: Canvas; }
      |    .status { font-size: .75rem; padding: .1rem .4rem; border-radius: .3rem; border: 1px solid #8886; }
      |    .muted { opacity: .6; font-size: .8rem; }
      |    .msg { margin: .5rem 0; min-height: 1.2rem; font-size: .85rem; }
      |    .err { color: #ef4444; } .ok { color: #22c55e; }
      |    a { color: #3b82f6; cursor: pointer; }
      |    .form-grid { display: grid; grid-template-columns: 160px 1fr; gap: .6rem .8rem; align-items: center;
      |                 max-width: 640px; }
      |    .form-grid label { text-align: right; opacity: .8; font-size: .9rem; }
      |    .form-grid input, .form-grid select, .form-grid textarea { width: 100%; }
      |    .actions { margin-top: 1rem; display: flex; gap: .5rem; flex-wrap: wrap; }
      |    .hint { grid-column: 2; font-size: .75rem; opacity: .55; margin-top: -.3rem; }
      |    .edit-cols { display: flex; gap: 2rem; flex-wrap: wrap; align-items: flex-start; }
      |    .edit-left { flex: 0 0 auto; }
      |    .edit-right { flex: 1 1 320px; min-width: 300px; max-width: 440px; }
      |    .info-actions { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: .6rem; }
      |    .btn-link { display: inline-block; padding: .5rem .6rem; border: 1px solid #8884; border-radius: .4rem;
      |                text-decoration: none; }
      |    .thumb { width: 100%; border-radius: .5rem; border: 1px solid #8883; display: block; margin-bottom: .6rem; }
      |    .meta { font-size: .85rem; line-height: 1.6; margin-bottom: .6rem; }
      |    .meta .k { opacity: .6; display: inline-block; min-width: 90px; }
      |    .meta-block { font-size: .82rem; }
      |    .meta-label { opacity: .6; margin-top: .5rem; }
      |    .meta-val { font-weight: 600; }
      |    .meta-desc { white-space: pre-wrap; word-break: break-word; max-height: 180px; overflow: auto;
      |                 background: #8881; padding: .5rem; border-radius: .4rem; font-size: .78rem; margin: .3rem 0 0; }
      |    .fq-stats { display: flex; gap: .5rem; flex-wrap: wrap; margin: .5rem 0 1rem; }
      |    .chip { font-size: .78rem; padding: .15rem .5rem; border-radius: 1rem; border: 1px solid #8884; }
      |    .chip.pending { background: #94a3b833; } .chip.running { background: #3b82f633; }
      |    .chip.done { background: #22c55e33; } .chip.errored { background: #ef444433; }
      |  </style>
      |</head>
      |<body>
      |  <h1>🎬 Offline Media Manager</h1>
      |  <div class="tabs">
      |    <button class="tab active" data-tab="search" onclick="showTab('search')">Search</button>
      |    <button class="tab" data-tab="edit" onclick="showTab('edit')">Edit</button>
      |    <button class="tab" data-tab="fetch" onclick="showTab('fetch'); previewFetch(); refreshQueue()">Fetch</button>
      |  </div>
      |  <div id="msg" class="msg"></div>
      |
      |  <!-- ===== Search tab ===== -->
      |  <div id="panel-search" class="panel active">
      |    <div class="bar">
      |      <input id="q" placeholder="Search artist, title, url..." size="30" />
      |      <select id="statusFilter">
      |        <option value="">any status</option>
      |        <option value="draft">draft</option>
      |        <option value="final">final</option>
      |        <option value="deleted">deleted</option>
      |      </select>
      |      <button class="primary" onclick="search()">Search</button>
      |      <input id="addUrl" placeholder="Add track by URL..." size="26" />
      |      <button onclick="addTrack()">Add</button>
      |    </div>
      |    <table>
      |      <thead><tr><th>Title</th><th>Artist</th><th>Album</th><th>Type</th><th>Status</th><th></th></tr></thead>
      |      <tbody id="rows"></tbody>
      |    </table>
      |  </div>
      |
      |  <!-- ===== Edit tab ===== -->
      |  <div id="panel-edit" class="panel">
      |    <div id="editEmpty" class="muted">No track selected. Pick one from the Search tab.</div>
      |    <div id="editForm" style="display:none">
      |     <div class="edit-cols">
      |      <div class="edit-left">
      |      <div class="form-grid">
      |        <label>ID</label><span id="f-id" class="id-readonly"></span>
      |        <label>URL</label><input id="f-url" onblur="onBlur('f-url')" />
      |        <label>Title</label><input id="f-title" onblur="onBlur('f-title')" />
      |        <label>Artist</label><input id="f-artist" onblur="onBlur('f-artist')" />
      |        <label>Album</label><input id="f-album" onblur="onBlur('f-album')" />
      |        <label>Type</label>
      |        <select id="f-type" onchange="onBlur('f-type')">
      |          <option value="music">music</option>
      |          <option value="video">video</option>
      |        </select>
      |        <label>Status</label>
      |        <select id="f-status" onchange="syncFetchBtn(); onBlur('f-status')">
      |          <option value="draft">draft</option>
      |          <option value="final">final</option>
      |          <option value="deleted">deleted</option>
      |        </select>
      |        <label>Start at</label><input id="f-startAt" placeholder="mm:ss or hh:mm:ss" onblur="onBlur('f-startAt')" />
      |        <label>End at</label><input id="f-endAt" placeholder="mm:ss or hh:mm:ss" onblur="onBlur('f-endAt')" />
      |        <label>Fade-out (s)</label><input id="f-fadeOutSeconds" type="number" min="0" onblur="onBlur('f-fadeOutSeconds')" />
      |        <label>Volume (dB)</label><input id="f-volumeChange" placeholder="e.g. -3.5" onblur="onBlur('f-volumeChange')" />
      |        <label>Playlists</label><input id="f-playlists" placeholder="comma-separated" onblur="onBlur('f-playlists')" />
      |        <div class="hint">Comma-separated playlist names.</div>
      |      </div>
      |      <div class="actions">
      |        <button class="primary" onclick="saveAll()">Save all</button>
      |        <button id="btn-fetch" onclick="doFetch()" title="Only available for final tracks">Fetch now</button>
      |        <button onclick="reloadCurrent()">Reload</button>
      |        <button class="danger" onclick="deleteTrack()">Delete</button>
      |      </div>
      |      </div>
      |      <!-- Right-side info panel: source preview + metadata to help decide settings -->
      |      <div class="edit-right">
      |        <div class="info-actions">
      |          <a id="i-open" class="btn-link" target="_blank" rel="noopener">↗ Open on YouTube</a>
      |          <button onclick="loadMeta()">Load YouTube metadata</button>
      |        </div>
      |        <a id="i-thumb-link" target="_blank" rel="noopener">
      |          <img id="i-thumb" class="thumb" alt="" style="display:none" />
      |        </a>
      |        <div id="i-meta" class="meta"></div>
      |        <div class="meta-block">
      |          <div class="meta-label">Raw title</div>
      |          <div id="i-rawTitle" class="meta-val"></div>
      |          <div class="meta-label">Raw description</div>
      |          <pre id="i-rawDesc" class="meta-desc"></pre>
      |        </div>
      |      </div>
      |     </div>
      |    </div>
      |  </div>
      |
      |  <!-- ===== Fetch tab ===== -->
      |  <div id="panel-fetch" class="panel">
      |    <div class="bar">
      |      <input id="fq-q" placeholder="Filter by name (optional)..." size="28" oninput="previewFetch()" />
      |      <select id="fq-type" onchange="previewFetch()">
      |        <option value="">any type</option>
      |        <option value="music">music</option>
      |        <option value="video">video</option>
      |      </select>
      |      <button class="primary" onclick="enqueueFetch()">Fetch</button>
      |      <span class="muted">Enqueues matching <b>final</b> tracks.</span>
      |    </div>
      |
      |    <!-- Preview of what the filters match (final tracks are the ones that enqueue). -->
      |    <div id="fq-preview-head" class="muted" style="margin:.3rem 0"></div>
      |    <table>
      |      <thead><tr><th>Title</th><th>Artist</th><th>Type</th><th>Status</th></tr></thead>
      |      <tbody id="fq-preview"></tbody>
      |    </table>
      |
      |    <h3 style="font-size:1rem;margin:1.5rem 0 .3rem">Progress</h3>
      |    <div id="fq-stats" class="fq-stats"></div>
      |    <div class="bar">
      |      <button onclick="refreshQueue()">Refresh</button>
      |      <button class="danger" onclick="killPending()">Kill pending</button>
      |      <button onclick="clearErrored()">Clear errored</button>
      |    </div>
      |    <table>
      |      <thead><tr><th>Title</th><th>Artist</th><th>Type</th><th>Status</th><th>Started</th><th>Error</th></tr></thead>
      |      <tbody id="fq-rows"></tbody>
      |    </table>
      |  </div>
      |
      |  <script>
      |    let current = null; // currently edited track
      |    let queueTimer = null;
      |
      |    const msg = (t, cls) => { const e = document.getElementById('msg'); e.textContent = t; e.className = 'msg ' + (cls || ''); };
      |    const esc = s => (s ?? '').toString().replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
      |    const val = id => document.getElementById(id).value.trim();
      |
      |    function showTab(name) {
      |      document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
      |      document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
      |      document.getElementById('panel-' + name).classList.add('active');
      |    }
      |
      |    // ---- Search ----
      |    async function search() {
      |      const q = val('q'), status = val('statusFilter');
      |      const params = new URLSearchParams();
      |      if (q) params.set('q', q);
      |      if (status) params.set('status', status);
      |      msg('Searching...');
      |      try {
      |        const res = await fetch('/tracks?' + params.toString());
      |        const tracks = await res.json();
      |        renderRows(tracks);
      |        msg(tracks.length + ' result(s)', 'ok');
      |      } catch (e) { msg('Search failed: ' + e, 'err'); }
      |    }
      |
      |    function renderRows(tracks) {
      |      const rows = document.getElementById('rows');
      |      rows.innerHTML = '';
      |      for (const t of tracks) {
      |        const tr = document.createElement('tr');
      |        tr.innerHTML =
      |          '<td>' + esc(t.title) + '</td><td>' + esc(t.artist) + '</td><td>' + esc(t.album) + '</td>' +
      |          '<td>' + (t.type === 'video' ? '🎬' : '🎵') + ' ' + esc(t.type) + '</td>' +
      |          '<td><span class="status">' + esc(t.status) + '</span></td>' +
      |          '<td><button class="icon-btn" title="Edit">✏️</button></td>';
      |        tr.querySelector('.icon-btn').onclick = () => openEditor(t);
      |        rows.appendChild(tr);
      |      }
      |    }
      |
      |    async function addTrack() {
      |      const url = val('addUrl');
      |      if (!url) { msg('Enter a URL first', 'err'); return; }
      |      msg('Adding...');
      |      try {
      |        const res = await fetch('/tracks', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ url }) });
      |        if (!res.ok) throw new Error(await res.text());
      |        const t = await res.json();
      |        document.getElementById('addUrl').value = '';
      |        msg('Added. Fill in details below.', 'ok');
      |        openEditor(t);
      |      } catch (e) { msg('Add failed: ' + e, 'err'); }
      |    }
      |
      |    // ---- Edit ----
      |    function openEditor(t) {
      |      current = t;
      |      document.getElementById('editEmpty').style.display = 'none';
      |      document.getElementById('editForm').style.display = 'block';
      |      document.getElementById('f-id').textContent = t.id;
      |      document.getElementById('f-url').value = t.url || '';
      |      document.getElementById('f-title').value = t.title || '';
      |      document.getElementById('f-artist').value = t.artist || '';
      |      document.getElementById('f-album').value = t.album || '';
      |      document.getElementById('f-type').value = t.type || 'music';
      |      document.getElementById('f-status').value = t.status || 'draft';
      |      syncFetchBtn();
      |      document.getElementById('f-startAt').value = t.startAt || '';
      |      document.getElementById('f-endAt').value = t.endAt || '';
      |      document.getElementById('f-fadeOutSeconds').value = t.fadeOutSeconds ?? '';
      |      document.getElementById('f-volumeChange').value = t.volumeChange ?? '';
      |      document.getElementById('f-playlists').value = (t.playlists || []).join(', ');
      |      populateInfo(t);
      |      showTab('edit');
      |    }
      |
      |    function youtubeId(url) {
      |      if (!url) return null;
      |      const m = url.match(/(?:v=|youtu\.be\/|\/embed\/|\/shorts\/)([A-Za-z0-9_-]{11})/);
      |      return m ? m[1] : null;
      |    }
      |
      |    function populateInfo(t) {
      |      const open = document.getElementById('i-open');
      |      open.href = t.url || '#';
      |      const vid = youtubeId(t.url);
      |      const thumb = document.getElementById('i-thumb');
      |      const thumbLink = document.getElementById('i-thumb-link');
      |      if (vid) {
      |        thumb.src = 'https://img.youtube.com/vi/' + vid + '/hqdefault.jpg';
      |        thumb.style.display = 'block';
      |        thumbLink.href = t.url;
      |      } else {
      |        thumb.style.display = 'none';
      |      }
      |      document.getElementById('i-rawTitle').textContent = t.rawTitle || '(none — fetch to populate)';
      |      document.getElementById('i-rawDesc').textContent = t.rawDescription || '';
      |      document.getElementById('i-meta').innerHTML = '';
      |    }
      |
      |    async function loadMeta() {
      |      if (!current) return;
      |      const el = document.getElementById('i-meta');
      |      el.innerHTML = '<span class="muted">Loading from YouTube...</span>';
      |      try {
      |        const res = await fetch('/tracks/' + current.id + '/metadata');
      |        if (!res.ok) throw new Error(await res.text());
      |        const m = await res.json();
      |        const row = (k, v) => v ? '<div><span class="k">' + k + '</span>' + esc(v) + '</div>' : '';
      |        el.innerHTML =
      |          row('Length', m.durationString || (m.durationSeconds ? m.durationSeconds + 's' : '')) +
      |          row('Uploader', m.uploader) +
      |          row('Uploaded', m.uploadDate) +
      |          row('YT title', m.fullTitle || m.title);
      |        // thumbnail from metadata if the URL-derived one was unavailable
      |        if (m.thumbnail) {
      |          const thumb = document.getElementById('i-thumb');
      |          if (thumb.style.display === 'none') { thumb.src = m.thumbnail; thumb.style.display = 'block'; }
      |        }
      |        // The endpoint persisted raw title/description to the DB; refresh the
      |        // panel's raw fields from the updated track.
      |        try {
      |          const tr = await fetch('/tracks/' + current.id);
      |          if (tr.ok) {
      |            const t = await tr.json();
      |            current.rawTitle = t.rawTitle;
      |            current.rawDescription = t.rawDescription;
      |            document.getElementById('i-rawTitle').textContent = t.rawTitle || '(none)';
      |            document.getElementById('i-rawDesc').textContent = t.rawDescription || '';
      |          }
      |        } catch (e) { /* panel refresh is best-effort */ }
      |      } catch (e) { el.innerHTML = '<span class="err">Metadata failed: ' + esc(e.message || e) + '</span>'; }
      |    }
      |
      |    // Fetch is only meaningful for final tracks. Reflect the CURRENT dropdown
      |    // value (not just the saved status) so it enables as soon as you pick final.
      |    function syncFetchBtn() {
      |      const isFinal = document.getElementById('f-status').value === 'final';
      |      const btn = document.getElementById('btn-fetch');
      |      btn.disabled = !isFinal;
      |      btn.title = isFinal ? 'Fetch now' : 'Save as final first';
      |    }
      |
      |    async function reloadCurrent() {
      |      if (!current) return;
      |      const res = await fetch('/tracks/' + current.id);
      |      if (res.ok) openEditor(await res.json());
      |    }
      |
      |    async function patch(path, body) {
      |      const res = await fetch('/tracks/' + current.id + '/' + path, {
      |        method: 'PATCH', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body),
      |      });
      |      if (!res.ok) throw new Error(path + ': ' + await res.text());
      |      return res.json();
      |    }
      |
      |    // field id -> { path, build(value) -> body object }. Drives save-on-blur.
      |    const FIELD_SPECS = {
      |      'f-url':            { path: 'url',               build: v => ({ url: v }) },
      |      'f-title':          { path: 'title',             build: v => ({ title: v }) },
      |      'f-artist':         { path: 'artist',            build: v => ({ artist: v }) },
      |      'f-album':          { path: 'album',             build: v => ({ album: v }) },
      |      'f-type':           { path: 'type',              build: v => ({ type: v }) },
      |      'f-status':         { path: 'status',            build: v => ({ status: v }) },
      |      'f-startAt':        { path: 'start-at',          build: v => ({ startAt: v }) },
      |      'f-endAt':          { path: 'end-at',            build: v => ({ endAt: v }) },
      |      'f-fadeOutSeconds': { path: 'fade-out-seconds',  build: v => ({ fadeOutSeconds: v }) },
      |      'f-volumeChange':   { path: 'volume-change',     build: v => ({ volumeChange: v }) },
      |      'f-playlists':      { path: 'playlists',         build: v => ({ playlists: v.split(',').map(s => s.trim()).filter(s => s) }) },
      |    };
      |    const trackField = { // which track property each field reflects, to detect changes
      |      'f-url':'url','f-title':'title','f-artist':'artist','f-album':'album','f-type':'type',
      |      'f-status':'status','f-startAt':'startAt','f-endAt':'endAt',
      |      'f-fadeOutSeconds':'fadeOutSeconds','f-volumeChange':'volumeChange','f-playlists':'playlists',
      |    };
      |
      |    // Current value of a field as a comparable string.
      |    function currentFieldStr(id) {
      |      if (id === 'f-playlists') return val(id).split(',').map(s=>s.trim()).filter(s=>s).join(',');
      |      return val(id);
      |    }
      |    // Stored value of the mapped track property, as a comparable string.
      |    function storedFieldStr(id) {
      |      const key = trackField[id];
      |      let v = current ? current[key] : '';
      |      if (key === 'playlists') return (v || []).join(',');
      |      if (v === null || v === undefined) return '';
      |      return String(v);
      |    }
      |
      |    // Save one field on blur if it changed; red while saving, green 5s on OK.
      |    async function onBlur(id) {
      |      if (!current) return;
      |      if (currentFieldStr(id) === storedFieldStr(id)) return; // unchanged
      |      const el = document.getElementById(id);
      |      const spec = FIELD_SPECS[id];
      |      el.classList.remove('saved'); el.classList.add('saving');
      |      try {
      |        const res = await fetch('/tracks/' + current.id + '/' + spec.path, {
      |          method: 'PATCH', headers: { 'content-type': 'application/json' },
      |          body: JSON.stringify(spec.build(val(id))),
      |        });
      |        const text = await res.text();
      |        if (!res.ok) {
      |          let detail = text;
      |          try { detail = JSON.parse(text).error || text; } catch (e) {}
      |          throw new Error(detail);
      |        }
      |        current = JSON.parse(text); // refresh stored track from the response
      |        el.classList.remove('saving'); el.classList.add('saved');
      |        setTimeout(() => el.classList.remove('saved'), 5000);
      |        msg('Saved ' + spec.path + '.', 'ok');
      |      } catch (e) {
      |        el.classList.remove('saving');
      |        msg('Failed to save ' + spec.path + ': ' + (e.message || e), 'err');
      |      }
      |    }
      |
      |    async function saveAll() {
      |      if (!current) return;
      |      msg('Saving...');
      |      try {
      |        // Order: url first (may reset status), then the rest, status last.
      |        await patch('url', { url: val('f-url') });
      |        await patch('title', { title: val('f-title') });
      |        await patch('artist', { artist: val('f-artist') });
      |        await patch('album', { album: val('f-album') });
      |        await patch('start-at', { startAt: val('f-startAt') });
      |        await patch('end-at', { endAt: val('f-endAt') });
      |        await patch('fade-out-seconds', { fadeOutSeconds: val('f-fadeOutSeconds') });
      |        await patch('volume-change', { volumeChange: val('f-volumeChange') });
      |        const playlists = val('f-playlists').split(',').map(s => s.trim()).filter(s => s);
      |        await patch('playlists', { playlists });
      |        await patch('type', { type: val('f-type') });
      |        const updated = await patch('status', { status: val('f-status') });
      |        current = updated;
      |        msg('Saved.', 'ok');
      |      } catch (e) { msg('Save failed: ' + e, 'err'); }
      |    }
      |
      |    async function deleteTrack() {
      |      if (!current) return;
      |      const label = (current.artist || current.title) ? (current.artist + ' - ' + current.title) : current.id;
      |      if (!confirm('Permanently delete this track?\n\n' + label)) return;
      |      msg('Deleting...');
      |      try {
      |        const res = await fetch('/tracks/' + current.id, { method: 'DELETE' });
      |        if (!res.ok && res.status !== 204) throw new Error(await res.text());
      |        current = null;
      |        document.getElementById('editForm').style.display = 'none';
      |        document.getElementById('editEmpty').style.display = 'block';
      |        msg('Deleted.', 'ok');
      |        showTab('search');
      |        search();
      |      } catch (e) { msg('Delete failed: ' + e, 'err'); }
      |    }
      |
      |    async function doFetch() {
      |      if (!current) return;
      |      msg('Starting fetch...');
      |      try {
      |        const res = await fetch('/tracks/' + current.id + '/fetch', { method: 'POST' });
      |        const job = await res.json();
      |        msg('Fetch job ' + job.jobId + ' started', 'ok');
      |        pollJob(job.jobId);
      |      } catch (e) { msg('Fetch failed: ' + e, 'err'); }
      |    }
      |
      |    async function pollJob(jobId) {
      |      try {
      |        const res = await fetch('/jobs/' + jobId);
      |        const job = await res.json();
      |        if (job.state === 'running') { setTimeout(() => pollJob(jobId), 2000); }
      |        else if (job.state === 'succeeded') { msg('Fetch succeeded', 'ok'); }
      |        else { msg('Fetch failed: ' + (job.error || 'unknown'), 'err'); }
      |      } catch (e) { /* stop polling */ }
      |    }
      |
      |    // ---- Fetch queue ----
      |    let previewTimer = null;
      |    function previewFetch() {
      |      // debounce rapid typing
      |      clearTimeout(previewTimer);
      |      previewTimer = setTimeout(doPreview, 250);
      |    }
      |    async function doPreview() {
      |      const q = val('fq-q'), type = val('fq-type');
      |      const params = new URLSearchParams();
      |      if (q) params.set('q', q);
      |      try {
      |        const res = await fetch('/tracks?' + params.toString());
      |        let tracks = await res.json();
      |        if (type) tracks = tracks.filter(t => t.type === type);
      |        const finalCount = tracks.filter(t => t.status === 'final').length;
      |        document.getElementById('fq-preview-head').innerHTML =
      |          tracks.length + ' match(es), <b>' + finalCount + ' final</b> will be enqueued';
      |        const body = document.getElementById('fq-preview');
      |        body.innerHTML = '';
      |        for (const t of tracks) {
      |          const tr = document.createElement('tr');
      |          if (t.status !== 'final') tr.style.opacity = '.45';
      |          tr.innerHTML =
      |            '<td>' + esc(t.title) + '</td><td>' + esc(t.artist) + '</td>' +
      |            '<td>' + (t.type === 'video' ? '🎬' : '🎵') + ' ' + esc(t.type) + '</td>' +
      |            '<td><span class="status">' + esc(t.status) + '</span></td>';
      |          body.appendChild(tr);
      |        }
      |      } catch (e) { /* preview is best-effort */ }
      |    }
      |
      |    async function enqueueFetch() {
      |      const q = val('fq-q'), type = val('fq-type');
      |      const params = new URLSearchParams();
      |      if (q) params.set('q', q);
      |      if (type) params.set('type', type);
      |      msg('Enqueuing...');
      |      try {
      |        const res = await fetch('/fetch-queue?' + params.toString(), { method: 'POST' });
      |        if (!res.ok) throw new Error(await res.text());
      |        const r = await res.json();
      |        msg('Matched ' + r.matched + ' final track(s), added ' + r.added + ' to the queue.', 'ok');
      |        refreshQueue();
      |      } catch (e) { msg('Enqueue failed: ' + e, 'err'); }
      |    }
      |
      |    async function refreshQueue() {
      |      try {
      |        const res = await fetch('/fetch-queue');
      |        const data = await res.json();
      |        renderQueue(data);
      |        // Poll while there's active work (pending or running).
      |        clearTimeout(queueTimer);
      |        if (data.stats.pending > 0 || data.stats.running > 0) {
      |          queueTimer = setTimeout(refreshQueue, 2000);
      |        }
      |      } catch (e) { /* ignore transient errors */ }
      |    }
      |
      |    function renderQueue(data) {
      |      const s = data.stats;
      |      document.getElementById('fq-stats').innerHTML =
      |        '<span class="chip pending">pending ' + s.pending + '</span>' +
      |        '<span class="chip running">running ' + s.running + '</span>' +
      |        '<span class="chip done">done ' + s.done + '</span>' +
      |        '<span class="chip errored">errored ' + s.errored + '</span>' +
      |        '<span class="chip">total ' + s.total + '</span>';
      |      const rows = document.getElementById('fq-rows');
      |      rows.innerHTML = '';
      |      for (const it of data.items) {
      |        const tr = document.createElement('tr');
      |        tr.innerHTML =
      |          '<td>' + esc(it.title) + '</td>' +
      |          '<td>' + esc(it.artist) + '</td>' +
      |          '<td>' + (it.type === 'video' ? '🎬' : '🎵') + ' ' + esc(it.type) + '</td>' +
      |          '<td><span class="chip ' + esc(it.status) + '">' + esc(it.status) + '</span></td>' +
      |          '<td class="muted">' + esc(it.startedAt ? it.startedAt.replace("T"," ").slice(0,19) : "") + '</td>' +
      |          '<td class="err" style="font-size:.75rem">' + esc(it.error || "") + '</td>';
      |        rows.appendChild(tr);
      |      }
      |    }
      |
      |    async function killPending() {
      |      if (!confirm('Remove all pending items? Running fetches will finish.')) return;
      |      try {
      |        const res = await fetch('/fetch-queue/pending', { method: 'DELETE' });
      |        const r = await res.json();
      |        msg('Removed ' + r.removed + ' pending item(s).', 'ok');
      |        refreshQueue();
      |      } catch (e) { msg('Kill failed: ' + e, 'err'); }
      |    }
      |
      |    async function clearErrored() {
      |      try {
      |        const res = await fetch('/fetch-queue/errored', { method: 'DELETE' });
      |        const r = await res.json();
      |        msg('Cleared ' + r.removed + ' errored item(s).', 'ok');
      |        refreshQueue();
      |      } catch (e) { msg('Clear failed: ' + e, 'err'); }
      |    }
      |
      |    search();
      |  </script>
      |</body>
      |</html>
      |""".stripMargin
