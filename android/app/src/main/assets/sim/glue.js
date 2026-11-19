/* Glue between the native app and the IFL game engine (engine.js, MIT, (c) Soms-IFL).
   Runs inside QuickJS on Android (and under Node for tests). Load engine.js first, then this file.
   Both entry points take and return JSON strings so only plain data crosses the native boundary. */
var window = (typeof globalThis !== 'undefined') ? globalThis : this;

/* simulate({home, away, seed, gid, playoff, out, venue, rosters}) -> the whole game, play by play, with the broadcast timeline,
   the win probability after every play and the down-and-distance text, exactly as the website's viewer computes them. */
function simulate(argsJson) {
  var a = JSON.parse(argsJson);
  var gopts = { playoff: !!a.playoff, rosters: a.rosters, out: a.out || [], venue: a.venue || undefined };
  var full = IFL.simulateGame(a.home, a.away, a.seed, gopts);
  var tl = IFL.timeline(full.events, IFL.stageOf({ gid: a.gid, playoff: !!a.playoff }));
  var g = new IFL.Game(a.home, a.away, Object.assign({ seed: a.seed }, gopts));
  var snap0 = g.snapshot(), wp0 = IFL.winProb(g), dd0 = '';
  try { dd0 = g.phase === 'play' ? g.dd() : ''; } catch (e) { dd0 = ''; }
  var evs = [];
  for (var i = 0; i < full.events.length; i++) {
    var ev = full.events[i];
    g.step();
    var dd = '';
    try { dd = g.phase === 'play' ? g.dd() : ''; } catch (e) { dd = ''; }
    evs.push({
      kind: ev.kind, text: ev.text, banner: ev.banner || null, tags: ev.tags || [], preText: ev.preText || '',
      qtr: ev.qtr, clockStart: ev.clockStart, snapClock: ev.snapClock,
      scoring: ev.scoring == null ? null : ev.scoring,
      flag: ev.flag ? { abs: ev.flag.abs, y: ev.flag.y == null ? 26.65 : ev.flag.y } : null,
      path: (ev.path || []).map(function (p) { return { x: p.x, y: p.y, m: p.m || '', h: p.h == null ? 0 : p.h }; }),
      pre: ev.pre, post: ev.post, dd: dd, wp: IFL.winProb(g)
    });
  }
  var teams = g.teams.map(function (t) { return { id: t.id, abbr: t.abbr, name: t.name, city: t.city, full: t.full, colors: t.colors }; });
  return JSON.stringify({ teams: teams, tl: tl, events: evs, snap0: snap0, wp0: wp0, dd0: dd0, score: full.score });
}

/* odds({teams, ratings, played, remaining, playoffs, semis, final, champion, sims}) -> per-team chance to make the playoffs,
   reach the IFL Bowl and win it. Monte Carlo over the unplayed games using the engine's pre-game win model. */
function odds(inputJson) {
  var input = JSON.parse(inputJson);
  var WP = { k: 0.85, home: 4.25, sd: 19.5 };
  var R = input.ratings, N = input.sims || 1000;
  function gauss() { var u = 0, v = 0; while (!u) u = Math.random(); while (!v) v = Math.random(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); }
  function edge(h, a, host) {
    if (host === undefined || host === 'flat') return WP.home;
    if (host === null || host === 'neutral') return 0;
    var v = IFL.getTeam(host), H = IFL.getTeam(h), A = IFL.getTeam(a);
    function adj(t) { return (host === t.id ? 1.5 : 0) - Math.min(2.5, Math.hypot(t.x - v.x, t.z - v.z) / 8000); }
    return WP.home * (adj(H) - adj(A)) / 2.6;
  }
  function margin(h, a, host) { return ((R[h].off - R[a].def) - (R[a].off - R[h].def)) * WP.k + edge(h, a, host); }
  function sample(h, a, host) { return margin(h, a, host) + WP.sd * gauss(); }
  var tally = {};
  input.teams.forEach(function (id) { tally[id] = { playoffs: 0, bowl: 0, champ: 0 }; });
  var base = input.played.map(function (g) { return { home: g.home, away: g.away, playoff: false, result: { score: [g.hs, g.as] } }; });
  function winOf(g) {
    if (g.result) return g.result.hs > g.result.as ? g.home : g.away;
    return sample(g.home, g.away, g.host) > 0 ? g.home : g.away;
  }
  for (var i = 0; i < N; i++) {
    var seeds;
    if (input.playoffs) seeds = input.playoffs.seeds;
    else {
      var games = base.slice();
      for (var j = 0; j < input.remaining.length; j++) {
        var g = input.remaining[j], m = sample(g.home, g.away, g.host), mm = Math.round(m);
        if (mm === 0) mm = m > 0 ? 1 : -1;
        games.push({ home: g.home, away: g.away, playoff: false, result: { score: [Math.max(0, Math.round(22 + mm / 2)), Math.max(0, Math.round(22 - mm / 2))] } });
      }
      seeds = IFL.playoffSeeds(games).map(function (x) { return x.id; });
    }
    seeds.forEach(function (id) { tally[id].playoffs++; });
    var semi = input.semis && input.semis.length === 2 ? input.semis
      : [{ home: seeds[0], away: seeds[3], host: seeds[0] }, { home: seeds[1], away: seeds[2], host: seeds[1] }];
    var w1 = winOf(semi[0]), w2 = winOf(semi[1]);
    var fin = input.final;
    if (!fin) { var s1 = seeds.indexOf(w1), s2 = seeds.indexOf(w2); fin = s1 < s2 ? { home: w1, away: w2, host: null } : { home: w2, away: w1, host: null }; }
    tally[fin.home].bowl++; tally[fin.away].bowl++;
    tally[input.champion || winOf(fin)].champ++;
  }
  var out = {};
  for (var id in tally) out[id] = { playoffs: tally[id].playoffs / N, bowl: tally[id].bowl / N, champ: tally[id].champ / N };
  return JSON.stringify({ teams: out, sims: N });
}
