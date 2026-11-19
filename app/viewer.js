/* IFL Gamecast — game viewer (watch live / replay).
   Re-simulates a game with the bundled IFL engine (vendor/engine.js, MIT, © Soms-IFL) from its seed and animates it on an
   SVG field, exactly like the website's broadcast: same engine, same timeline, so live viewing is in sync with the broadcast.
   Window.Viewer.create(root, opts) -> { destroy() }. */
(function () {
  'use strict';
  const SVGNS = 'http://www.w3.org/2000/svg';
  const X = a => (a + 10) * 10, Y = y => y * 10;
  const lerp = (a, b, t) => a + (b - a) * t;
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const ic = n => `<span class="ms" aria-hidden="true">${n}</span>`;
  const ORD = ['', '1st', '2nd', '3rd', '4th'];
  const qLabel = q => (q >= 5 ? (q === 5 ? 'OT' : (q - 4) + 'OT') : 'Q' + q);
  const lum = hex => { const c = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255); return .2126 * c[0] + .7152 * c[1] + .0722 * c[2]; };
  const ink = hex => (lum(hex) > .55 ? '#111' : '#fff');
  const visible = hex => (lum(hex) < .12 ? '#ffffff' : hex);     // route trails need to show on grass
  const fmtIn = ms => { const s = Math.max(0, Math.round(ms / 1000)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); };

  // Same pacing helpers the website uses for ball travel.
  function pathSegs(path) {
    const segs = []; let total = 0;
    for (let i = 1; i < path.length; i++) {
      const a = path[i - 1], b = path[i];
      const v = b.m === 'kick' ? 30 : b.m === 'air' ? 22 : 8.5;
      const dur = Math.max(0.18, Math.hypot(b.x - a.x, b.y - a.y) / v) + (b.m === 'kick' ? 0.25 : 0);
      segs.push({ a, b, dur, t0: total }); total += dur;
    }
    return { segs, total };
  }
  // runs `cb` on the next frame, or after 50ms if the page is hidden (rAF is paused in background tabs)
  const raf = cb => { let fired = false; const f = () => { if (fired) return; fired = true; cb(); }; return { a: requestAnimationFrame(f), b: setTimeout(f, 50) }; };
  const caf = h => { if (h) { cancelAnimationFrame(h.a); clearTimeout(h.b); } };

  /* opts: { game:{gid,home,away,seed,playoff,out,venue,label}, rosters, mode:'live'|'replay', airStart, offset (server-now minus Date.now()),
             homeRef, awayRef (v1 team refs for logos), expected:{home,away}|null, base } */
  function create(root, opts) {
    const IFL = window.IFL, o = opts.game;
    const gopts = { playoff: !!o.playoff, rosters: opts.rosters, out: o.out || [], venue: o.venue || undefined };
    const full = IFL.simulateGame(o.home, o.away, o.seed, gopts);
    const stage = IFL.stageOf(o);
    const tl = IFL.timeline(full.events, stage);
    const last = tl[tl.length - 1];
    let game = null;
    const live = opts.mode === 'live';
    const P = { N: full.events.length, idx: 0, began: -1, ps: null, names: {}, airIdx: -1, vt: 0, wall: performance.now(), paused: !live, speed: 1, done: false, dead: false,
      total: last.start + last.dur, wp: [], log: [], lastPoss: null, flagShown: false, anim: null, banTimer: null };
    const mismatch = opts.expected && (full.score[0] !== opts.expected.home || full.score[1] !== opts.expected.away);

    const reset = () => {
      game = new IFL.Game(o.home, o.away, Object.assign({ seed: o.seed }, gopts));
      Object.assign(P, { idx: 0, began: -1, ps: null, vt: 0, wall: performance.now(), paused: !live, done: false, wp: [{ t: 0, p: IFL.winProb(game) }], log: [], lastPoss: null, flagShown: false });
    };
    reset();
    const H = game.teams[0], A = game.teams[1];
    const logoOf = ref => (ref && ref.logo ? (opts.base || '').replace(/\/+$/, '') + ref.logo : null);

    root.innerHTML = `<div class="stack viewer">
      ${mismatch ? `<div class="banner warn">${ic('info')}<div class="body-s">This replay may differ slightly from the original broadcast (the game engine has changed since it aired).</div></div>` : ''}
      <section class="hero v-board">
        <div class="hero-top"><span class="tag ${live ? 'to' : ''}" id="v-mode">${live ? 'LIVE · SYNCED' : 'REPLAY'}</span><span class="label-m muted" id="v-stage"></span></div>
        <div class="board">
          <div class="side" id="v-away"></div>
          <div class="mid"><div class="title-l" id="v-q"></div><div class="headline-s tnum" id="v-clock"></div><div class="label-l muted" id="v-dd"></div></div>
          <div class="side" id="v-home"></div>
        </div>
      </section>
      <div class="v-field"><svg id="v-svg" viewBox="0 0 1200 533.3" preserveAspectRatio="xMidYMid meet" role="img" aria-label="Football field"></svg><div class="v-banner" id="v-banner"></div><div class="v-final" id="v-final" hidden></div>
        <button class="v-fsbtn sl" id="v-fsbtn" aria-label="Fullscreen" title="Fullscreen">${ic('fullscreen')}</button></div>
      <div class="card filled v-play"><div class="label-m muted" id="v-sit">&nbsp;</div><div class="body-l" id="v-txt">${live ? 'Waiting for the next play…' : 'Press play to kick off.'}</div></div>
      <div class="v-controls" id="v-controls" ${live ? 'hidden' : ''}>
        <button class="btn filled sl" id="v-play">${ic('play_arrow')}<span>Play</span></button>
        <button class="btn tonal sl" id="v-next">${ic('skip_next')}Next play</button>
        <div class="seg v-speed" role="group" aria-label="Speed">${[1, 2, 4, 10].map(n => `<button class="sl ${n === 1 ? 'on' : ''}" data-sp="${n}">${ic('check')}${n}×</button>`).join('')}</div>
        <button class="btn text sl" id="v-end">${ic('fast_forward')}Skip to end</button>
        <span class="label-m muted grow" id="v-eta" style="text-align:right"></span>
      </div>
      <div class="card stack tight v-wp"><div class="row between"><span class="title-s">Win probability</span><span class="label-m muted" id="v-wpl"></span></div>
        <div class="winprob" id="v-wpbar"><span></span><span></span></div><svg id="v-spark" viewBox="0 0 300 60" preserveAspectRatio="none" class="v-spark"></svg></div>
      <div class="stack tight v-logsec"><div class="section-h"><h2>Play-by-play</h2><span class="label-m muted" id="v-count"></span></div><div class="card"><div class="plays" id="v-log"></div></div></div>
    </div>`;
    const $ = s => root.querySelector(s);
    $('#v-stage').textContent = o.label || (o.week && o.week !== 'P' ? 'Week ' + o.week : 'Playoffs');

    /* ---------------- field ---------------- */
    const field = $('#v-svg');
    let L = {};
    function s(tag, attrs, parent) {
      const e = document.createElementNS(SVGNS, tag);
      for (const k in attrs) { const v = attrs[k]; if (typeof v === 'string' && v.startsWith('var(')) e.style.setProperty(k, v); else e.setAttribute(k, v); }
      (parent || field).appendChild(e); return e;
    }
    function drawField() {
      field.innerHTML = '';
      const gr = s('g', {});
      for (let i = 0; i < 20; i++) s('rect', { x: X(i * 5), y: 0, width: 50, height: 533.3, fill: i % 2 ? 'var(--grass-b)' : 'var(--grass-a)' }, gr);
      L.rz = s('rect', { x: 0, y: 0, width: 200, height: 533.3, fill: '#ff2d2d', opacity: 0 }, gr);
      const ez = (x, t, rot) => {
        s('rect', { x, y: 0, width: 100, height: 533.3, fill: t.colors[0] }, gr);
        s('rect', { x: rot < 0 ? x + 88 : x, y: 0, width: 12, height: 533.3, fill: t.colors[1], opacity: .9 }, gr);
        const cx = rot < 0 ? x + 44 : x + 56;
        const tx = s('text', { x: cx, y: 266.6, fill: ink(t.colors[0]), 'font-family': 'Roboto Flex, sans-serif', 'font-weight': 800, 'font-size': 56, 'text-anchor': 'middle', 'dominant-baseline': 'central', 'letter-spacing': 5, transform: `rotate(${rot} ${cx} 266.6)` }, gr);
        tx.textContent = t.name.toUpperCase();
        tx.setAttribute('textLength', Math.min(480, t.name.length * 38)); tx.setAttribute('lengthAdjust', 'spacingAndGlyphs');
      };
      ez(0, H, -90); ez(1100, A, 90);
      const lines = s('g', { stroke: 'rgba(255,255,255,.9)', 'stroke-width': 2 }, gr);
      for (let yd = 0; yd <= 100; yd += 5) s('line', { x1: X(yd), y1: 0, x2: X(yd), y2: 533.3, 'stroke-width': (yd === 0 || yd === 100) ? 4 : 2 }, lines);
      s('rect', { x: 1, y: 1, width: 1198, height: 531.3, fill: 'none', 'stroke-width': 4 }, lines);
      for (let yd = 1; yd < 100; yd++) {
        if (yd % 5 === 0) continue;
        for (const hy of [Y(0.6), Y(23.58), Y(29.05), Y(52)]) s('line', { x1: X(yd), y1: hy, x2: X(yd), y2: hy + 7, 'stroke-width': 1.6 }, lines);
      }
      const nums = s('g', { fill: 'rgba(255,255,255,.9)', 'font-family': 'Roboto Flex, sans-serif', 'font-weight': 700, 'font-size': 44, 'text-anchor': 'middle', 'dominant-baseline': 'central' }, gr);
      for (let yd = 10; yd <= 90; yd += 10) {
        const n = yd <= 50 ? yd : 100 - yd;
        const t1 = s('text', { x: X(yd), y: Y(12) - 10 }, nums); t1.textContent = n;
        const t2 = s('text', { x: X(yd), y: Y(41.3) + 10, transform: `rotate(180 ${X(yd)} ${Y(41.3) + 10})` }, nums); t2.textContent = n;
      }
      const hl = logoOf(opts.homeRef);   // midfield: home logo on a light disc, else a plain disc with the team initials
      s('circle', { cx: X(50), cy: Y(26.65), r: 74, fill: 'rgba(255,255,255,.9)', stroke: H.colors[0], 'stroke-width': 5 }, gr);
      if (hl) s('image', { href: hl, x: X(50) - 58, y: Y(26.65) - 58, width: 116, height: 116, preserveAspectRatio: 'xMidYMid meet' }, gr);
      else { const t = s('text', { x: X(50), y: Y(26.65), 'text-anchor': 'middle', 'dominant-baseline': 'central', 'font-weight': 800, 'font-size': 44, fill: H.colors[0], 'font-family': 'Roboto Flex, sans-serif' }, gr); t.textContent = H.abbr; }
      L.trail = s('g', {});
      L.lines = s('g', {});
      L.los = s('rect', { x: 0, y: 0, width: 4, height: 533.3, fill: '#2f7bff', opacity: 0 }, L.lines);
      L.fd = s('rect', { x: 0, y: 0, width: 4, height: 533.3, fill: '#ffd400', opacity: 0 }, L.lines);
      L.chain = s('g', { opacity: 0 });
      L.chLink = s('line', { x1: 0, x2: 0, y1: 9, y2: 9, stroke: '#ff8a00', 'stroke-width': 2, 'stroke-dasharray': '4 3' }, L.chain);
      L.chA = s('rect', { x: 0, y: 1, width: 5, height: 18, rx: 1.5, fill: '#ff8a00', stroke: '#000', 'stroke-width': .8 }, L.chain);
      L.chB = s('rect', { x: 0, y: 1, width: 5, height: 18, rx: 1.5, fill: '#ff8a00', stroke: '#000', 'stroke-width': .8 }, L.chain);
      L.dbox = s('g', {}, L.chain);
      s('rect', { x: -11, y: 1, width: 22, height: 20, rx: 3, fill: '#ff8a00', stroke: '#000', 'stroke-width': .8 }, L.dbox);
      L.dnum = s('text', { x: 0, y: 15.5, 'text-anchor': 'middle', 'font-weight': 800, 'font-size': 16, fill: '#111', 'font-family': 'Roboto Flex, sans-serif' }, L.dbox);
      L.flag = s('g', { opacity: 0 });
      s('path', { d: 'M0 0 L18 -6 L0 -12 Z', fill: '#f5c400', stroke: '#8a6d00', 'stroke-width': 1.2 }, L.flag);
      s('line', { x1: 0, y1: 0, x2: 0, y2: 10, stroke: '#8a6d00', 'stroke-width': 2 }, L.flag);
      L.shadow = s('ellipse', { cx: 0, cy: 0, rx: 9, ry: 5, fill: 'rgba(0,0,0,.35)' });
      L.tag = s('g', { opacity: 0 });
      L.tagBg = s('rect', { x: 0, y: 0, width: 10, height: 22, rx: 3, fill: 'rgba(0,0,0,.78)' }, L.tag);
      L.tagTx = s('text', { x: 0, y: 15.5, 'text-anchor': 'middle', 'font-weight': 800, 'font-size': 13, fill: '#fff', 'font-family': 'Roboto Flex, sans-serif' }, L.tag);
      L.ball = s('g', {});
      s('ellipse', { cx: 0, cy: 0, rx: 9, ry: 5.6, fill: '#8b4a22', stroke: '#3d1d08', 'stroke-width': 1.2 }, L.ball);
      s('line', { x1: -4, y1: 0, x2: 4, y2: 0, stroke: '#fff', 'stroke-width': 1.2 }, L.ball);
      setBall(50, 26.65, 0, 90);
    }
    function setBall(ax, y, h, ang) {
      const px = X(ax), py = Y(y), sc = 1 + h / 12;
      L.shadow.setAttribute('cx', px); L.shadow.setAttribute('cy', py);
      L.shadow.setAttribute('opacity', h > 0 ? Math.max(.25, 1 - h / 30) : 1);
      L.ball.setAttribute('transform', `translate(${px} ${py - h * 2.2}) rotate(${ang || 0}) scale(${sc})`);
    }
    function setLines(snap) {
      if (snap && snap.phase === 'play' && !snap.over) {
        L.los.setAttribute('x', X(snap.ballAbs) - 2); L.los.setAttribute('opacity', .9);
        const gtg = snap.ball + snap.togo >= 100;
        L.fd.setAttribute('x', X(snap.fdAbs) - 2); L.fd.setAttribute('opacity', gtg ? 0 : .95);
        const xa = X(snap.ballAbs), xb = X(snap.fdAbs);
        L.chain.setAttribute('opacity', 1);
        L.chA.setAttribute('x', xa - 2.5); L.chB.setAttribute('x', xb - 2.5); L.chB.setAttribute('opacity', gtg ? 0 : 1);
        L.chLink.setAttribute('x1', xa); L.chLink.setAttribute('x2', gtg ? xa : xb);
        L.dbox.setAttribute('transform', `translate(${xa} 22)`); L.dnum.textContent = snap.down;
        const inRz = snap.ball >= 80;
        L.rz.setAttribute('opacity', inRz ? .09 : 0);
        if (inRz) L.rz.setAttribute('x', snap.attackRight ? X(80) : X(0));
      } else { L.los.setAttribute('opacity', 0); L.fd.setAttribute('opacity', 0); L.chain.setAttribute('opacity', 0); L.rz.setAttribute('opacity', 0); }
    }
    function restingBall(snap) {
      if (!snap) return;
      if (snap.phase === 'kickoff' || snap.phase === 'freekick') setBall(snap.kickAbs, 26.65, 0, 90);
      else setBall(snap.ballAbs, snap.ballY, 0, 0);
    }
    function showFlag(f) {
      if (!f) { L.flag.setAttribute('opacity', 0); return; }
      L.flag.setAttribute('opacity', 1);
      L.flag.setAttribute('transform', `translate(${X(f.abs)} ${Y(Math.max(3, Math.min(50, (f.y || 26.65) - 3)))})`);
    }
    const trailClear = () => { L.trail.innerHTML = ''; };
    function trailAdd(path, color) {
      if (!path || path.length < 2) return;
      for (let i = 1; i < path.length; i++) {
        const a = path[i - 1], b = path[i], air = b.m === 'air' || b.m === 'kick';
        s('line', { x1: X(a.x), y1: Y(a.y), x2: X(b.x), y2: Y(b.y), stroke: color, 'stroke-width': air ? 2.5 : 4, 'stroke-dasharray': air ? '7 6' : 'none', 'stroke-linecap': 'round', opacity: .85 }, L.trail);
      }
    }
    function drawPathAt(ps, p) {
      const t = Math.max(0, Math.min(1, p)) * ps.total;
      let sg = ps.segs[ps.segs.length - 1], lt = 1;
      for (const g of ps.segs) { if (t <= g.t0 + g.dur) { sg = g; lt = (t - g.t0) / g.dur; break; } }
      lt = Math.max(0, Math.min(1, lt));
      const x = sg.a.x + (sg.b.x - sg.a.x) * lt, y = sg.a.y + (sg.b.y - sg.a.y) * lt;
      const h = (sg.b.m === 'air' || sg.b.m === 'kick') ? (sg.b.h || 2) * 4 * lt * (1 - lt) : 0;
      setBall(x, y, h, Math.atan2(sg.b.y - sg.a.y, sg.b.x - sg.a.x) * 180 / Math.PI);
      const idx = ps.segs.indexOf(sg), inAir = sg.b.m === 'air' || sg.b.m === 'kick';
      const afterThrow = P.airIdx >= 0 && (idx > P.airIdx || (idx === P.airIdx && lt >= 1));
      const name = inAir && lt < 1 ? null : afterThrow ? P.names.after : P.names.before;
      if (!name || p >= 1) { L.tag.setAttribute('opacity', 0); return; }
      if (L.tagTx.textContent !== name) {
        L.tagTx.textContent = name;
        const w = name.length * 8; L.tagBg.setAttribute('width', w + 14); L.tagBg.setAttribute('x', -(w + 14) / 2);
      }
      L.tag.setAttribute('transform', `translate(${X(x)} ${Math.max(4, Y(y) - 42)})`); L.tag.setAttribute('opacity', 1);
    }

    /* ---------------- scoreboard / feed / win probability ---------------- */
    function sideHtml(t, ref, i, snap) {
      const over = snap.over, won = over && snap.score[i] > snap.score[1 - i], lost = over && snap.score[i] < snap.score[1 - i];
      const lg = logoOf(ref);
      return `<span class="badge-t s72" style="--tc:${esc(t.colors[0])};--tc2:${esc(t.colors[1])}">${esc(t.abbr)}${lg ? `<img src="${esc(lg)}" alt="" onerror="this.remove()">` : ''}</span>
        <div class="nm">${esc(t.name)}</div><div class="score tnum ${won ? 'win' : ''} ${lost ? 'lose' : ''}">${snap.score[i]}</div>
        <div class="poss-dot">${!over && snap.poss === i && snap.phase !== 'coin' ? '<span class="ms fill sm">sports_football</span>' : ''}</div>
        <div class="label-s muted">${'●'.repeat(snap.to[i])}${'○'.repeat(Math.max(0, (snap.qtr >= 5 ? 2 : 3) - snap.to[i]))}</div>`;
    }
    function renderBoard(snap) {
      $('#v-away').innerHTML = sideHtml(A, opts.awayRef, 1, snap);
      $('#v-home').innerHTML = sideHtml(H, opts.homeRef, 0, snap);
      $('#v-q').textContent = snap.over ? 'Final' : qLabel(snap.qtr);
      $('#v-clock').textContent = snap.over ? '' : IFL.fmtClock(snap.clock);
      let dd = '';
      if (snap.over) dd = snap.qtr >= 5 ? 'Overtime' : '';
      else if (snap.phase === 'play') { try { dd = game.dd(); } catch (e) { dd = ORD[snap.down] + ' & ' + snap.togo; } }
      else if (snap.phase === 'kickoff' || snap.phase === 'freekick') dd = 'Kickoff';
      else if (snap.phase === 'pat') dd = 'Extra point';
      $('#v-dd').textContent = dd;
    }
    const setClock = c => { $('#v-clock').textContent = IFL.fmtClock(c); };
    function renderWP() {
      const p = P.wp[P.wp.length - 1].p;
      const c1 = A.colors[0], c2 = H.colors[0];
      const bar = $('#v-wpbar').children; bar[0].style.width = (1 - p) * 100 + '%'; bar[0].style.background = c1; bar[1].style.width = p * 100 + '%'; bar[1].style.background = c2;
      $('#v-wpl').textContent = `${A.abbr} ${Math.round((1 - p) * 100)}%  ·  ${Math.round(p * 100)}% ${H.abbr}`;
      const tmax = Math.max(3600, P.wp[P.wp.length - 1].t), pts = P.wp.map(w => `${(w.t / tmax * 300).toFixed(1)},${((1 - w.p) * 60).toFixed(1)}`).join(' ');
      $('#v-spark').innerHTML = `<line x1="0" y1="30" x2="300" y2="30" stroke="var(--outline-variant)" stroke-width="1" vector-effect="non-scaling-stroke"/><polyline points="${pts}" fill="none" stroke="var(--primary)" stroke-width="2" vector-effect="non-scaling-stroke"/>`;
    }
    function renderLog() {
      const items = P.log.slice(-200).reverse();
      $('#v-count').textContent = P.log.length ? P.log.length + ' plays' : '';
      $('#v-log').innerHTML = items.length ? items.map(l => l).join('') : '<div class="center empty" style="padding:16px"><div class="body-m">Plays appear here as they happen.</div></div>';
    }
    function addLog(ev) {
      const sc = ev.scoring != null, labels = [];
      if (ev.banner) labels.push(`<span class="tag ${(ev.tags || []).some(t => t === 'TD' || t === 'FG') ? 'score' : (ev.tags || []).some(t => t === 'TO' || t === 'FLAG') ? 'to' : ''}">${esc(ev.banner)}</span>`);
      if (sc) labels.push(`<span class="tag score">${esc(A.abbr)} ${ev.post.score[1]} – ${ev.post.score[0]} ${esc(H.abbr)}</span>`);
      P.log.push(`<div class="play ${sc ? 'scoring' : ''}"><div class="clk">${qLabel(ev.pre.qtr)}<br>${IFL.fmtClock(ev.pre.clock)}</div><div>${ev.preText ? `<div class="sit">${esc(ev.preText)}</div>` : ''}<div class="tx">${esc(ev.text)}</div>${labels.length ? `<div class="tags">${labels.join('')}</div>` : ''}</div></div>`);
    }
    function showBanner(ev) {
      const el = $('#v-banner');
      el.textContent = ev.banner; el.className = 'v-banner show' + ((ev.tags || []).some(t => t === 'TD' || t === 'FG') ? ' good' : (ev.tags || []).some(t => t === 'TO' || t === 'FLAG') ? ' bad' : '');
      clearTimeout(P.banTimer); P.banTimer = setTimeout(() => { el.className = 'v-banner'; }, 1900 / (live ? 1 : Math.max(1, P.speed / 2)));
    }

    /* ---------------- playback ---------------- */
    function beginEvent(ev) {
      P.began = P.idx; P.flagShown = false; showFlag(null);
      const snap = ev.pre;
      if (ev.kind === 'period' || (ev.kind === 'info' && /coin toss/.test(ev.text))) trailClear();
      if (snap.phase === 'kickoff' || snap.phase === 'freekick') trailClear();
      if (P.lastPoss !== null && snap.phase === 'play' && snap.poss !== P.lastPoss) trailClear();
      setLines(snap.phase === 'play' ? snap : null);
      $('#v-sit').textContent = qLabel(ev.qtr) + ' ' + IFL.fmtClock(ev.clockStart) + (ev.preText ? ' · ' + ev.preText : '');
      P.ps = ev.path && ev.path.length > 1 ? pathSegs(ev.path) : null;
      L.tag.setAttribute('opacity', 0);
      const nm = (ev.text.match(/[A-Z]\. [A-Z][A-Za-z'-]+/g) || []);
      const pick = (ev.text.match(/INTERCEPTED by ([A-Z]\. [A-Z][A-Za-z'-]+)/) || [])[1];
      const passFail = /incomplete|thrown away|spiked/.test(ev.text);
      P.names = { before: nm[0] || null, after: pick || (passFail ? null : nm[1] || null) };
      P.airIdx = P.ps ? P.ps.segs.findIndex(x => x.b.m === 'air' || x.b.m === 'kick') : -1;
      if (['kickoff', 'punt', 'fg', 'pat'].includes(ev.kind)) P.names.before = null;
      if (P.ps) setBall(ev.path[0].x, ev.path[0].y, 0, 0);
    }
    function completeNext(fresh) {
      const ev = game.step();
      if (P.began !== P.idx) beginEvent(ev);
      if (ev.path && ev.path.length > 1) trailAdd(ev.path, visible(game.teams[ev.pre.poss].colors[0]));
      if (ev.flag) showFlag(ev.flag);
      const post = ev.post;
      renderBoard(post);
      setLines(post.phase === 'play' ? post : null);
      if (P.ps && fresh) drawPathAt(P.ps, 1);
      if (!(post.phase === 'play' || post.phase === 'pat' || post.phase === 'kickoff' || post.phase === 'freekick') && P.ps) drawPathAt(P.ps, 1);
      else restingBall(post);
      if (post.phase === 'play') P.lastPoss = post.poss;
      $('#v-txt').textContent = ev.text;
      addLog(ev);
      const otLen = game.playoff ? 900 : 600;
      const tg = post.qtr <= 4 ? (post.qtr - 1) * 900 + (900 - post.clock) : 3600 + (post.qtr - 5) * otLen + (otLen - post.clock);
      P.wp.push({ t: Math.max(tg, P.wp[P.wp.length - 1].t), p: IFL.winProb(game) });
      if (fresh && ev.banner && ev.banner !== 'KICKOFF') showBanner(ev);
      P.idx++;
    }
    function vtime() {
      if (live) return Date.now() + opts.offset - opts.airStart;
      const w = performance.now();
      if (!P.paused) P.vt += (w - P.wall) * P.speed;
      P.wall = w;
      return P.vt;
    }
    function frame() {
      if (P.dead) return;
      const t = vtime();
      let completed = 0;
      while (P.idx < P.N) {
        const sg = tl[P.idx], endAct = sg.start + sg.pre + sg.act;
        if (t < endAct) break;
        completeNext(t - endAct < 1200 * (live ? 1 : Math.max(1, P.speed)));
        completed++;
      }
      if (completed) { renderWP(); renderLog(); }
      if (P.idx >= P.N) { if (!P.done) { P.done = true; onDone(); } return; }
      const sg = tl[P.idx];
      if (t >= sg.start) {
        const ev = full.events[P.idx];
        if (P.began !== P.idx) beginEvent(ev);
        const lt = t - sg.start, sameQ = ev.pre.qtr === ev.post.qtr;
        if (lt < sg.pre) { if (sameQ) setClock(lerp(ev.pre.clock, ev.snapClock, lt / sg.pre)); }
        else {
          const p = sg.act ? (lt - sg.pre) / sg.act : 1;
          if (P.ps) drawPathAt(P.ps, p);
          if (sameQ) setClock(lerp(ev.snapClock, ev.post.clock, p));
          if (ev.flag && !P.flagShown && p > 0.35) { showFlag(ev.flag); P.flagShown = true; }
        }
      }
      if (!live) $('#v-eta').textContent = (P.paused ? 'Paused · ' : '') + fmtIn(Math.max(0, P.total - t) / P.speed) + ' left';
      P.anim = raf(frame);
    }
    const kick = () => { caf(P.anim); P.anim = raf(frame); };
    function onDone() {
      updateButtons();
      $('#v-eta').textContent = '';
      const f = $('#v-final'); const sc = game.score, w = sc[0] > sc[1] ? H : sc[1] > sc[0] ? A : null;
      f.hidden = false; f.innerHTML = `<div class="headline-s">${w ? esc(w.full) + ' win' : 'Final — tie'}</div><div class="title-l tnum">${esc(A.abbr)} ${sc[1]} – ${sc[0]} ${esc(H.abbr)}</div>${live ? '' : `<button class="btn filled sl" id="v-again">${ic('replay')}Watch again</button>`}`;
      const again = $('#v-again'); if (again) again.onclick = restart;
    }
    function restart() {
      reset(); trailClear(); showFlag(null); setLines(null); setBall(50, 26.65, 0, 90);
      $('#v-final').hidden = true; $('#v-txt').textContent = 'Press play to kick off.'; $('#v-sit').innerHTML = '&nbsp;';
      renderBoard(game.snapshot()); renderWP(); renderLog(); updateButtons(); kick();
    }
    function updateButtons() {
      if (live) return;
      const pb = $('#v-play');
      pb.innerHTML = P.done ? `${ic('replay')}<span>Replay</span>` : P.paused ? `${ic('play_arrow')}<span>${P.idx === 0 && P.vt <= 0 ? 'Play' : 'Resume'}</span>` : `${ic('pause')}<span>Pause</span>`;
      $('#v-next').disabled = P.done; $('#v-end').disabled = P.done;
    }
    if (!live) {
      $('#v-play').onclick = () => { if (P.done) return restart(); P.paused = !P.paused; P.wall = performance.now(); updateButtons(); kick(); };
      $('#v-next').onclick = () => {
        if (P.done || P.idx >= P.N) return;
        const sg = tl[P.idx]; P.vt = Math.max(P.vt, sg.start + sg.pre + sg.act); P.wall = performance.now();
        if (P.paused && P.idx + 1 < P.N) P.vt = Math.max(P.vt, tl[P.idx + 1].start);   // paused: finish this play, park at the next one
        updateButtons(); kick();
      };
      $('#v-end').onclick = () => {
        while (P.idx < P.N) completeNext(false);
        P.vt = P.total; renderWP(); renderLog(); P.done = true; onDone(); updateButtons();
      };
      root.querySelectorAll('[data-sp]').forEach(b => b.onclick = () => {
        P.speed = +b.dataset.sp; root.querySelectorAll('[data-sp]').forEach(x => x.classList.toggle('on', x === b));
      });
    }

    /* ---------------- fullscreen ----------------
       Layout goes fullscreen with CSS (works everywhere). On top of that: the browser Fullscreen API where it exists, and in the
       Android app the native bridge rotates to landscape and hides the system bars. The Android back button leaves fullscreen first. */
    const viewerEl = $('.viewer'), native = window.IFLNative;
    let fs = false, usedApi = false;
    function setFs(on) {
      if (on === fs) return;
      fs = on;
      viewerEl.classList.toggle('v-fs', on);
      document.body.style.overflow = on ? 'hidden' : '';
      $('#v-fsbtn').innerHTML = ic(on ? 'fullscreen_exit' : 'fullscreen'); $('#v-fsbtn').setAttribute('aria-label', on ? 'Exit fullscreen' : 'Fullscreen');
      try {
        if (native && native.setFullscreen) native.setFullscreen(on);
        else if (on && document.documentElement.requestFullscreen) { usedApi = true; document.documentElement.requestFullscreen().catch(() => { usedApi = false; }); }
        else if (!on && usedApi && document.fullscreenElement) { usedApi = false; document.exitFullscreen().catch(() => {}); }
      } catch (e) { /* CSS fullscreen still applies */ }
      window.scrollTo(0, 0);
    }
    $('#v-fsbtn').onclick = () => setFs(!fs);
    const onKey = e => { if (e.key === 'Escape' && fs) setFs(false); if ((e.key === 'f' || e.key === 'F') && !/INPUT|TEXTAREA/.test((e.target || {}).tagName || '')) setFs(!fs); };
    const onFsChange = () => { if (fs && usedApi && !document.fullscreenElement) { usedApi = false; setFs(false); } };
    document.addEventListener('keydown', onKey); document.addEventListener('fullscreenchange', onFsChange);
    window.__onBack = () => { if (fs) { setFs(false); return true; } return false; };

    drawField();
    renderBoard(game.snapshot()); renderWP(); renderLog(); updateButtons();
    kick();
    return {
      destroy() {
        P.dead = true; caf(P.anim); clearTimeout(P.banTimer);
        if (fs) setFs(false);
        document.removeEventListener('keydown', onKey); document.removeEventListener('fullscreenchange', onFsChange);
        if (window.__onBack) delete window.__onBack;
        document.body.style.overflow = '';
      },
    };
  }

  window.Viewer = { create };
})();
