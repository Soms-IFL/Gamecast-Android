/* IFL Gamecast — Material 3 web app for the IFL Gamecast public API (v1).
   No build step: plain ES2020, hash routing, EventSource for live plays. */
'use strict';

/* =====================================================================
   Config / persistence
   ===================================================================== */
const DEFAULT_BASE = 'https://iflgamecast.colebolebole.xyz';
const store = {
  get(k, d) { try { const v = localStorage.getItem('ifl.' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('ifl.' + k, JSON.stringify(v)); } catch (e) { /* private mode */ } },
  del(k) { try { localStorage.removeItem('ifl.' + k); } catch (e) { /* ignore */ } },
};
function defaultBase() {
  const q = new URLSearchParams(location.search).get('api');
  if (q === 'self') return location.origin;   // dev: the page's own server (dev/mock_server.py)
  if (q) return q.replace(/\/+$/, '');
  return DEFAULT_BASE;
}
const S = {
  base: store.get('base', null) || defaultBase(),
  season: null,            // null = current season, otherwise an archived season number
  currentSeason: null,     // number of the live season, learned from /status
  fav: store.get('fav', null),   // { id, hue }
  theme: store.get('theme', 'auto'),
  notify: store.get('notify', { on: false, mode: 'all' }),   // game-start alerts (Android app only)
};
const NATIVE = window.IFLNative || null;   // present only inside the Android app

/* =====================================================================
   Tiny helpers
   ===================================================================== */
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ic = (name, cls = '') => `<span class="ms ${cls}" aria-hidden="true">${name}</span>`;
const num = n => (n == null ? '–' : Number(n).toLocaleString());
const ORD = ['', '1st', '2nd', '3rd', '4th'];
const TIME = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });
const DAY = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const DAYLONG = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' });
const sameDay = (a, b) => a.toDateString() === b.toDateString();
function dayLabel(ms, long) {
  const d = new Date(ms), t = new Date(), n = new Date(t), p = new Date(t);
  n.setDate(t.getDate() + 1); p.setDate(t.getDate() - 1);
  if (sameDay(d, t)) return 'Today'; if (sameDay(d, n)) return 'Tomorrow'; if (sameDay(d, p)) return 'Yesterday';
  return (long ? DAYLONG : DAY).format(d);
}
function countdown(ms) {
  const s = Math.max(0, Math.round((ms - Date.now()) / 1000));
  if (s <= 0) return 'Starting…';
  const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), sec = s % 60;
  if (h >= 48) return Math.floor(h / 24) + ' days';
  if (h) return `${h}h ${String(m).padStart(2, '0')}m`;
  return `${m}:${String(sec).padStart(2, '0')}`;
}
const qLabel = q => (q >= 5 ? (q === 5 ? 'OT' : (q - 4) + 'OT') : 'Q' + q);
const pct = x => Math.round(x * 100);
const mmss = sec => `${Math.floor((sec || 0) / 60)}:${String((sec || 0) % 60).padStart(2, '0')}`;
const weekLabel = g => (g.playoff || g.week === 'P') ? (g.label || 'Playoffs') : 'Week ' + g.week;

/* =====================================================================
   API client
   ===================================================================== */
class ApiError extends Error { constructor(code, msg, path) { super(msg); this.code = code; this.path = path; } }
const NO_SEASON = /^(status|games\/live|seasons)/;
function apiUrl(path, params = {}) {
  const u = new URL(S.base.replace(/\/+$/, '') + '/api/v1/' + path);
  const p = Object.assign({}, params);
  if (S.season != null && !NO_SEASON.test(path) && p.season == null) p.season = S.season;
  for (const k in p) if (p[k] != null && p[k] !== '') u.searchParams.set(k, p[k]);
  return u.toString();
}
const memo = new Map();
async function api(path, params, ttl = 0) {
  const url = apiUrl(path, params);
  const hit = memo.get(url);
  if (ttl && hit && Date.now() - hit.t < ttl) return hit.v;
  let r;
  try { r = await fetch(url, { headers: { Accept: 'application/json' } }); }
  catch (e) { throw new ApiError(0, 'Could not reach the server.', path); }
  let body = null;
  try { body = await r.json(); } catch (e) { /* non-JSON */ }
  if (!r.ok) {
    if (r.status === 429) throw new ApiError(429, 'Too many requests — slow down for a moment.', path);
    throw new ApiError(r.status, (body && body.error) || r.statusText || 'Request failed', path);
  }
  if (ttl) memo.set(url, { t: Date.now(), v: body });
  return body;
}
async function allGames(params = {}) {
  const first = await api('games', Object.assign({ limit: 200 }, params));
  let games = first.games;
  for (let off = 200; off < first.total; off += 200) games = games.concat((await api('games', Object.assign({ limit: 200, offset: off }, params))).games);
  return { season: first.season, games };
}

/* =====================================================================
   Theming — one hue drives the whole M3 scheme (see styles.css)
   ===================================================================== */
