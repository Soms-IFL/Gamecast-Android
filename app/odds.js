/* IFL Gamecast — playoff odds.
   Monte Carlo over the rest of the season: every unplayed regular-season game is sampled from the engine's own pre-game win model
   (expected margin from team offence/defence ratings + home edge, normal with sd 19.5), then the league's real standings rules
   (IFL.playoffSeeds, with tiebreakers) pick the four playoff teams, and the semifinals and the IFL Bowl are sampled the same way.
   Needs vendor/engine.js. Window.Odds.compute(input) -> { teams: { ID: { playoffs, bowl, champ } }, sims }. */
(function () {
  'use strict';
  const WP = { k: 0.85, home: 4.25, sd: 19.5 };               // same constants as the engine's winProb()
  function normCdf(x) { const t = 1 / (1 + 0.3275911 * Math.abs(x) / Math.SQRT2); const y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x / 2); return x >= 0 ? 0.5 * (1 + y) : 0.5 * (1 - y); }
  function gauss() { let u = 0, v = 0; while (!u) u = Math.random(); while (!v) v = Math.random(); return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v); }

  /* input: {
       teams: [ids], ratings: {ID:{off,def}},
       played:   [{home, away, hs, as}]            finished regular-season games
       remaining:[{home, away}]                    regular-season games still to play (live games count as unplayed)
       playoffs: null | { seeds:[ids in seed order] }
       semis:    [{home, away, result?:{hs,as}}]   when the bracket already exists
       final:    null | {home, away, result?:{hs,as}}
       champion: null | id, sims? } */
  async function compute(input) {
    const IFL = window.IFL, N = input.sims || 1500;
    const R = input.ratings;
    // Home edge as the engine computes it: the host gets +1.5, and each team loses travel/8000 (max 2.5) rating points for the
    // distance to the venue; the edge is the difference, scaled. host === undefined: flat edge (old rule); null: neutral/unknown venue.
    const edge = (h, a, host) => {
      if (host === undefined) return WP.home;
      if (host === null) return 0;
      const v = IFL.getTeam(host), H = IFL.getTeam(h), A = IFL.getTeam(a);
      const adj = t => (host === t.id ? 1.5 : 0) - Math.min(2.5, Math.hypot(t.x - v.x, t.z - v.z) / 8000);
      return WP.home * (adj(H) - adj(A)) / 2.6;
    };
    const margin = (h, a, host) => ((R[h].off - R[a].def) - (R[a].off - R[h].def)) * WP.k + edge(h, a, host);
    const sample = (h, a, host) => margin(h, a, host) + WP.sd * gauss();      // home margin
    const tally = {}; input.teams.forEach(id => { tally[id] = { playoffs: 0, bowl: 0, champ: 0 }; });
    const base = input.played.map(g => ({ home: g.home, away: g.away, playoff: false, result: { score: [g.hs, g.as] } }));
    const decided = r => (r.hs > r.as ? 0 : 1);

    for (let i = 0; i < N; i++) {
      let seeds;
      if (input.playoffs) seeds = input.playoffs.seeds;
      else {
        const games = base.slice();
        for (const g of input.remaining) {
          const m = sample(g.home, g.away, g.host);
          let mm = Math.round(m); if (mm === 0) mm = m > 0 ? 1 : -1;               // no ties
          games.push({ home: g.home, away: g.away, playoff: false, result: { score: [Math.max(0, Math.round(22 + mm / 2)), Math.max(0, Math.round(22 - mm / 2))] } });
        }
        seeds = IFL.playoffSeeds(games).map(x => x.id);
      }
      seeds.forEach(id => { tally[id].playoffs++; });

      const winOf = g => {
        if (g.result) return decided(g.result) === 0 ? g.home : g.away;
        return sample(g.home, g.away, g.host) > 0 ? g.home : g.away;
      };
      // semifinals are hosted by the better seed; the Bowl's venue isn't known until it's scheduled, so it is treated as neutral
      const semi = input.semis && input.semis.length === 2 ? input.semis : [{ home: seeds[0], away: seeds[3], host: seeds[0] }, { home: seeds[1], away: seeds[2], host: seeds[1] }];
      const w1 = winOf(semi[0]), w2 = winOf(semi[1]);
      let fin = input.final;
      if (!fin) { const si = id => seeds.indexOf(id); fin = si(w1) < si(w2) ? { home: w1, away: w2, host: null } : { home: w2, away: w1, host: null }; }
      tally[fin.home].bowl++; tally[fin.away].bowl++;
      const champ = input.champion || winOf(fin);
      tally[champ].champ++;
      if (i % 250 === 249) await new Promise(r => setTimeout(r, 0));                // keep the UI responsive
    }
    const out = {};
    for (const id in tally) out[id] = { playoffs: tally[id].playoffs / N, bowl: tally[id].bowl / N, champ: tally[id].champ / N };
    return { teams: out, sims: N };
  }
  // pre-game home win probability from ratings (same model as above; exposed for checks)
  function pHome(ratings, h, a, host) {
    const IFL = window.IFL, R = ratings;
    let e = WP.home;
    if (host !== undefined) {
      if (host === null) e = 0;
      else { const v = IFL.getTeam(host), H = IFL.getTeam(h), A = IFL.getTeam(a); const adj = t => (host === t.id ? 1.5 : 0) - Math.min(2.5, Math.hypot(t.x - v.x, t.z - v.z) / 8000); e = WP.home * (adj(H) - adj(A)) / 2.6; }
    }
    return normCdf((((R[h].off - R[a].def) - (R[a].off - R[h].def)) * WP.k + e) / WP.sd);
  }
  window.Odds = { compute, pHome };
})();
