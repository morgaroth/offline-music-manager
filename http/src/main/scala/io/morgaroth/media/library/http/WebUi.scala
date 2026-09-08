package io.morgaroth.media.library.http

/** Minimal self-contained web UI served at GET /.
  *
  * Single HTML page (inline CSS + vanilla JS) that talks to the same JSON API
  * this server exposes. Intentionally dependency-free so it works offline
  * inside the Home Assistant add-on with no build step or CDN.
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
      |    .bar { display: flex; gap: .5rem; flex-wrap: wrap; margin-bottom: 1rem; }
      |    input, select, button { padding: .5rem .6rem; font-size: .95rem; border-radius: .4rem;
      |           border: 1px solid #8884; background: transparent; color: inherit; }
      |    button { cursor: pointer; }
      |    button.primary { background: #3b82f6; color: #fff; border-color: #3b82f6; }
      |    table { width: 100%; border-collapse: collapse; font-size: .9rem; }
      |    th, td { text-align: left; padding: .4rem .5rem; border-bottom: 1px solid #8883;
      |             vertical-align: top; }
      |    th { position: sticky; top: 0; background: Canvas; }
      |    .status { font-size: .75rem; padding: .1rem .4rem; border-radius: .3rem; border: 1px solid #8886; }
      |    .muted { opacity: .6; font-size: .8rem; }
      |    .msg { margin: .5rem 0; min-height: 1.2rem; font-size: .85rem; }
      |    .err { color: #ef4444; }
      |    .ok { color: #22c55e; }
      |    a { color: #3b82f6; }
      |  </style>
      |</head>
      |<body>
      |  <h1>🎵 Music Library</h1>
      |  <div class="bar">
      |    <input id="q" placeholder="Search artist, title, url..." size="30" />
      |    <select id="status">
      |      <option value="">any status</option>
      |      <option value="draft">draft</option>
      |      <option value="final">final</option>
      |      <option value="deleted">deleted</option>
      |    </select>
      |    <button class="primary" onclick="search()">Search</button>
      |    <input id="addUrl" placeholder="Add track by URL..." size="28" />
      |    <button onclick="addTrack()">Add</button>
      |  </div>
      |  <div id="msg" class="msg"></div>
      |  <table>
      |    <thead>
      |      <tr><th>Title</th><th>Artist</th><th>Album</th><th>Status</th><th>Actions</th></tr>
      |    </thead>
      |    <tbody id="rows"></tbody>
      |  </table>
      |
      |  <script>
      |    const msg = (t, cls) => {
      |      const el = document.getElementById('msg');
      |      el.textContent = t; el.className = 'msg ' + (cls || '');
      |    };
      |    const esc = s => (s ?? '').toString()
      |      .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
      |
      |    async function search() {
      |      const q = document.getElementById('q').value.trim();
      |      const status = document.getElementById('status').value;
      |      const params = new URLSearchParams();
      |      if (q) params.set('q', q);
      |      if (status) params.set('status', status);
      |      msg('Searching...');
      |      try {
      |        const res = await fetch('/tracks?' + params.toString());
      |        const tracks = await res.json();
      |        render(tracks);
      |        msg(tracks.length + ' result(s)', 'ok');
      |      } catch (e) { msg('Search failed: ' + e, 'err'); }
      |    }
      |
      |    function render(tracks) {
      |      const rows = document.getElementById('rows');
      |      rows.innerHTML = '';
      |      for (const t of tracks) {
      |        const tr = document.createElement('tr');
      |        tr.innerHTML =
      |          '<td>' + esc(t.title) + '<div class="muted">' +
      |            (t.url ? '<a href="' + esc(t.url) + '" target="_blank" rel="noopener">source</a>' : '') +
      |          '</div></td>' +
      |          '<td>' + esc(t.artist) + '</td>' +
      |          '<td>' + esc(t.album) + '</td>' +
      |          '<td><span class="status">' + esc(t.status) + '</span></td>' +
      |          '<td><button onclick="doFetch(\'' + t.id + '\')">Fetch</button></td>';
      |        rows.appendChild(tr);
      |      }
      |    }
      |
      |    async function addTrack() {
      |      const url = document.getElementById('addUrl').value.trim();
      |      if (!url) { msg('Enter a URL first', 'err'); return; }
      |      msg('Adding...');
      |      try {
      |        const res = await fetch('/tracks', {
      |          method: 'POST',
      |          headers: { 'content-type': 'application/json' },
      |          body: JSON.stringify({ url }),
      |        });
      |        if (!res.ok) throw new Error(await res.text());
      |        document.getElementById('addUrl').value = '';
      |        msg('Added. Edit its metadata, set status=final, then Fetch.', 'ok');
      |        search();
      |      } catch (e) { msg('Add failed: ' + e, 'err'); }
      |    }
      |
      |    async function doFetch(id) {
      |      msg('Starting fetch...');
      |      try {
      |        const res = await fetch('/tracks/' + id + '/fetch', { method: 'POST' });
      |        const job = await res.json();
      |        msg('Fetch job ' + job.jobId + ' started (' + job.state + ')', 'ok');
      |        pollJob(job.jobId);
      |      } catch (e) { msg('Fetch failed: ' + e, 'err'); }
      |    }
      |
      |    async function pollJob(jobId) {
      |      try {
      |        const res = await fetch('/jobs/' + jobId);
      |        const job = await res.json();
      |        if (job.state === 'running') {
      |          setTimeout(() => pollJob(jobId), 2000);
      |        } else if (job.state === 'succeeded') {
      |          msg('Fetch ' + jobId + ' succeeded', 'ok');
      |        } else {
      |          msg('Fetch ' + jobId + ' failed: ' + (job.error || 'unknown'), 'err');
      |        }
      |      } catch (e) { /* stop polling on error */ }
      |    }
      |
      |    search();
      |  </script>
      |</body>
      |</html>
      |""".stripMargin
