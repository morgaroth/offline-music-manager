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
      |  <title>Music Library</title>
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
      |  </style>
      |</head>
      |<body>
      |  <h1>🎵 Music Library</h1>
      |  <div class="tabs">
      |    <button class="tab active" data-tab="search" onclick="showTab('search')">Search</button>
      |    <button class="tab" data-tab="edit" onclick="showTab('edit')">Edit</button>
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
      |      <thead><tr><th>Title</th><th>Artist</th><th>Album</th><th>Status</th><th></th></tr></thead>
      |      <tbody id="rows"></tbody>
      |    </table>
      |  </div>
      |
      |  <!-- ===== Edit tab ===== -->
      |  <div id="panel-edit" class="panel">
      |    <div id="editEmpty" class="muted">No track selected. Pick one from the Search tab.</div>
      |    <div id="editForm" style="display:none">
      |      <div class="form-grid">
      |        <label>ID</label><input id="f-id" readonly />
      |        <label>URL</label><input id="f-url" />
      |        <label>Title</label><input id="f-title" />
      |        <label>Artist</label><input id="f-artist" />
      |        <label>Album</label><input id="f-album" />
      |        <label>Status</label>
      |        <select id="f-status">
      |          <option value="draft">draft</option>
      |          <option value="final">final</option>
      |          <option value="deleted">deleted</option>
      |        </select>
      |        <label>Start at</label><input id="f-startAt" placeholder="mm:ss or hh:mm:ss" />
      |        <label>End at</label><input id="f-endAt" placeholder="mm:ss or hh:mm:ss" />
      |        <label>Fade-out (s)</label><input id="f-fadeOutSeconds" type="number" min="0" />
      |        <label>Volume (dB)</label><input id="f-volumeChange" placeholder="e.g. -3.5" />
      |        <label>Playlists</label><input id="f-playlists" placeholder="comma-separated" />
      |        <div class="hint">Comma-separated playlist names.</div>
      |      </div>
      |      <div class="actions">
      |        <button class="primary" onclick="saveAll()">Save all</button>
      |        <button onclick="doFetch()">Fetch now</button>
      |        <button onclick="reloadCurrent()">Reload</button>
      |      </div>
      |    </div>
      |  </div>
      |
      |  <script>
      |    let current = null; // currently edited track
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
      |          '<td><span class="status">' + esc(t.status) + '</span></td>' +
      |          '<td><a>edit</a></td>';
      |        tr.querySelector('a').onclick = () => openEditor(t);
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
      |      document.getElementById('f-id').value = t.id;
      |      document.getElementById('f-url').value = t.url || '';
      |      document.getElementById('f-title').value = t.title || '';
      |      document.getElementById('f-artist').value = t.artist || '';
      |      document.getElementById('f-album').value = t.album || '';
      |      document.getElementById('f-status').value = t.status || 'draft';
      |      document.getElementById('f-startAt').value = t.startAt || '';
      |      document.getElementById('f-endAt').value = t.endAt || '';
      |      document.getElementById('f-fadeOutSeconds').value = t.fadeOutSeconds ?? '';
      |      document.getElementById('f-volumeChange').value = t.volumeChange ?? '';
      |      document.getElementById('f-playlists').value = (t.playlists || []).join(', ');
      |      showTab('edit');
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
      |        const updated = await patch('status', { status: val('f-status') });
      |        current = updated;
      |        msg('Saved.', 'ok');
      |      } catch (e) { msg('Save failed: ' + e, 'err'); }
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
      |    search();
      |  </script>
      |</body>
      |</html>
      |""".stripMargin