function hexToHue(hex) {
  const c = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(v => (v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4));
  const l = Math.cbrt(.4122214708 * c[0] + .5363325363 * c[1] + .0514459929 * c[2]);
  const m = Math.cbrt(.2119034982 * c[0] + .6806995451 * c[1] + .1073969566 * c[2]);
  const s = Math.cbrt(.0883024619 * c[0] + .2817188376 * c[1] + .6299787005 * c[2]);
  const a = 1.9779984951 * l - 2.428592205 * m + .4505937099 * s;
  const b = .0259040371 * l + .7827717662 * m - .808675766 * s;
  return (Math.atan2(b, a) * 180 / Math.PI + 360) % 360;
}
function applyTheme() {
  const root = document.documentElement;
  if (S.theme === 'light' || S.theme === 'dark') root.dataset.theme = S.theme; else delete root.dataset.theme;
  if (S.fav && S.fav.hue != null) root.style.setProperty('--h', S.fav.hue); else root.style.removeProperty('--h');
  const dark = S.theme === 'dark' || (S.theme === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  const m = $('#theme-color'); if (m) m.content = dark ? '#16161d' : '#f6f5fb';
}
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', applyTheme);

/* =====================================================================
   Shared UI pieces
   ===================================================================== */
function badge(t, size = 40) {
  if (!t) return '';
  const [c1, c2] = t.colors || [];
  const logo = t.logo ? S.base.replace(/\/+$/, '') + t.logo : '';
  return `<span class="badge-t s${size}" style="--tc:${esc(c1 || '#6b6b78')};--tc2:${esc(c2 || 'transparent')}">${esc(t.abbr || t.id)}${logo ? `<img src="${esc(logo)}" alt="" loading="lazy" onerror="this.remove()">` : ''}</span>`;
}
const isFav = id => S.fav && S.fav.id === id;
const star = id => (isFav(id) ? ic('star', 'fill sm') : '');
const tag = (text, cls = '') => `<span class="tag ${cls}">${esc(text)}</span>`;
const liveDot = text => `<span class="conn live"><span class="dot"></span>${esc(text || 'LIVE')}</span>`;

function skeleton(kind) {
  const b = (h, w = '100%') => `<div class="skeleton" style="height:${h}px;width:${w}"></div>`;
  if (kind === 'list') return `<div class="stack">${b(120)}${b(72)}${b(72)}${b(72)}</div>`;
  return `<div class="stack loose">${b(220)}${b(80)}<div class="stack">${b(72)}${b(72)}</div></div>`;
}
function emptyState(icon, title, sub) {
  return `<div class="center empty">${ic(icon)}<div class="title-m">${esc(title)}</div>${sub ? `<div class="body-m">${esc(sub)}</div>` : ''}</div>`;
}
function errorState(e) {
  let title = 'Something went wrong', body = e && e.message || '', extra = '';
  if (e instanceof ApiError) {
    if (e.code === 0) { title = 'Can’t reach the server'; body = `The app couldn’t connect to ${S.base}. Check your connection or the server address in settings.`; }
    else if (e.code === 404 && /^status/.test(e.path || '')) {
      title = 'This server has no /api/v1 yet';
      body = `${S.base} answered, but it doesn’t serve the public API. It needs the latest Gamecast version (the one that includes api.js) — update it and restart the server.`;
    } else if (e.code === 429) title = 'Slow down a little';
  }
  return `<div class="center"><span class="ms" style="font-size:48px;color:var(--error)">cloud_off</span><div class="title-m">${esc(title)}</div>
    <div class="body-m muted" style="max-width:440px">${esc(body)}</div>${extra}
    <div class="row"><button class="btn tonal sl" data-act="retry">${ic('refresh')}Try again</button><button class="btn text sl" data-act="settings">Settings</button></div></div>`;
}
function snack(text, action) {
  const old = $('.snack'); if (old) old.remove();
  const el = document.createElement('div'); el.className = 'snack'; el.setAttribute('role', 'status');
  el.innerHTML = `<span>${esc(text)}</span>${action ? `<button class="sl">${esc(action.label)}</button>` : ''}`;
  document.body.appendChild(el);
  if (action) $('button', el).onclick = () => { action.run(); el.remove(); };
  setTimeout(() => el.remove(), 4500);
}

/* compact game row used in lists */
function gameRow(g) {
  const fin = g.status === 'final', live = g.status === 'live';
  const line = t => {
    const won = fin && g.winner === t.id, lost = fin && g.winner && g.winner !== t.id;
    return `<div class="tr ${won ? 'won' : ''} ${lost ? 'lost' : ''}">${badge(t, 32)}<span class="nm ellipsis">${esc(t.name)} ${star(t.id)}</span><span class="sc">${t.score == null ? '' : t.score}</span></div>`;
  };
  let meta;
  if (live) meta = `${liveDot()}<span class="label-s muted">${esc(weekLabel(g))}</span>`;
  else if (fin) meta = `<span class="label-m">FINAL${g.overtime ? ' / OT' : ''}</span><span class="label-s muted">${esc(weekLabel(g))}</span>`;
  else meta = `<span class="label-l">${TIME.format(g.startsAtMs)}</span><span class="label-s muted">${esc(dayLabel(g.startsAtMs))}</span>`;
  return `<button class="game sl" data-go="#/game/${esc(g.id)}"><div class="teams">${line(g.away)}${line(g.home)}</div><div class="meta">${meta}</div></button>`;
}
const gameList = games => `<div class="card flush"><div class="list">${games.map(gameRow).join('')}</div></div>`;

/* =====================================================================
   Router / view lifecycle
   ===================================================================== */
const NAV = [
  { id: 'home', icon: 'home', label: 'Home', hash: '#/' },
  { id: 'games', icon: 'sports_football', label: 'Games', hash: '#/games' },
  { id: 'standings', icon: 'leaderboard', label: 'Standings', hash: '#/standings' },
  { id: 'teams', icon: 'groups', label: 'Teams', hash: '#/teams' },
  { id: 'leaders', icon: 'military_tech', label: 'Leaders', hash: '#/leaders' },
];
let navToken = 0, cur = null;
const ui = { gamesWeek: null, standTab: 'div', teamTab: 'roster', gameTab: null, playFilter: 'all', boxSide: 'away', leadCat: 'overview' };

function drawNav(active) {
  $('#nav').innerHTML = `<div class="nav-rail-top"></div>` + NAV.map(n => `<a class="nav-item sl" href="${n.hash}" aria-label="${n.label}" ${active === n.id ? 'aria-current="page"' : ''} data-nav="${n.id}">
    <span class="pill">${ic(n.icon)}</span><span class="nlabel">${n.label}</span></a>`).join('');
  $$('.nav-item').forEach(a => a.classList.toggle('active', a.dataset.nav === active));
}
function setBar(title, back) {
  const t = $('#top-bar .title');
  t.innerHTML = (back
    ? `<button class="icon-btn sl" data-act="back" aria-label="Back" style="margin-left:-12px">${ic('arrow_back')}</button>`
    : `<img class="logo-img" src="logo.png" alt="IFL">`) + `<h1 class="page-title ellipsis">${esc(title)}</h1>`;
  document.title = (title && title !== 'Gamecast' ? title + ' · ' : '') + 'IFL Gamecast';
  const sb = $('#btn-season');
  sb.classList.toggle('tonal', S.season != null);
  sb.setAttribute('aria-label', S.season != null ? `Season ${S.season} (archived). Choose season` : 'Choose season');
}
function setConn(kind, text) {
  const el = $('#conn');
  if (!kind) { el.hidden = true; return; }
  el.hidden = false; el.className = 'conn ' + kind;
  el.innerHTML = `<span class="dot"></span>${esc(text)}`;
}

function parseHash() {
  const parts = (location.hash.replace(/^#\/?/, '') || '').split('?')[0].split('/').filter(Boolean).map(decodeURIComponent);
  return { name: parts[0] || 'home', args: parts.slice(1) };
}
const VIEWS = {};
async function route() {
  if (cur) { cur.cleanups.forEach(fn => { try { fn(); } catch (e) { /* ignore */ } }); }
  const { name, args } = parseHash();
  const token = ++navToken;
  const view = VIEWS[name] || VIEWS.home;
  const navId = { home: 'home', games: 'games', game: 'games', watch: 'games', standings: 'standings', teams: 'teams', team: 'teams', leaders: 'leaders' }[name] || 'home';
  const main = $('#view');
  const ctx = cur = {
    name, args, token, cleanups: [], actions: {},
    alive: () => token === navToken,
    mount(html) { if (!ctx.alive()) return; main.innerHTML = `<div class="view">${html}</div>`; },
    patch(sel, html) { if (!ctx.alive()) return; const el = $(sel, main); if (el) el.innerHTML = html; },
    $: sel => $(sel, main),
    onCleanup(fn) { ctx.cleanups.push(fn); },
    every(ms, fn) { const id = setInterval(fn, ms); ctx.cleanups.push(() => clearInterval(id)); },
    setTitle(t, back) { if (ctx.alive()) setBar(t, back); },
  };
  drawNav(navId);
  const detail = name === 'game' || name === 'team' || name === 'watch';
  setBar({ home: 'Gamecast', games: 'Games', standings: 'Standings', teams: 'Teams', leaders: 'Leaders' }[name] || 'IFL Gamecast', detail);
  setConn(null);
  window.scrollTo(0, 0);
  main.innerHTML = `<div class="view">${skeleton(name === 'home' ? 'home' : 'list')}</div>`;
  try { await view(ctx); }
  catch (e) { if (ctx.alive()) { console.error(e); ctx.mount(errorState(e)); setConn('off', 'Offline'); } }
}
window.addEventListener('hashchange', route);

/* ---------- live stream helper: EventSource with polling fallback ---------- */
function openStream(ctx, path, h, poll) {
  let es = null, pollId = null, closed = false;
  const startPoll = () => { if (pollId || closed) return; h.polling && h.polling(); pollId = setInterval(() => poll && poll(), 5000); poll && poll(); };
  try {
    es = new EventSource(apiUrl(path));
    for (const n of ['state', 'play', 'score', 'idle', 'final', 'end']) es.addEventListener(n, ev => { let d = null; try { d = JSON.parse(ev.data); } catch (e) { /* ignore */ } if (h[n] && !closed) h[n](d); });
    es.onopen = () => { h.open && h.open(); };
    es.onerror = () => { if (es.readyState === 2) startPoll(); else h.drop && h.drop(); };
  } catch (e) { startPoll(); }
  const stop = () => { closed = true; if (es) es.close(); if (pollId) clearInterval(pollId); };
  ctx.onCleanup(stop);
  return stop;
}
const throttle = (fn, ms) => { let last = 0, t = null; return () => { const now = Date.now(); clearTimeout(t); if (now - last >= ms) { last = now; fn(); } else t = setTimeout(() => { last = Date.now(); fn(); }, ms - (now - last)); }; };

/* =====================================================================
   Play-by-play rendering (shared by home + game page)
   ===================================================================== */
const SHOW_TAGS = { TO: 'to', INJ: 'inj', ONSIDE: '', '2PT': 'score', MISS: '' };
function playHtml(p, opts = {}) {
  const scoring = !!p.scoringTeam;
  const labels = [];
  if (p.banner) labels.push([p.banner, (p.tags || []).some(t => ['TD', 'FG'].includes(t)) ? 'score' : ((p.tags || []).includes('TO') || (p.tags || []).includes('FLAG') ? 'to' : '')]);
  for (const t of p.tags || []) if (SHOW_TAGS[t] != null && !labels.some(l => l[0] === t)) labels.push([t === 'TO' ? 'TURNOVER' : t === 'INJ' ? 'INJURY' : t, SHOW_TAGS[t]]);
  return `<div class="play ${scoring ? 'scoring' : ''}" data-n="${p.n}">
    <div class="clk">${qLabel(p.quarter)}<br>${esc(p.clock)}</div>
    <div>${p.situation ? `<div class="sit">${esc(p.situation)}</div>` : ''}<div class="tx">${esc(p.text)}</div>
    ${labels.length || scoring ? `<div class="tags">${labels.map(l => tag(l[0], l[1])).join('')}${scoring && opts.game ? tag(`${opts.game.away.abbr} ${p.score.away} – ${p.score.home} ${opts.game.home.abbr}`, 'score') : ''}</div>` : ''}</div></div>`;
}
function playsByQuarter(plays, game, newestFirst) {
  if (!plays.length) return emptyState('sports_football', 'No plays yet', 'Plays show up here as they finish airing.');
  const groups = [];
  for (const p of plays) { let g = groups[groups.length - 1]; if (!g || g.q !== p.quarter) groups.push(g = { q: p.quarter, items: [] }); g.items.push(p); }
  if (newestFirst) { groups.reverse(); groups.forEach(g => g.items.reverse()); }
  return `<div class="plays">${groups.map(g => `<div class="qhead">${g.q >= 5 ? (g.q === 5 ? 'Overtime' : (g.q - 4) + 'OT') : 'Quarter ' + g.q}</div>${g.items.map(p => playHtml(p, { game })).join('')}`).join('')}</div>`;
}

/* =====================================================================
   HOME
   ===================================================================== */
function situationText(s) {
  if (!s) return '';
  if (s.down >= 1 && s.down <= 4) return `${ORD[s.down]} & ${s.toGo}`;
  return s.phase ? s.phase.replace(/^\w/, c => c.toUpperCase()) : '';
}
function liveHero(det) {
  const g = det.game, s = det.situation || {}, wp = det.winProbability;
  const side = t => `<div class="side">${badge(t, 72)}<div class="nm">${esc(t.nickname || t.name)}</div>
      <div class="score tnum" data-score="${t.id}">${t.score == null ? 0 : t.score}</div>
      <div class="poss-dot">${s.possession === t.id ? ic('sports_football', 'fill sm') : ''}</div></div>`;
  const h = wp ? wp.home : .5;
  return `<section class="hero live" data-go="#/game/${esc(g.id)}" style="cursor:pointer">
    <div class="hero-top">${liveDot()}<span class="label-m muted">${esc(weekLabel(g))}${g.stage === 'bowl' ? ' · IFL Bowl' : ''}</span></div>
    <div class="board">${side(g.away)}
      <div class="mid"><div class="title-l">${s.quarter ? qLabel(s.quarter) : ''}</div><div class="headline-s tnum">${esc(s.clock || '')}</div><div class="label-l muted">${esc(situationText(s))}</div></div>
      ${side(g.home)}</div>
    ${wp ? `<div class="stack tight" style="margin-top:12px"><div class="winprob" role="img" aria-label="Win probability">
      <span style="width:${pct(1 - h)}%;background:${esc((g.away.colors || [])[0] || 'var(--primary)')}"></span><span style="width:${pct(h)}%;background:${esc((g.home.colors || [])[0] || 'var(--tertiary)')}"></span></div>
      <div class="row between label-m muted"><span>${esc(g.away.abbr)} ${pct(1 - h)}%</span><span>Win probability</span><span>${pct(h)}% ${esc(g.home.abbr)}</span></div></div>` : ''}
    <div class="watch-cta"><button class="btn filled sl" data-go="#/watch/${esc(g.id)}">${ic('live_tv')}Watch live</button></div>
    </section>`;
}
function liveFeed(det) {
  const plays = (det.recentPlays || []).slice(-5);
  if (!plays.length) return '';
  return `<div class="section-h"><h2>Latest plays</h2><button class="btn text sl" data-go="#/game/${esc(det.game.id)}">Full play-by-play</button></div>
    <div class="card"><div class="plays">${plays.slice().reverse().map(p => playHtml(p, { game: det.game })).join('')}</div></div>`;
}
function nextHero(next, status) {
  if (!next) return '';
  const g = next;
  const side = t => `<div class="side">${badge(t, 72)}<div class="nm">${esc(t.nickname || t.name)}</div><div class="rec">${esc(t.city || '')}</div></div>`;
  return `<section class="hero" data-go="#/game/${esc(g.id)}" style="cursor:pointer">
    <div class="hero-top"><span class="tag">${ic('schedule')}UP NEXT</span><span class="label-m muted">${esc(weekLabel(g))}${g.stage === 'bowl' ? ' · IFL Bowl' : ''}</span></div>
    <div class="board">${side(g.away)}
      <div class="mid"><div class="label-m muted">${esc(dayLabel(g.startsAtMs))}</div><div class="headline-s">${TIME.format(g.startsAtMs)}</div>
      ${status && status.paused ? '' : `<div class="label-l" style="color:var(--primary)" data-countdown="${g.startsAtMs}">${countdown(g.startsAtMs)}</div>`}</div>
      ${side(g.home)}</div>
    ${g.venue && g.venue.name ? `<div class="row label-m muted" style="justify-content:center;margin-top:12px">${ic('stadium', 'sm')}${esc(g.venue.name)}${g.venue.town ? ' · ' + esc(g.venue.town) : ''}</div>` : ''}</section>`;
}
function seasonCard(st) {
  const s = st.season; if (!s) return '';
  const p = s.gamesTotal ? s.gamesPlayed / s.gamesTotal : 0;
  const bits = [];
  if (s.done && s.champion) bits.push('Season complete');
  else if (st.estimatedSeasonEnd) bits.push('Est. finish ' + dayLabel(Date.parse(st.estimatedSeasonEnd)) + ' ' + TIME.format(Date.parse(st.estimatedSeasonEnd)));
  if (st.nextSeasonStartsAt) bits.push(`Next season ${dayLabel(Date.parse(st.nextSeasonStartsAt))} ${TIME.format(Date.parse(st.nextSeasonStartsAt))}`);
  return `<div class="card filled stack tight"><div class="row between"><div class="title-m">Season ${s.number}</div><div class="label-l muted tnum">${s.gamesPlayed} / ${s.gamesTotal} games</div></div>
    <div class="linear" role="progressbar" aria-valuenow="${pct(p)}" aria-valuemin="0" aria-valuemax="100"><i style="width:${pct(p)}%"></i></div>
    <div class="row between wrap label-m muted"><span>${s.playoffsStarted ? 'Playoffs under way' : 'Regular season'}</span><span>${esc(bits.join(' · '))}</span></div></div>`;
}
function championCard(team, season, sub) {
  if (!team) return '';
  return `<button class="card tonal sl row" data-go="#/team/${esc(team.id)}" style="gap:16px">${badge(team, 56)}<div class="grow"><div class="label-m" style="opacity:.8">${ic('emoji_events', 'sm')} SEASON ${season} CHAMPIONS</div>
    <div class="title-l">${esc(team.name)}</div>${sub ? `<div class="body-m" style="opacity:.8">${esc(sub)}</div>` : ''}</div>${ic('chevron_right')}</button>`;
}

VIEWS.home = async function (ctx) {
  if (S.season != null) return homeArchive(ctx);
  const status = await api('status');
  S.currentSeason = status.season && status.season.number;
  let det = status.live ? await api('games/live', { plays: 8 }) : { live: false };
  let next = status.next;
  const finals = await recentFinals(4).catch(() => []);
  const upcoming = (await api('games', { status: 'scheduled', limit: 5 }).catch(() => ({ games: [] }))).games;

  let oddsData = null;
  const draw = () => {
    const parts = [];
    if (status.paused) parts.push(`<div class="banner warn">${ic('pause_circle')}<div><div class="title-s">Schedule paused</div><div class="body-s">Games will resume when the league starts the clock again.</div></div></div>`);
    if (det.live) parts.push(`<div id="live-hero">${liveHero(det)}</div><div id="live-feed" class="stack tight">${liveFeed(det)}</div>`);
    else if (status.season && status.season.done && status.season.champion) parts.push(championCard(teamRefOf(status.season.champion), status.season.number));
    else if (next) parts.push(nextHero(next, status));
    else parts.push(`<div class="card filled">${emptyState('event_busy', 'No game on air', status.nextSeasonStartsAt ? 'The next season starts ' + dayLabel(Date.parse(status.nextSeasonStartsAt)) + ' at ' + TIME.format(Date.parse(status.nextSeasonStartsAt)) + '.' : 'Nothing scheduled right now.')}</div>`);
    if (!(status.season && status.season.done)) parts.push(`<div id="odds-card">${favOddsHtml(oddsData)}</div>`);
    parts.push(seasonCard(status));
    const lists = [];
    const up = upcoming.filter(g => !(next && g.id === next.id && !det.live)).slice(0, 4);
    if (up.length) lists.push(`<div class="stack tight"><div class="section-h"><h2>Coming up</h2><button class="btn text sl" data-go="#/games">All games</button></div>${gameList(up)}</div>`);
    if (finals.length) lists.push(`<div class="stack tight"><div class="section-h"><h2>Latest results</h2><button class="btn text sl" data-go="#/games">All games</button></div>${gameList(finals)}</div>`);
    if (lists.length) parts.push(`<div class="grid cols-2" style="align-items:start">${lists.join('')}</div>`);
    ctx.mount(`<div class="stack loose">${parts.join('')}</div>`);
    if (det.live) setConn('live', 'LIVE'); else setConn('on', 'Connected');
  };
  draw();
  if (S.fav && !(status.season && status.season.done)) {
    getOdds().then(o => { oddsData = o; ctx.patch('#odds-card', favOddsHtml(o)); }).catch(() => ctx.patch('#odds-card', ''));
  }

  // Re-run the whole view. Stop this view's streams/timers first, or every reload would stack another stream.
  const reload = () => { if (!ctx.alive()) return; ctx.cleanups.splice(0).forEach(fn => { try { fn(); } catch (e) { /* ignore */ } }); VIEWS.home(ctx).catch(() => {}); };
  const refresh = async () => {
    try { const d = await api('games/live', { plays: 8 }); if (!d.live) { if (det.live) reload(); return; } if (!det.live) return reload(); det = d; ctx.patch('#live-hero', liveHero(det)); ctx.patch('#live-feed', liveFeed(det)); } catch (e) { /* keep last */ }
  };
  const softRefresh = throttle(refresh, 3000);
  openStream(ctx, 'games/live/stream', {
    open() { if (!det.live) setConn('on', 'Connected'); },
    play(p) {
      if (!det.live || !det.game) return reload();
      det.recentPlays = (det.recentPlays || []).filter(x => x.n !== p.n).concat(p).slice(-8);
      det.game.home.score = p.score.home; det.game.away.score = p.score.away;
      ctx.patch('#live-feed', liveFeed(det)); ctx.patch('#live-hero', liveHero(det));
      softRefresh();
    },
    state() { softRefresh(); },   // sent on every (re)connect — must not reload the view
    final() { setTimeout(reload, 1500); },
    idle() { if (det.live) reload(); },
    end() { setTimeout(reload, 1000); },
  }, () => (det.live ? refresh() : api('status').then(s => { if (s.live) reload(); }).catch(() => {})));
  ctx.every(30000, async () => { try { const s = await api('status'); if (!!s.live !== !!det.live || (s.next && next && s.next.id !== next.id)) reload(); } catch (e) { /* ignore */ } });
};
const teamRefOf = t => t;   // /status returns full team refs already
async function recentFinals(n) {
  const first = await api('games', { status: 'final', limit: 1 });
  if (!first.total) return [];
  const off = Math.max(0, first.total - n);
  return (await api('games', { status: 'final', limit: n, offset: off })).games.reverse();
}
async function homeArchive(ctx) {
  const d = await api('seasons/' + S.season);
  const bowl = d.iflBowl;
  const std = d.standings || [];
  ctx.mount(`<div class="stack loose">
    <div class="banner info">${ic('history')}<div class="grow"><div class="title-s">Viewing Season ${d.season}</div><div class="body-s">You’re looking at an archived season. Switch back any time.</div></div>
      <button class="btn text sl" data-act="season-current">Current</button></div>
    ${d.champion ? championCard(d.champion, d.season) : ''}
    ${bowl ? `<div class="stack tight"><div class="section-h"><h2>IFL Bowl</h2><button class="btn text sl" data-go="#/watch/${esc(bowl.id)}">${ic('play_circle')}Watch replay</button></div>${gameList([bowl])}</div>` : ''}
    <div class="stack tight"><div class="section-h"><h2>Final standings</h2><button class="btn text sl" data-go="#/standings">Full table</button></div>
      <div class="grid cols-2">${std.map(dv => standTable(dv.name, dv.teams, true)).join('')}</div></div>
  </div>`);
}

/* =====================================================================
   GAMES (schedule by week)
   ===================================================================== */
VIEWS.games = async function (ctx) {
  const { games } = await allGames();
  if (!games.length) return ctx.mount(emptyState('event', 'No games yet', 'The schedule appears once a season is created.'));
  const weeks = new Map();
  for (const g of games) { const k = String(g.week); if (!weeks.has(k)) weeks.set(k, []); weeks.get(k).push(g); }
  const keys = [...weeks.keys()].sort((a, b) => (a === 'P') - (b === 'P') || (+a - +b));
  const pick = () => {
    const f = games.find(g => g.status === 'live') || games.find(g => g.status === 'scheduled');
    return f ? String(f.week) : keys[keys.length - 1];
  };
  if (!ui.gamesWeek || !weeks.has(ui.gamesWeek)) ui.gamesWeek = pick();
  let onlyFav = false;
  const draw = () => {
    let list = weeks.get(ui.gamesWeek) || [];
    if (onlyFav && S.fav) list = list.filter(g => g.home.id === S.fav.id || g.away.id === S.fav.id);
    const days = [];
    for (const g of list) { const d = g.startsAtMs == null ? 'TBD' : new Date(g.startsAtMs).toDateString(); let x = days.find(y => y.d === d); if (!x) days.push(x = { d, ms: g.startsAtMs, items: [] }); x.items.push(g); }
    ctx.mount(`<div class="stack">
      <div class="chips" id="week-chips" role="tablist">${keys.map(k => `<button class="chip sl ${k === ui.gamesWeek ? 'selected' : ''}" role="tab" aria-selected="${k === ui.gamesWeek}" data-act="week" data-v="${k}">${k === ui.gamesWeek ? ic('check') : ''}${k === 'P' ? 'Playoffs' : 'Week ' + k}</button>`).join('')}
        ${S.fav ? `<button class="chip sl ${onlyFav ? 'selected' : ''}" data-act="favonly">${onlyFav ? ic('check') : ic('star')}My team</button>` : ''}</div>
      ${days.length ? days.map(d => `<div class="stack tight"><div class="label-m muted" style="padding:8px 4px 0">${d.ms == null ? 'Time to be announced' : esc(dayLabel(d.ms, true))}</div>${gameList(d.items)}</div>`).join('') : emptyState('filter_alt_off', 'No games match')}
    </div>`);
    const sel = $('#week-chips .selected'); if (sel) sel.scrollIntoView({ inline: 'center', block: 'nearest' });
  };
  ctx.actions.week = el => { ui.gamesWeek = el.dataset.v; draw(); };
  ctx.actions.favonly = () => { onlyFav = !onlyFav; draw(); };
  draw();
  ctx.every(20000, async () => { // keep live / just-finished scores fresh
    if (!(weeks.get(ui.gamesWeek) || []).some(g => g.status !== 'final')) return;
    try { const r = await allGames(); r.games.forEach((g, i) => { games[i] = g; }); weeks.clear(); for (const g of games) { const k = String(g.week); if (!weeks.has(k)) weeks.set(k, []); weeks.get(k).push(g); } const y = scrollY; draw(); scrollTo(0, y); } catch (e) { /* ignore */ }
  });
};

/* =====================================================================
   GAME DETAIL
   ===================================================================== */
const STAT_ROWS = [
  ['Total yards', t => t.yds], ['Passing yards', t => t.py], ['Rushing yards', t => t.ry], ['First downs', t => t.fd], ['Plays', t => t.plays],
  ['Turnovers', t => t.to, true], ['Penalties', t => `${t.pen || 0}–${t.peny || 0}`, false, t => t.pen || 0], ['Time of possession', t => mmss(t.top), false, t => t.top],
  ['3rd down', t => `${t.d3c || 0}/${t.d3a || 0}`, false, t => (t.d3a ? t.d3c / t.d3a : 0)], ['4th down', t => `${t.d4c || 0}/${t.d4a || 0}`, false, t => (t.d4a ? t.d4c / t.d4a : 0)],
  ['Sacks taken', t => `${t.sk || 0}–${t.sky || 0}`, true, t => t.sk || 0],
];
const PLAYER_GROUPS = [
  ['Passing', s => s.pa, [['C/ATT', s => `${s.pc || 0}/${s.pa || 0}`], ['YDS', 'py'], ['TD', 'ptd'], ['INT', 'pint']], 'py'],
  ['Rushing', s => s.ra, [['CAR', 'ra'], ['YDS', 'ry'], ['LNG', 'rlng'], ['TD', 'rtd']], 'ry'],
  ['Receiving', s => s.rec || s.tgt, [['REC', 'rec'], ['TGT', 'tgt'], ['YDS', 'recy'], ['TD', 'rectd']], 'recy'],
  ['Defense', s => s.tk || s.sacks || s.ints || s.pd || s.ff || s.fr, [['TK', 'tk'], ['SCK', 'sacks'], ['INT', 'ints'], ['PD', 'pd']], 'tk'],
  ['Kicking', s => s.fga || s.xpa, [['FG', s => `${s.fgm || 0}/${s.fga || 0}`], ['LNG', 'fglng'], ['XP', s => `${s.xpm || 0}/${s.xpa || 0}`]], 'fgm'],
  ['Punting', s => s.pn, [['PUNTS', 'pn'], ['YDS', 'pny']], 'pny'],
  ['Returns', s => s.kr || s.pr, [['KR', 'kr'], ['KR YDS', 'kry'], ['PR', 'pr'], ['PR YDS', 'pry']], 'kry'],
];
function lineScore(d) {
  const g = d.game, q = d.quarters; if (!q) return '';
  const n = q.home.length, cols = Array.from({ length: n }, (_, i) => (i < 4 ? i + 1 : (i === 4 ? 'OT' : (i - 3) + 'OT')));
  const row = (t, arr, tot) => `<tr><td><div class="tm">${badge(t, 24)}${esc(t.abbr)}</div></td>${arr.map(v => `<td>${v}</td>`).join('')}<td><b>${tot == null ? arr.reduce((a, b) => a + b, 0) : tot}</b></td></tr>`;
  return `<div class="card flush tbl-wrap"><table class="t"><thead><tr><th></th>${cols.map(c => `<th>${c}</th>`).join('')}<th>T</th></tr></thead>
    <tbody>${row(g.away, q.away, g.away.score)}${row(g.home, q.home, g.home.score)}</tbody></table></div>`;
}
function gameBoard(d) {
  const g = d.game, s = d.situation || {}, live = g.status === 'live', fin = g.status === 'final';
  const side = t => {
    const won = fin && g.winner === t.id, lost = fin && g.winner && g.winner !== t.id;
    return `<div class="side"><button class="sl" data-go="#/team/${esc(t.id)}" style="border-radius:50%" aria-label="${esc(t.name)}">${badge(t, 72)}</button>
      <div class="nm">${esc(t.nickname || t.name)} ${star(t.id)}</div>
      ${g.status === 'scheduled' ? '<div class="score" style="color:var(--outline)">–</div>' : `<div class="score tnum ${won ? 'win' : ''} ${lost ? 'lose' : ''}">${t.score == null ? 0 : t.score}</div>`}
      <div class="poss-dot">${live && s.possession === t.id ? ic('sports_football', 'fill sm') : ''}</div></div>`;
  };
  let mid;
  if (live) mid = `<div class="title-l">${s.quarter ? qLabel(s.quarter) : ''}</div><div class="headline-s tnum">${esc(s.clock || '')}</div><div class="label-l muted">${esc(situationText(s))}</div>`;
  else if (fin) mid = `<div class="title-m">Final${g.overtime ? ' / OT' : ''}</div><div class="label-m muted">${esc(dayLabel(g.startsAtMs))}</div>`;
  else mid = `<div class="label-m muted">${esc(dayLabel(g.startsAtMs))}</div><div class="headline-s">${TIME.format(g.startsAtMs)}</div><div class="label-l" style="color:var(--primary)" data-countdown="${g.startsAtMs}">${countdown(g.startsAtMs)}</div>`;
  const wp = d.winProbability, h = wp ? wp.home : null;
  return `<section class="hero ${live ? 'live' : ''}"><div class="hero-top">${live ? liveDot() : `<span class="tag ${fin ? 'ok' : ''}">${fin ? 'FINAL' : 'SCHEDULED'}</span>`}
      <span class="label-m muted">${esc(weekLabel(g))}${g.stage === 'bowl' ? ' · IFL Bowl' : ''}</span></div>
    <div class="board">${side(g.away)}<div class="mid">${mid}</div>${side(g.home)}</div>
    ${wp ? `<div class="stack tight" style="margin-top:12px"><div class="winprob"><span style="width:${pct(1 - h)}%;background:${esc((g.away.colors || [])[0] || 'var(--primary)')}"></span><span style="width:${pct(h)}%;background:${esc((g.home.colors || [])[0] || 'var(--tertiary)')}"></span></div>
      <div class="row between label-m muted"><span>${esc(g.away.abbr)} ${pct(1 - h)}%</span><span>${wp.basis === 'pregame' ? 'Pregame win probability' : 'Win probability'}</span><span>${pct(h)}% ${esc(g.home.abbr)}</span></div></div>` : ''}
    ${(g.venue && g.venue.name) || (d.venue && d.venue.name) ? `<div class="row label-m muted" style="justify-content:center;margin-top:12px">${ic('stadium', 'sm')}${esc((d.venue || g.venue).name)}${(d.venue || g.venue).town ? ' · ' + esc((d.venue || g.venue).town) : ''}</div>` : ''}
    ${g.status === 'scheduled' ? '' : `<div class="watch-cta"><button class="btn filled sl" data-go="#/watch/${esc(g.id)}">${ic(live ? 'live_tv' : 'play_circle')}${live ? 'Watch live' : 'Watch replay'}</button></div>`}</section>`;
}
function inactiveCard(d) {
  const a = d.inactive; if (!a || (!a.home.length && !a.away.length)) return '';
  const col = (t, arr) => `<div><div class="row" style="margin-bottom:8px">${badge(t, 24)}<span class="title-s">${esc(t.abbr)}</span></div>${arr.length ? arr.map(p => `<button class="row sl" data-act="player" data-id="${esc(p.id)}" style="width:100%;padding:6px 0;border-radius:8px;text-align:left"><span class="tag inj">OUT</span><span class="body-m">${esc(p.name)}</span><span class="label-m muted">${esc(p.position)}</span></button>`).join('') : '<div class="body-m muted">No one out</div>'}</div>`;
  return `<div class="stack tight"><div class="section-h"><h2>Inactive players</h2></div><div class="card grid cols-2">${col(d.game.away, a.away)}${col(d.game.home, a.home)}</div></div>`;
}
function statCompare(d) {
  const b = d.box; if (!b || !b.home.stats || !b.away.stats) return '';
  const A = b.away.stats, H = b.home.stats, g = d.game;
  const rows = STAT_ROWS.map(([label, fn, lowerBetter, numFn]) => {
    const av = fn(A), hv = fn(H), an = +(numFn ? numFn(A) : fn(A)) || 0, hn = +(numFn ? numFn(H) : fn(H)) || 0;
    const tot = an + hn || 1;
    const aLead = lowerBetter ? an < hn : an > hn, hLead = lowerBetter ? hn < an : hn > an;
    return `<div class="v ${aLead ? '' : 'muted'}" style="${aLead ? 'font-weight:700' : ''}">${esc(av)}</div><div class="lbl">${esc(label)}</div><div class="v r ${hLead ? '' : 'muted'}" style="${hLead ? 'font-weight:700' : ''}">${esc(hv)}</div>
      <div class="bar"><i style="width:${an / tot * 100}%;background:${esc((g.away.colors || [])[0] || 'var(--primary)')};opacity:${aLead ? 1 : .45}"></i><i class="h" style="width:${hn / tot * 100}%;background:${esc((g.home.colors || [])[0] || 'var(--tertiary)')};opacity:${hLead ? 1 : .45}"></i></div>`;
  }).join('');
  return `<div class="card stack"><div class="row between">${badge(g.away, 32)}<span class="title-m">Team stats</span>${badge(g.home, 32)}</div><div class="cmp">${rows}</div></div>`;
}
function playerTables(side) {
  const players = side.players || [];
  const out = PLAYER_GROUPS.map(([title, test, cols, sortKey]) => {
    const rows = players.filter(p => p.stats && test(p.stats)).sort((a, b) => (b.stats[sortKey] || 0) - (a.stats[sortKey] || 0));
    if (!rows.length) return '';
    return `<div class="stack tight"><div class="section-h"><h2>${title}</h2></div><div class="card flush tbl-wrap"><table class="t"><thead><tr><th class="l">Player</th>${cols.map(c => `<th>${c[0]}</th>`).join('')}</tr></thead><tbody>
      ${rows.map(p => `<tr data-act="player" data-id="${esc(p.id)}"><td><div class="tm"><div><div>${esc(p.short || p.name)}</div><div class="label-s muted" style="font-weight:400">#${p.number} · ${esc(p.position)}</div></div></div></td>${cols.map(c => `<td>${typeof c[1] === 'function' ? c[1](p.stats) : (p.stats[c[1]] || 0)}</td>`).join('')}</tr>`).join('')}</tbody></table></div></div>`;
  }).join('');
  return out || emptyState('table_chart', 'No player stats yet');
}

VIEWS.game = async function (ctx) {
  const id = ctx.args[0];
  let d = await api('games/' + encodeURIComponent(id));
  let plays = [], next = 0;
  const loadPlays = async () => {
    if (d.game.status === 'scheduled') return;
    const r = await api('games/' + encodeURIComponent(id) + '/plays', { since: next, limit: 1000 });
    for (const p of r.plays) if (!plays.some(x => x.n === p.n)) plays.push(p);
    next = r.next;
  };
  await loadPlays();
  const g0 = d.game;
  ctx.setTitle(`${g0.away.abbr} @ ${g0.home.abbr}`, true);
  const tabs = () => [['summary', 'Summary'], ['plays', 'Plays'], ['box', 'Box score']];
  let tab = d.game.status === 'live' ? 'plays' : 'summary';

  const body = () => {
    const g = d.game;
    if (tab === 'summary') {
      const scoring = plays.filter(p => p.scoringTeam);
      return `<div class="stack loose">${lineScore(d)}
        ${g.status === 'scheduled' ? `<div class="banner info">${ic('info')}<div class="body-m">Play-by-play and the box score appear as the game airs — nothing is revealed early.</div></div>` : ''}
        ${scoring.length ? `<div class="stack tight"><div class="section-h"><h2>Scoring</h2></div><div class="card"><div class="plays">${scoring.map(p => playHtml(p, { game: g })).join('')}</div></div></div>` : ''}
        ${inactiveCard(d)}</div>`;
    }
    if (tab === 'plays') {
      const list = ui.playFilter === 'scoring' ? plays.filter(p => p.scoringTeam) : plays;
      return `<div class="stack"><div class="chips">${[['all', 'All plays'], ['scoring', 'Scoring']].map(([k, l]) => `<button class="chip sl ${ui.playFilter === k ? 'selected' : ''}" data-act="pfilter" data-v="${k}">${ui.playFilter === k ? ic('check') : ''}${l}</button>`).join('')}</div>
        <div id="plays-list">${playsByQuarter(list, g, g.status === 'live')}</div></div>`;
    }
    const b = d.box; if (!b) return emptyState('table_chart', 'No box score yet');
    const side = b[ui.boxSide];
    return `<div class="stack loose">${statCompare(d)}
      <div class="seg" role="tablist">${['away', 'home'].map(k => `<button class="sl ${ui.boxSide === k ? 'on' : ''}" data-act="boxside" data-v="${k}">${ic('check')}${badge(g[k], 24)}${esc(g[k].abbr)}</button>`).join('')}</div>
      ${playerTables(side)}</div>`;
  };
  const draw = () => {
    const keepY = scrollY;
    ctx.mount(`<div class="stack"><div id="board">${gameBoard(d)}</div>
      <div class="tabs" role="tablist">${tabs().map(([k, l]) => `<button class="tab sl ${tab === k ? 'on' : ''}" role="tab" aria-selected="${tab === k}" data-act="gtab" data-v="${k}" ${k !== 'summary' && d.game.status === 'scheduled' ? 'disabled style="opacity:.4"' : ''}>${l}</button>`).join('')}</div>
      <div id="gbody" style="padding-top:8px">${body()}</div></div>`);
    scrollTo(0, keepY);
  };
  ctx.actions.gtab = el => { if (el.disabled) return; tab = ui.gameTab = el.dataset.v; draw(); };
  ctx.actions.pfilter = el => { ui.playFilter = el.dataset.v; draw(); };
  ctx.actions.boxside = el => { ui.boxSide = el.dataset.v; draw(); };
  draw();
  setConn(d.game.status === 'live' ? 'live' : 'on', d.game.status === 'live' ? 'LIVE' : 'Connected');

  const refreshDetail = async () => {
    try { const nd = await api('games/' + encodeURIComponent(id)); const was = d.game.status; d = nd; await loadPlays(); if (was !== d.game.status) return draw(); ctx.patch('#board', gameBoard(d)); if (tab === 'box') ctx.patch('#gbody', body()); } catch (e) { /* ignore */ }
  };
  const soft = throttle(refreshDetail, 8000);
  if (S.season == null) {
    const g = d.game;
    if (g.status === 'final') return;
    const onPlay = p => {
      if (!plays.some(x => x.n === p.n)) plays.push(p);
      next = Math.max(next, p.n);
      d.game.home.score = p.score.home; d.game.away.score = p.score.away;
      ctx.patch('#board', gameBoard(d));
      if (tab === 'plays') { const list = ui.playFilter === 'scoring' ? plays.filter(x => x.scoringTeam) : plays; ctx.patch('#plays-list', playsByQuarter(list, d.game, true)); }
      if (tab === 'summary') ctx.patch('#gbody', body());
      soft();
    };
    openStream(ctx, `games/${encodeURIComponent(id)}/stream`, {
      play: onPlay,
      state() { if (d.game.status === 'scheduled') refreshDetail(); },
      final() { setTimeout(refreshDetail, 1200); setConn('on', 'Final'); },
      end() { refreshDetail(); },
    }, refreshDetail);
  }
};

/* =====================================================================
   WATCH (live broadcast / replay) — see viewer.js
   The animation needs each game's seed, which the public /api/v1 deliberately doesn't expose. The site's own data
   endpoints (/api/state, /api/history/N) do, but they don't send CORS headers, so inside the Android app the request goes
   through a native bridge; on the server's own origin a normal fetch works.
   ===================================================================== */
const scriptsLoaded = {};
const loadScript = src => scriptsLoaded[src] || (scriptsLoaded[src] = new Promise((res, rej) => {
  const el = document.createElement('script'); el.src = src; el.onload = res;
  el.onerror = () => { delete scriptsLoaded[src]; rej(new Error('Could not load ' + src)); };
  document.head.appendChild(el);
}));
const nativeCalls = {}; let nativeSeq = 0;
window.__nativeFetch = (id, ok, text) => {
  const c = nativeCalls[id]; delete nativeCalls[id]; if (!c) return;
  if (!ok) return c.rej(new ApiError(0, text || 'Request failed'));
  try { c.res(JSON.parse(text)); } catch (e) { c.rej(new ApiError(0, 'Bad response')); }
};
const imemo = new Map();
function internalJson(path, ttl = 0) {
  const key = S.base + path, hit = imemo.get(key);
  if (ttl && hit && Date.now() - hit.t < ttl) return hit.p;
  const p = (NATIVE && NATIVE.fetchInternal)
    ? new Promise((res, rej) => { const id = 'f' + (++nativeSeq); nativeCalls[id] = { res, rej }; NATIVE.fetchInternal(S.base, path, id); })
    : fetch(S.base.replace(/\/+$/, '') + path, { cache: 'no-store' }).then(r => { if (!r.ok) throw new ApiError(r.status, 'Request failed'); return r.json(); }).catch(e => { throw e instanceof ApiError ? e : new ApiError(0, 'blocked'); });
  imemo.set(key, { t: Date.now(), p }); p.catch(() => imemo.delete(key));
  return p;
}
async function replayData(g) {
  let data, rosters, offset = 0;
  if (S.season == null) {
    const st = await internalJson('/api/state', g.status === 'live' ? 0 : 20000);
    offset = st.now - Date.now(); rosters = st.season.rosters; data = st.season.games;
  } else {
    const h = await internalJson('/api/history/' + S.season, 600000);
    rosters = h.rosters; data = h.games;
  }
  const game = data.find(x => String(x.gid).toUpperCase() === String(g.id).toUpperCase());
  if (!game) throw new ApiError(404, 'Game data not found');
  return { game, rosters: rosters || window.IFL.DEFAULT_ROSTERS, offset };
}
VIEWS.watch = async function (ctx) {
  const id = ctx.args[0];
  const d = await api('games/' + encodeURIComponent(id));    // v1 is the spoiler gate: only games that have started can be watched
  const g = d.game;
  ctx.setTitle(`${g.away.abbr} @ ${g.home.abbr}`, true);
  if (g.status === 'scheduled') {
    return ctx.mount(`<div class="center empty">${ic('schedule')}<div class="title-m">Not on air yet</div><div class="body-m">${esc(g.away.name)} at ${esc(g.home.name)} starts ${esc(dayLabel(g.startsAtMs))} at ${TIME.format(g.startsAtMs)}. Come back at kickoff to watch it live.</div>
      <button class="btn tonal sl" data-act="back">Back</button></div>`);
  }
  let data;
  try { await loadScript('vendor/engine.js'); await loadScript('viewer.js'); data = await replayData(g); }
  catch (e) {
    const blocked = e instanceof ApiError && e.code === 0;
    return ctx.mount(`<div class="center"><span class="ms" style="font-size:48px;color:var(--error)">${blocked ? 'lock' : 'error'}</span><div class="title-m">${blocked ? 'Can’t load the game data here' : 'Couldn’t load the game'}</div>
      <div class="body-m muted" style="max-width:460px">${blocked ? 'The animated viewer needs each game’s seed, which the server only shares with its own site and the Android app. Use the Android app, or host this app from the Gamecast server itself.' : esc(e.message)}</div>
      <div class="row"><button class="btn tonal sl" data-act="retry">${ic('refresh')}Try again</button><button class="btn text sl" data-act="back">Back</button></div></div>`);
  }
  const live = g.status === 'live';
  ctx.mount('<div id="viewer-root"></div>');
  const v = window.Viewer.create(ctx.$('#viewer-root'), {
    game: data.game, rosters: data.rosters, mode: live ? 'live' : 'replay', airStart: data.game.airStart, offset: data.offset,
    homeRef: g.home, awayRef: g.away, base: S.base,
    expected: g.status === 'final' ? { home: g.home.score, away: g.away.score } : null,
  });
  ctx.onCleanup(() => v.destroy());
  setConn(live ? 'live' : 'on', live ? 'LIVE' : 'Replay');
};

/* =====================================================================
   PLAYOFF ODDS (simulated from /api/v1 data — see odds.js)
   ===================================================================== */
let oddsMemo = { key: null, t: 0, p: null };
async function getOdds() {
  if (S.season != null) return null;
  await loadScript('vendor/engine.js'); await loadScript('odds.js');
  const [gm, tm, st] = await Promise.all([allGames({ season: 'current' }), api('teams', { season: 'current' }, 60000), api('standings', { season: 'current' })]);
  const games = gm.games, reg = games.filter(g => !g.playoff);
  const ratings = {}, refs = {};
  tm.teams.forEach(t => { ratings[t.id] = t.rating; refs[t.id] = t; });
  if (tm.teams.some(t => !t.rating)) return null;
  const sf = games.filter(g => g.id === 'P-SF1' || g.id === 'P-SF2').sort((a, b) => (a.id < b.id ? -1 : 1));
  const fin = games.find(g => g.id === 'P-F');
  const res = g => (g.status === 'final' ? { result: { hs: g.home.score, as: g.away.score } } : {});
  const playoffs = st.playoffs && st.playoffs.seeds && st.playoffs.seeds.length ? { seeds: st.playoffs.seeds.slice().sort((a, b) => a.seed - b.seed).map(x => x.id) } : null;
  const input = {
    teams: tm.teams.map(t => t.id), ratings,
    played: reg.filter(g => g.status === 'final').map(g => ({ home: g.home.id, away: g.away.id, hs: g.home.score, as: g.away.score })),
    remaining: reg.filter(g => g.status !== 'final').map(g => ({ home: g.home.id, away: g.away.id, host: g.venue && g.venue.host ? g.venue.host : undefined })),
    playoffs, semis: playoffs && sf.length === 2 ? sf.map(g => Object.assign({ home: g.home.id, away: g.away.id, host: g.venue && g.venue.host ? g.venue.host : undefined }, res(g))) : null,
    final: fin ? Object.assign({ home: fin.home.id, away: fin.away.id, host: fin.venue && fin.venue.host ? fin.venue.host : null }, res(fin)) : null,
    champion: st.champion ? st.champion.id : null,
  };
  const key = [input.played.length, playoffs ? 1 : 0, sf.filter(g => g.status === 'final').length, fin ? 1 : 0, input.champion || ''].join('|');
  if (oddsMemo.key === key && Date.now() - oddsMemo.t < 600000 && oddsMemo.p) return oddsMemo.p;
  const p = window.Odds.compute(input).then(o => Object.assign(o, { refs, playoffsKnown: !!playoffs, seeds: playoffs ? playoffs.seeds : null, champion: input.champion, played: input.played.length, total: reg.length }));
  oddsMemo = { key, t: Date.now(), p };
  p.catch(() => { oddsMemo = { key: null, t: 0, p: null }; });
  return p;
}
function pctTxt(x) {
  if (x >= 1) return '100%'; if (x <= 0) return '0%';
  if (x >= .995) return '>99%'; if (x < .005) return '<1%';
  return Math.round(x * 100) + '%';
}
function oddsRows(t) {
  const row = (label, icon, v) => `<div class="odds-row"><div class="row between"><span class="row" style="gap:8px">${ic(icon, 'sm')}<span class="label-l">${label}</span></span><span class="title-m tnum">${pctTxt(v)}</span></div>
    <div class="linear"><i style="width:${Math.max(v > 0 ? 1.5 : 0, v * 100)}%"></i></div></div>`;
  return `${row('Make the playoffs', 'flag', t.playoffs)}${row('Reach the IFL Bowl', 'stadium', t.bowl)}${row('Win the IFL Bowl', 'emoji_events', t.champ)}`;
}
function oddsNote(o, id) {
  if (o.champion) return o.champion === id ? 'Champions!' : '';
  if (o.playoffsKnown && !o.seeds.includes(id)) return 'Missed the playoffs this season.';
  if (o.playoffsKnown) return `Playoff seed ${o.seeds.indexOf(id) + 1}.`;
  return `Simulated ${o.sims.toLocaleString()} times from team ratings · ${o.played} of ${o.total} games played`;
}
function teamOddsCard(o, id) {
  const t = o && o.teams[id], ref = o && o.refs[id];
  if (!t || !ref) return '';
  return `<div class="card stack" ${S.fav && S.fav.id === id ? '' : ''}>
    <button class="row sl" data-go="#/team/${esc(id)}" style="gap:12px;text-align:left;border-radius:12px">${badge(ref, 40)}<div class="grow"><div class="title-m">${esc(ref.name)}</div><div class="body-s muted">Chance to…</div></div>${ic('chevron_right')}</button>
    <div class="stack tight">${oddsRows(t)}</div><div class="body-s muted">${esc(oddsNote(o, id))}</div></div>`;
}
function oddsInline(o, id) {
  const t = o && o.teams[id];
  return t ? `<div class="card stack"><div class="title-m">Chance to…</div><div class="stack tight">${oddsRows(t)}</div><div class="body-s muted">${esc(oddsNote(o, id))}</div></div>` : '';
}
function oddsPlaceholder(text) { return `<div class="card filled"><div class="row"><div class="spinner" style="width:24px;height:24px;border-width:3px"></div><span class="body-m muted">${esc(text)}</span></div></div>`; }
function favOddsHtml(o) {
  if (!S.fav) return `<button class="card filled sl row" data-act="settings" style="gap:12px">${ic('star', 'fill')}<div class="grow"><div class="title-m">Follow your team</div><div class="body-s muted">Pick a team to see its chances of reaching and winning the IFL Bowl.</div></div>${ic('chevron_right')}</button>`;
  if (!o) return oddsPlaceholder('Working out your team’s title chances…');
  return teamOddsCard(o, S.fav.id);
}

/* =====================================================================
   STANDINGS
   ===================================================================== */
function standTable(title, rows, compact) {
  return `<div class="stack tight">${title ? `<div class="section-h"><h2>${esc(title)}</h2></div>` : ''}<div class="card flush tbl-wrap"><table class="t"><thead><tr><th class="l">Team</th><th>W-L</th>${compact ? '' : '<th>PCT</th>'}<th>PF</th><th>PA</th><th>DIFF</th>${compact ? '' : '<th>HOME</th><th>AWAY</th><th>DIV</th><th>STRK</th>'}</tr></thead><tbody>
    ${rows.map((r, i) => `<tr data-go="#/team/${esc(r.team.id)}"><td><div class="tm"><span class="muted label-m" style="width:16px;font-weight:400">${r.rank || i + 1}</span>${badge(r.team, 32)}<span>${esc(compact ? r.team.abbr : r.team.name)} ${star(r.team.id)}</span></div></td>
      <td>${r.wins}-${r.losses}${r.ties ? '-' + r.ties : ''}</td>${compact ? '' : `<td>${(+r.pct).toFixed(3).replace(/^0/, '')}</td>`}<td>${r.pointsFor}</td><td>${r.pointsAgainst}</td>
      <td style="color:${r.differential > 0 ? 'var(--success)' : r.differential < 0 ? 'var(--error)' : 'inherit'}">${r.differential > 0 ? '+' : ''}${r.differential}</td>
      ${compact ? '' : `<td>${esc(r.home)}</td><td>${esc(r.away)}</td><td>${esc(r.divisionRecord)}</td><td>${esc(r.streak || '–')}</td>`}</tr>`).join('')}</tbody></table></div></div>`;
}
VIEWS.standings = async function (ctx) {
  const s = await api('standings');
  const dorder = n => { const i = ['east', 'central', 'west'].findIndex(k => String(n).toLowerCase().includes(k)); return i < 0 ? 9 : i; };
  s.divisions.sort((a, b) => dorder(a.name) - dorder(b.name));
  const draw = () => {
    let body = '';
    if (ui.standTab === 'div') body = `<div class="stack loose">${s.divisions.map(d => standTable(d.name + ' Division', d.teams)).join('')}</div>`;
    else if (ui.standTab === 'all') body = standTable('', s.overall);
    else if (ui.standTab === 'odds') body = S.season != null ? emptyState('insights', 'Odds are for the current season', 'Switch back to the current season to see playoff and IFL Bowl chances.') : `<div id="odds-body">${oddsPlaceholder('Simulating the rest of the season…')}</div>`;
    else if (s.playoffs && s.playoffs.seeds && s.playoffs.seeds.length) {
      body = `<div class="card flush"><div class="list">${s.playoffs.seeds.map(x => `<button class="li two sl" data-go="#/team/${esc(x.id)}"><span class="headline-s" style="width:28px;text-align:center;color:var(--primary)">${x.seed}</span>${badge(x, 40)}<div class="grow"><div class="t">${esc(x.name)}</div>${x.qualified ? `<div class="s">${esc(x.qualified)}</div>` : ''}</div>${ic('chevron_right')}</button>`).join('')}</div></div>`;
    } else body = emptyState('emoji_events', 'Playoff seeds aren’t set yet', 'They’re locked in when the regular season ends.');
    ctx.mount(`<div class="stack">
      ${s.champion ? championCard(s.champion, s.season) : ''}
      <div class="row between wrap"><div class="body-m muted">Season ${s.season} · ${s.regularSeasonGamesPlayed} of ${s.regularSeasonGames} regular-season games played</div></div>
      <div class="seg" role="tablist">${[['div', 'Divisions'], ['all', 'League'], ['po', 'Seeds'], ['odds', 'Odds']].map(([k, l]) => `<button class="sl ${ui.standTab === k ? 'on' : ''}" data-act="stab" data-v="${k}">${ic('check')}${l}</button>`).join('')}</div>
      ${body}</div>`);
    if (ui.standTab === 'odds' && S.season == null) {
      getOdds().then(o => {
        if (!o) return ctx.patch('#odds-body', emptyState('insights', 'Odds aren’t available yet'));
        const ids = Object.keys(o.teams).sort((a, b) => o.teams[b].champ - o.teams[a].champ || o.teams[b].bowl - o.teams[a].bowl || o.teams[b].playoffs - o.teams[a].playoffs);
        ctx.patch('#odds-body', `<div class="stack tight"><div class="card flush tbl-wrap"><table class="t"><thead><tr><th class="l">Team</th><th>PLAYOFFS</th><th>BOWL</th><th>TITLE</th></tr></thead><tbody>
          ${ids.map(id => { const t = o.teams[id], r = o.refs[id]; return `<tr data-go="#/team/${esc(id)}"><td><div class="tm">${badge(r, 32)}<span>${esc(r.name)} ${star(id)}</span></div></td><td>${pctTxt(t.playoffs)}</td><td>${pctTxt(t.bowl)}</td><td><b>${pctTxt(t.champ)}</b></td></tr>`; }).join('')}</tbody></table></div>
          <div class="body-s muted" style="padding:4px 4px 0">${esc(oddsNote(o, '—'))}. Bowl = reaches the IFL Bowl; title = wins it.</div></div>`);
      }).catch(() => ctx.patch('#odds-body', emptyState('insights', 'Couldn’t work out the odds', 'Try again in a moment.')));
    }
  };
  ctx.actions.stab = el => { ui.standTab = el.dataset.v; draw(); };
  draw();
};

/* =====================================================================
   TEAMS
   ===================================================================== */
VIEWS.teams = async function (ctx) {
  const t = await api('teams', {}, 30000);
  const divs = new Map();
  for (const x of t.teams) { const k = x.division ? x.division.name : 'League'; if (!divs.has(k)) divs.set(k, []); divs.get(k).push(x); }
  const order = n => { const i = ['east', 'central', 'west'].findIndex(k => n.toLowerCase().includes(k)); return i < 0 ? 9 : i; };
  const names = [...divs.keys()].sort((a, b) => order(a) - order(b) || a.localeCompare(b));
  let sel = 'all';
  const draw = () => {
    const shown = names.filter(n => sel === 'all' || n === sel);
    ctx.mount(`<div class="stack loose"><div class="chips" style="margin-bottom:-12px">${['all', ...names].map(n => `<button class="chip sl ${sel === n ? 'selected' : ''}" data-act="div" data-v="${esc(n)}">${sel === n ? ic('check') : ''}${n === 'all' ? 'All divisions' : esc(n)}</button>`).join('')}</div>
    ${shown.map(name => `<div class="stack tight"><div class="section-h"><h2>${esc(name)}</h2></div>
    <div class="grid cols-3">${divs.get(name).map(x => `<button class="card filled sl row" data-go="#/team/${esc(x.id)}" style="gap:16px">${badge(x, 56)}
      <div class="grow"><div class="title-m ellipsis">${esc(x.name)} ${star(x.id)}</div><div class="body-m muted">${esc(x.record)}${x.rating ? ' · OVR ' + x.rating.ovr : ''}</div></div></button>`).join('')}</div></div>`).join('')}</div>`);
  };
  ctx.actions.div = el => { sel = el.dataset.v; draw(); };
  draw();
};

const POS_ORDER = ['QB', 'RB', 'WR', 'TE', 'OL', 'DL', 'LB', 'CB', 'S', 'K', 'P'];
const POS_NAME = { QB: 'Quarterbacks', RB: 'Running backs', WR: 'Wide receivers', TE: 'Tight ends', OL: 'Offensive line', DL: 'Defensive line', LB: 'Linebackers', CB: 'Cornerbacks', S: 'Safeties', K: 'Kickers', P: 'Punters' };
function ringHtml(label, v) { return `<div class="stat-tile" style="background:rgba(255,255,255,.16);color:#fff"><div class="n">${v == null ? '–' : v}</div><div class="label-m" style="opacity:.85">${label}</div></div>`; }
VIEWS.team = async function (ctx) {
  const id = ctx.args[0];
  const t = await api('teams/' + encodeURIComponent(id));
  ctx.setTitle(t.name, true);
  const r = t.record, [c1, c2] = t.colors || [];
  let teamOdds = null;
  const draw = () => {
    let body;
    if (ui.teamTab === 'roster') {
      const groups = POS_ORDER.map(pos => [pos, t.roster.filter(p => p.position === pos).sort((a, b) => b.rating - a.rating)]).filter(x => x[1].length);
      body = `<div class="stack">${groups.map(([pos, list]) => `<div class="card flush"><div class="pos-grp">${POS_NAME[pos] || pos}</div><div class="list">${list.map(p => `<button class="li sl" data-act="player" data-id="${esc(p.id)}" style="min-height:56px">
        <span class="label-l tnum" style="width:28px;color:var(--on-surface-variant)">${p.number}</span><div class="grow"><div class="t ellipsis">${esc(p.name)}</div><div class="s">Age ${p.age} · ${p.yearsPro} yr${p.yearsPro === 1 ? '' : 's'} pro</div></div>
        ${p.injury ? tag(p.injury.seasonEnding ? 'OUT – SEASON' : 'OUT ' + (p.injury.gamesLeft || '') , 'inj') : ''}<span class="title-m tnum" style="min-width:28px;text-align:right">${p.rating}</span></button>`).join('')}</div></div>`).join('')}</div>`;
    } else {
      const byWeek = t.schedule;
      body = byWeek.length ? gameList(byWeek) : emptyState('event', 'No games scheduled');
    }
    ctx.mount(`<div class="stack loose">
      <section class="team-hero" style="--tc:${esc(c1 || '#555')};--tc2:${esc(c2 || '#fff')}">
        <div class="row" style="gap:16px;position:relative;z-index:1">${badge(t, 96)}<div class="grow"><div class="label-m" style="opacity:.85">${esc(t.division ? t.division.name + ' Division' : '')}</div>
          <div class="headline-s">${esc(t.city)}<br><b style="font-weight:700">${esc(t.nickname)}</b></div>
          <div class="row wrap" style="margin-top:8px;gap:8px"><span class="pill">${r ? `${r.wins}-${r.losses}${r.ties ? '-' + r.ties : ''}` : '0-0'}</span>${r && r.streak ? `<span class="pill">${esc(r.streak)}</span>` : ''}${t.stadium && t.stadium.name ? `<span class="pill">${esc(t.stadium.name)}</span>` : ''}</div></div></div>
        <div class="stat-tiles" style="margin-top:16px;position:relative;z-index:1">${ringHtml('OVERALL', t.rating && t.rating.ovr)}${ringHtml('OFFENSE', t.rating && t.rating.off)}${ringHtml('DEFENSE', t.rating && t.rating.def)}</div>
      </section>
      <div class="row wrap">
        <button class="btn ${isFav(t.id) ? 'tonal' : 'outlined'} sl" data-act="fav">${ic(isFav(t.id) ? 'star' : 'star', isFav(t.id) ? 'fill' : '')}${isFav(t.id) ? 'My team' : 'Make my team'}</button>
        ${r ? `<span class="body-m muted">PF ${r.pointsFor} · PA ${r.pointsAgainst} · Home ${esc(r.home)} · Away ${esc(r.away)}</span>` : ''}</div>
      ${S.season == null ? `<div id="team-odds">${teamOdds ? oddsInline(teamOdds, t.id) : ''}</div>` : ''}
      <div><div class="tabs" role="tablist">${[['roster', 'Roster'], ['schedule', 'Schedule']].map(([k, l]) => `<button class="tab sl ${ui.teamTab === k ? 'on' : ''}" role="tab" data-act="ttab" data-v="${k}">${l}</button>`).join('')}</div><div style="padding-top:16px">${body}</div></div>
    </div>`);
  };
  ctx.actions.ttab = el => { ui.teamTab = el.dataset.v; draw(); };
  ctx.actions.fav = () => {
    if (isFav(t.id)) { S.fav = null; store.del('fav'); snack('Theme reset'); }
    else { S.fav = { id: t.id, hue: Math.round(hexToHue(c1 || '#4a4fd0')) }; store.set('fav', S.fav); snack(`${t.name} is now your team`); }
    applyTheme(); syncAlarms(); draw();
  };
  draw();
  if (S.season == null) getOdds().then(o => { teamOdds = o; ctx.patch('#team-odds', oddsInline(o, t.id)); }).catch(() => {});
};

/* =====================================================================
   LEADERS
   ===================================================================== */
VIEWS.leaders = async function (ctx) {
  const ov = await api('leaders', { limit: 5 }, 15000);
  const cats = Object.keys(ov.categories);
  if (ui.leadCat !== 'overview' && !cats.includes(ui.leadCat)) ui.leadCat = 'overview';
  const rows = (list, big) => `<div class="list">${list.map(l => `<button class="li sl ${l.rank === 1 && big ? 'leader-1' : ''}" data-act="player" data-id="${esc(l.id)}" style="min-height:56px">
    <span class="label-l tnum" style="width:20px;color:inherit;opacity:.7">${l.rank}</span>${badge(l.teamInfo, 32)}<div class="grow"><div class="t ellipsis">${esc(l.name)}</div><div class="s" style="color:inherit;opacity:.75">${esc(l.position)} · ${esc(l.teamInfo.abbr)} · ${l.gamesPlayed} GP</div></div>
    <span class="title-l tnum">${num(l.value)}</span></button>`).join('')}</div>`;
  const draw = async () => {
    let body;
    if (ui.leadCat === 'overview') {
      body = `<div class="grid cols-2" style="align-items:start">${cats.filter(k => ov.categories[k].leaders.length).map(k => `<div class="stack tight"><div class="section-h"><h2>${esc(ov.categories[k].label)}</h2><button class="btn text sl" data-act="lcat" data-v="${k}">More</button></div><div class="card flush">${rows(ov.categories[k].leaders.slice(0, 3), true)}</div></div>`).join('')}</div>`;
      if (!cats.some(k => ov.categories[k].leaders.length)) body = emptyState('military_tech', 'No stats yet', 'Leaders appear once games have been played.');
    } else {
      body = `<div class="skeleton" style="height:320px"></div>`;
    }
    ctx.mount(`<div class="stack"><div class="chips" id="lead-chips"><button class="chip sl ${ui.leadCat === 'overview' ? 'selected' : ''}" data-act="lcat" data-v="overview">${ui.leadCat === 'overview' ? ic('check') : ''}Overview</button>
      ${cats.map(k => `<button class="chip sl ${ui.leadCat === k ? 'selected' : ''}" data-act="lcat" data-v="${k}">${ui.leadCat === k ? ic('check') : ''}${esc(ov.categories[k].label)}</button>`).join('')}</div><div id="lead-body">${body}</div></div>`);
    const sel = $('#lead-chips .selected'); if (sel) sel.scrollIntoView({ inline: 'center', block: 'nearest' });
    if (ui.leadCat !== 'overview') {
      try {
        const r = await api('leaders', { category: ui.leadCat, limit: 25 }, 15000);
        const c = r.categories[ui.leadCat];
        ctx.patch('#lead-body', c.leaders.length ? `<div class="card flush">${rows(c.leaders, true)}</div>` : emptyState('military_tech', 'No leaders yet'));
      } catch (e) { ctx.patch('#lead-body', errorState(e)); }
    }
  };
  ctx.actions.lcat = el => { ui.leadCat = el.dataset.v; draw(); };
  draw();
};

/* =====================================================================
   Player sheet, season sheet, settings sheet
   ===================================================================== */
function openSheet(html, onOpen) {
  closeSheet();
  const o = $('#overlay');
  o.innerHTML = `<div class="scrim" data-act="close-sheet"></div><div class="sheet" role="dialog" aria-modal="true"><div class="handle"></div>${html}</div>`;
  document.body.style.overflow = 'hidden';
  const f = $('.sheet button, .sheet input', o); if (f) f.focus({ preventScroll: true });
  if (onOpen) onOpen($('.sheet', o));
}
function closeSheet() { $('#overlay').innerHTML = ''; document.body.style.overflow = ''; }
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeSheet(); });

const STAT_LABEL = { gp: 'Games', pa: 'Pass attempts', pc: 'Completions', py: 'Passing yards', ptd: 'Passing TD', pint: 'Interceptions thrown', ra: 'Rushes', ry: 'Rushing yards', rlng: 'Longest rush', rtd: 'Rushing TD',
  tgt: 'Targets', rec: 'Receptions', recy: 'Receiving yards', reclng: 'Longest reception', rectd: 'Receiving TD', fum: 'Fumbles', tk: 'Tackles', sacks: 'Sacks', ints: 'Interceptions', intyds: 'INT return yards',
  deftd: 'Defensive TD', pd: 'Passes defended', ff: 'Forced fumbles', fr: 'Fumble recoveries', kr: 'Kick returns', kry: 'KR yards', pr: 'Punt returns', pry: 'PR yards', krtd: 'KR TD', prtd: 'PR TD',
  fga: 'FG attempts', fgm: 'FG made', fglng: 'Longest FG', xpa: 'XP attempts', xpm: 'XP made', pn: 'Punts', pny: 'Punt yards' };
async function showPlayer(id) {
  openSheet(`<div class="center"><div class="spinner"></div></div>`);
  try {
    const p = await api('players/' + encodeURIComponent(id));
    const st = p.seasonStats || {};
    const keys = Object.keys(st).filter(k => st[k]).sort((a, b) => (k => k === 'gp' ? -1 : 0)(a) - (k => k === 'gp' ? -1 : 0)(b));
    $('.sheet', $('#overlay')).innerHTML = `<div class="handle"></div>
      <div class="row" style="gap:16px;padding:8px 0 16px">${badge(p.team, 56)}<div class="grow"><div class="headline-s ellipsis">${esc(p.name)}</div><div class="body-m muted">#${p.number} · ${esc(p.position)} · ${esc(p.team.name)}</div></div>
        <button class="icon-btn sl" data-act="close-sheet" aria-label="Close">${ic('close')}</button></div>
      <div class="stat-tiles"><div class="stat-tile"><div class="n">${p.rating}</div><div class="label-m muted">OVERALL</div></div><div class="stat-tile"><div class="n">${p.age}</div><div class="label-m muted">AGE</div></div><div class="stat-tile"><div class="n">${p.yearsPro}</div><div class="label-m muted">YEARS PRO</div></div></div>
      ${p.injury ? `<div class="banner warn" style="margin-top:12px">${ic('personal_injury')}<div><div class="title-s">Injured — ${esc(p.injury.part)}</div><div class="body-s">${p.injury.seasonEnding ? 'Out for the rest of the season' : 'Out ' + (p.injury.gamesLeft || '?') + ' more game' + (p.injury.gamesLeft === 1 ? '' : 's')}</div></div></div>` : ''}
      <div class="section-h" style="margin-top:12px"><h2>Season ${p.season} stats</h2></div>
      ${keys.length ? `<dl class="kv card filled" style="margin:8px 0 0">${keys.map(k => `<dt>${esc(STAT_LABEL[k] || k)}</dt><dd class="tnum">${num(st[k])}</dd>`).join('')}</dl>` : '<div class="body-m muted" style="padding:8px 0">No stats recorded yet.</div>'}
      <div class="row" style="margin-top:16px"><button class="btn tonal sl grow" data-go="#/team/${esc(p.team.id)}" data-close="1">${ic('groups')}Team page</button></div>`;
  } catch (e) { $('.sheet', $('#overlay')).innerHTML = `<div class="handle"></div>${errorState(e)}`; }
}
async function showSeasons() {
  openSheet(`<h2 class="title-l">Season</h2><div class="center"><div class="spinner"></div></div>`);
  try {
    const { seasons } = await api('seasons');
    const curNum = (seasons.find(s => s.current) || {}).number;
    S.currentSeason = curNum;
    $('.sheet', $('#overlay')).innerHTML = `<div class="handle"></div><h2 class="title-l">Choose season</h2><div class="list">${seasons.map(s => {
      const sel = (S.season == null && s.current) || S.season === s.number;
      return `<button class="li two sl ${sel ? 'sel' : ''}" data-act="pick-season" data-v="${s.number}" data-cur="${s.current ? 1 : 0}">${s.champion ? badge(s.champion, 40) : `<span class="ms" style="font-size:40px;color:var(--outline)">${s.current ? 'play_circle' : 'help'}</span>`}
        <div class="grow"><div class="t">Season ${s.number}${s.current ? ' · current' : ''}</div><div class="s">${s.champion ? 'Champion: ' + esc(s.champion.name) : (s.done ? 'Complete' : 'In progress')}${s.runnerUp ? ' · Runner-up ' + esc(s.runnerUp.abbr) : ''}</div></div>${sel ? ic('check') : ''}</button>`;
    }).join('')}</div>`;
  } catch (e) { $('.sheet', $('#overlay')).innerHTML = `<div class="handle"></div>${errorState(e)}`; }
}
async function showSettings() {
  let teams = [];
  try { teams = (await api('teams', {}, 60000)).teams; } catch (e) { /* offline: still allow settings */ }
  openSheet(`<h2 class="title-l">Settings</h2>
    <div class="stack loose" style="padding:8px 0">
      <div class="stack tight"><div class="title-s">Appearance</div>
        <div class="seg">${[['auto', 'System', 'brightness_auto'], ['light', 'Light', 'light_mode'], ['dark', 'Dark', 'dark_mode']].map(([k, l]) => `<button class="sl ${S.theme === k ? 'on' : ''}" data-act="set-theme" data-v="${k}">${ic('check')}${l}</button>`).join('')}</div></div>
      <div class="stack tight"><div class="title-s">My team &amp; colours</div><div class="body-s muted">Pick a team to colour the whole app with its palette and highlight its games.</div>
        <div class="swatches" role="radiogroup">
          <button class="swatch sl ${S.fav ? '' : 'on'}" role="radio" aria-checked="${!S.fav}" aria-label="Default colours" data-act="set-fav" data-id="" style="background:oklch(.48 .17 262)">${ic('check')}</button>
          ${teams.map(t => `<button class="swatch sl ${isFav(t.id) ? 'on' : ''}" role="radio" aria-checked="${isFav(t.id)}" aria-label="${esc(t.name)}" title="${esc(t.name)}" data-act="set-fav" data-id="${esc(t.id)}" data-c="${esc((t.colors || [])[0] || '')}" style="background:${esc((t.colors || [])[0] || '#777')}">${ic('check')}</button>`).join('')}</div></div>
      ${notifySection()}
      <div class="stack tight"><div class="title-s">Server</div><div class="field"><label for="f-base">Gamecast server address</label><input id="f-base" type="url" inputmode="url" autocapitalize="off" spellcheck="false" value="${esc(S.base)}" placeholder="${DEFAULT_BASE}"></div>
        <div class="row wrap"><button class="btn filled sl" data-act="save-base">Save &amp; test</button><button class="btn text sl" data-act="reset-base">Reset</button></div>
        <div class="body-s muted">The app talks to <code>/api/v1</code> on this address (read-only, no key needed).</div></div>
      <div class="row"><button class="btn tonal sl grow" data-act="close-sheet">Done</button></div>
    </div>`);
}

/* =====================================================================
   Game-start notifications (Android app): hand the upcoming schedule to the native alarm scheduler
   ===================================================================== */
async function syncAlarms() {
  if (!NATIVE) return;
  try {
    if (!S.notify.on || !NATIVE.hasPermission()) { NATIVE.cancelAll(); return; }
    const { games } = await allGames({ status: 'scheduled', season: 'current' });
    const now = Date.now(), teamOnly = S.notify.mode === 'team' && S.fav;
    const list = games
      .filter(g => g.startsAtMs && g.startsAtMs > now - 60000 && (!teamOnly || g.home.id === S.fav.id || g.away.id === S.fav.id))
      .sort((a, b) => a.startsAtMs - b.startsAtMs).slice(0, 40)
      .map(g => ({ id: g.id, ms: g.startsAtMs, label: `${g.away.abbr} @ ${g.home.abbr}` }));
    NATIVE.schedule(S.base, JSON.stringify(list));
  } catch (e) { /* offline: keep the alarms we already have */ }
}
let pendingEnable = false;
window.onNativePerm = granted => {
  if (!pendingEnable) return;
  pendingEnable = false;
  if (granted) { S.notify.on = true; store.set('notify', S.notify); syncAlarms(); snack('Game-start alerts are on'); }
  else snack('Notifications are blocked — allow them in Android settings');
  if ($('.sheet')) showSettings();
};
function notifySection() {
  if (!NATIVE) return '';
  const on = S.notify.on && NATIVE.hasPermission(), teamOk = !!S.fav, mode = teamOk ? S.notify.mode : 'all';
  return `<div class="stack tight"><div class="title-s">Notifications</div>
    <button class="li sl" role="switch" aria-checked="${on}" data-act="notif-toggle" style="padding:8px 0;min-height:56px;border-radius:12px">
      <div class="grow"><div class="t">Game-start alerts</div><div class="s">A notification when a game kicks off — even if the app is closed</div></div><span class="switch ${on ? 'on' : ''}"></span></button>
    ${on ? `<div class="seg"><button class="sl ${mode === 'all' ? 'on' : ''}" data-act="notif-mode" data-v="all">${ic('check')}All games</button>
      <button class="sl ${mode === 'team' ? 'on' : ''}" data-act="notif-mode" data-v="team" ${teamOk ? '' : 'disabled style="opacity:.45"'}>${ic('check')}My team only</button></div>
      ${teamOk ? '' : '<div class="body-s muted">Pick a team under “My team” to get alerts for just its games.</div>'}
      ${NATIVE.canExact() ? '' : `<div class="banner info" style="padding:8px 12px"><div class="body-s grow">Alerts may arrive a few minutes late. Allow exact alarms for on-time kickoff alerts.</div><button class="btn text sl" data-act="alarm-settings">Allow</button></div>`}` : ''}</div>`;
}

/* =====================================================================
   Global events
   ===================================================================== */
const GLOBAL = {
  back() { if (history.length > 1) history.back(); else location.hash = '#/'; },
  retry() { route(); },
  settings() { showSettings(); },
  'close-sheet'() { closeSheet(); },
  player(el) { showPlayer(el.dataset.id); },
  'pick-season'(el) {
    const n = +el.dataset.v; S.season = el.dataset.cur === '1' ? null : n; closeSheet();
    if (['game', 'team', 'watch'].includes(parseHash().name)) location.hash = '#/'; else route();
  },
  'season-current'() { S.season = null; route(); },
  'notif-toggle'() {
    if (!NATIVE) return;
    if (S.notify.on && NATIVE.hasPermission()) { S.notify.on = false; store.set('notify', S.notify); syncAlarms(); showSettings(); return; }
    pendingEnable = true; NATIVE.requestPermission();   // answer arrives in window.onNativePerm
  },
  'notif-mode'(el) { if (el.disabled) return; S.notify.mode = el.dataset.v; store.set('notify', S.notify); syncAlarms(); showSettings(); },
  'alarm-settings'() { if (NATIVE) NATIVE.openAlarmSettings(); },
  'set-theme'(el) { S.theme = el.dataset.v; store.set('theme', S.theme); applyTheme(); $$('.seg button', $('#overlay')).forEach(b => b.classList.toggle('on', b.dataset.v === S.theme)); },
  'set-fav'(el) {
    const id = el.dataset.id;
    if (!id) { S.fav = null; store.del('fav'); } else { S.fav = { id, hue: Math.round(hexToHue(el.dataset.c || '#4a4fd0')) }; store.set('fav', S.fav); }
    applyTheme(); syncAlarms();
    $$('.swatch', $('#overlay')).forEach(b => { const on = (b.dataset.id || '') === (id || ''); b.classList.toggle('on', on); b.setAttribute('aria-checked', on); });
  },
  async 'save-base'() {
    let v = ($('#f-base').value || '').trim().replace(/\/+$/, '').replace(/\/api\/v1$/, '');
    if (!/^https?:\/\//.test(v)) v = 'https://' + v;
    const old = S.base; S.base = v; memo.clear();
    try { await api('status'); store.set('base', v); snack('Connected to ' + v); closeSheet(); route(); }
    catch (e) { S.base = old; snack(e instanceof ApiError && e.code === 404 ? 'That server doesn’t serve /api/v1 yet' : 'Couldn’t connect to ' + v); }
  },
  'reset-base'() { store.del('base'); S.base = defaultBase(); memo.clear(); const f = $('#f-base'); if (f) f.value = S.base; snack('Server address reset'); route(); },
};
document.addEventListener('click', e => {
  const act = e.target.closest('[data-act]'), go = e.target.closest('[data-go]');
  const el = act || go; if (!el) return;
  // ripple
  const sl = e.target.closest('.sl');
  if (sl && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const r = sl.getBoundingClientRect(), size = Math.max(r.width, r.height) * 2, rp = document.createElement('span');
    rp.className = 'ripple'; rp.style.cssText = `width:${size}px;height:${size}px;left:${e.clientX - r.left - size / 2}px;top:${e.clientY - r.top - size / 2}px`;
    sl.appendChild(rp); setTimeout(() => rp.remove(), 600);
  }
  if (act) {
    const name = act.dataset.act, fn = (cur && cur.actions[name]) || GLOBAL[name];
    if (fn) { e.preventDefault(); fn(act); if (!go || go === act) return; }
  }
  if (go) {
    if ($('.sheet') && go.dataset.close) closeSheet();
    if (go.getAttribute('href') == null) location.hash = go.dataset.go;
  }
});
$('#btn-season').addEventListener('click', showSeasons);
$('#btn-settings').addEventListener('click', showSettings);
addEventListener('scroll', () => $('#top-bar').classList.toggle('scrolled', scrollY > 4), { passive: true });
setInterval(() => $$('[data-countdown]').forEach(el => { el.textContent = countdown(+el.dataset.countdown); }), 1000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) { syncAlarms(); if (cur && ['home', 'game'].includes(cur.name)) route(); } });
setInterval(syncAlarms, 15 * 60 * 1000);
syncAlarms();
addEventListener('online', () => route());

if ('serviceWorker' in navigator && location.protocol.startsWith('http') && location.hostname !== 'appassets.androidplatform.net') navigator.serviceWorker.register('sw.js').catch(() => {});
applyTheme();
route();
