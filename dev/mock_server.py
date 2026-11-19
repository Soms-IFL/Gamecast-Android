"""Local stand-in for the IFL Gamecast /api/v1 server, for developing the app offline.

Serves the app from ../app and a fake but correctly-shaped /api/v1 (same field names as api.js).
One game is always "on air": a new play appears every few seconds, and /games/live/stream pushes it.

    py dev/mock_server.py            # http://localhost:8099
"""
import json, random, re, sys, time, threading
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse, parse_qs

APP_DIR = Path(__file__).resolve().parent.parent / "app"
# `py dev/mock_server.py 8099 --proxy=https://host` forwards all /api/* calls to a real server instead of faking them
PROXY = next((a.split("=", 1)[1].rstrip("/") for a in sys.argv[1:] if a.startswith("--proxy=")), None)
TEAMS = [
    ("MIS", "Miskauga", "Boulders", "West", ["#5f646c", "#e07b24"]),
    ("LAI", "Laiyford", "Evergreens", "West", ["#1f6b3a", "#d9b23a"]),
    ("GHB", "Greene Hill", "Brawlers", "East", ["#b3202a", "#1b1b1b"]),
    ("PAR", "Parikia", "Poseidons", "Central", ["#0f7c8c", "#0b2545"]),
    ("DEN", "Dennison", "Spears", "East", ["#5b2a86", "#c9c9d4"]),
    ("KYL", "Kyleton", "Polar Bears", "Central", ["#2f7fc1", "#e8f1f8"]),
    ("ASH", "Ashford", "Smokers", "West", ["#3d3936", "#e2552b"]),
    ("ARG", "Argiroupolis", "Silvers", "Central", ["#7d8794", "#1d3f8f"]),
    ("NSO", "New Southington", "Skylines", "West", ["#1f2a5a", "#5bc0eb"]),
    ("NWT", "Newtown", "Knights", "East", ["#141414", "#c9a227"]),
    ("MON", "Montford", "Monarchs", "East", ["#7b1e3a", "#e0b84c"]),
    ("BRV", "Bradyville", "Beavers", "Central", ["#6b3f1d", "#f28c28"]),
]
T = {t[0]: t for t in TEAMS}
POS = [("QB", 2), ("RB", 3), ("WR", 5), ("TE", 2), ("OL", 7), ("DL", 6), ("LB", 4), ("CB", 4), ("S", 3), ("K", 1), ("P", 1)]
FIRST = ["Gage", "Orlando", "Luca", "Easton", "Bennett", "Marcus", "Tyrell", "Jalen", "Brody", "Dante", "Owen", "Malik", "Grant", "Rhett", "Darius", "Kellan"]
LAST = ["Stiles", "Fitzgerald", "Corbin", "Ness", "Kessler", "Hale", "Mercer", "Vance", "Okafor", "Brandt", "Castillo", "Dunmore", "Ellison", "Garrow"]
rng = random.Random(7)
NOW0 = int(time.time() * 1000)
MIN = 60_000


def iso(ms):
    return None if ms is None else time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(ms / 1000)) + ".%03dZ" % (ms % 1000)


def team_ref(i):
    t = T[i]
    return {"id": t[0], "abbr": t[0], "name": f"{t[1]} {t[2]}", "city": t[1], "nickname": t[2], "colors": t[4], "logo": None}


# ---------------------------------------------------------------- rosters / stats
rosters, stats, injuries = {}, {}, {}
for t in TEAMS:
    ros = []
    for pos, n in POS:
        for k in range(1, n + 1):
            f, l = rng.choice(FIRST), rng.choice(LAST)
            pid = f"{t[0]}-{pos}{k}"
            p = {"id": pid, "name": f"{f} {l}", "short": f"{f[0]}. {l}", "position": pos, "number": rng.randint(1, 99), "team": t[0],
                 "rating": rng.randint(52, 88), "age": rng.randint(22, 36), "yearsPro": rng.randint(1, 13)}
            ros.append(p)
            s = {"gp": 3}
            if pos == "QB" and k == 1:
                s.update(pa=rng.randint(70, 110), pc=rng.randint(45, 75), py=rng.randint(450, 900), ptd=rng.randint(3, 9), pint=rng.randint(0, 4), ra=rng.randint(3, 12), ry=rng.randint(0, 60))
            elif pos == "RB":
                s.update(ra=rng.randint(10, 60), ry=rng.randint(40, 300), rlng=rng.randint(10, 50), rtd=rng.randint(0, 3), rec=rng.randint(0, 9), recy=rng.randint(0, 80))
            elif pos in ("WR", "TE"):
                s.update(tgt=rng.randint(5, 30), rec=rng.randint(3, 22), recy=rng.randint(30, 350), reclng=rng.randint(12, 60), rectd=rng.randint(0, 3))
            elif pos in ("DL", "LB", "CB", "S"):
                s.update(tk=rng.randint(2, 30), sacks=rng.randint(0, 5) if pos in ("DL", "LB") else 0, ints=rng.randint(0, 2) if pos in ("CB", "S") else 0, pd=rng.randint(0, 4))
            elif pos == "K":
                s.update(fga=rng.randint(3, 9), fgm=rng.randint(2, 8), fglng=rng.randint(30, 54), xpa=rng.randint(4, 12), xpm=rng.randint(4, 11))
            elif pos == "P":
                s.update(pn=rng.randint(6, 16), pny=rng.randint(250, 700))
            else:
                s = None
            if s:
                stats[pid] = s
    rosters[t[0]] = ros
for pid in ("MIS-WR2", "KYL-LB1", "GHB-RB1"):
    injuries[pid] = {"part": rng.choice(["Hamstring", "Knee", "Ankle"]), "gamesLeft": rng.randint(1, 4), "seasonEnding": False}
injuries["LAI-OL3"] = {"part": "ACL", "gamesLeft": None, "seasonEnding": True}


def stat_line(pid):
    return dict(stats[pid]) if pid in stats else None


def player_ref(p):
    return {k: p[k] for k in ("id", "name", "short", "position", "number", "team")}


# ---------------------------------------------------------------- schedule
GAMES = []  # dicts with gid, week, home, away, start, dur, score, status resolved by time
ids = [t[0] for t in TEAMS]
# 14 weeks x 6 games; week 1..3 already played, week 4 is "now", the rest upcoming. One game every 10 min.
live_week = 4
for w in range(1, 15):
    order = ids[:]
    random.Random(w).shuffle(order)
    for g in range(6):
        h, a = order[2 * g], order[2 * g + 1]
        if w % 2 == 0:
            h, a = a, h
        idx = (w - live_week) * 6 + g
        start = NOW0 + idx * 12 * MIN - 6 * MIN  # g==0 of live week started 6 minutes ago
        GAMES.append({"gid": f"W{w}G{g + 1}", "week": w, "home": h, "away": a, "start": start, "dur": 9 * MIN, "seed": w * 31 + g})
for k, (gid, label, h, a, off) in enumerate([("P-SF1", "Semifinal 1", "GHB", "MIS", 100), ("P-SF2", "Semifinal 2", "KYL", "PAR", 112), ("P-F", "IFL Bowl", "GHB", "KYL", 140)]):
    GAMES.append({"gid": gid, "week": "P", "label": label, "playoff": True, "home": h, "away": a, "start": NOW0 + (60 + off) * 6 * MIN, "dur": 12 * MIN, "seed": 900 + k})


def status_of(g, now):
    return "scheduled" if now < g["start"] else ("live" if now < g["start"] + g["dur"] else "final")


# ---------------------------------------------------------------- play generator (deterministic per game)
def plays_for(g):
    if "plays" in g:
        return g["plays"]
    r = random.Random(g["seed"])
    plays, score, qtr, clock, poss = [], [0, 0], 1, 900, r.randint(0, 1)
    n = 0
    total = 118
    for i in range(total):
        qtr = min(4, 1 + i * 4 // total)
        clock = max(0, 900 - ((i * 4 % total) * 900 // total) - r.randint(0, 12))
        kind = r.choice(["run", "run", "pass", "pass", "pass", "punt", "fg", "penalty", "timeout"])
        down, togo, yl = r.randint(1, 4), r.randint(1, 12), r.randint(10, 49)
        ab = [g["home"], g["away"]][poss]
        scoring = None
        tags = []
        banner = None
        pl = rosters[ab]
        a, b = r.choice(pl)["short"], r.choice(pl)["short"]
        if kind == "run":
            y = r.randint(-2, 14)
            text = f"{a} up the middle to {g['away'] if poss == 0 else g['home']} {yl} for {y} yards"
        elif kind == "pass":
            y = r.randint(0, 28)
            text = f"{a} pass complete to {b} for {y} yards" if r.random() > 0.2 else f"{a} pass incomplete intended for {b}"
            if r.random() < 0.05:
                text = f"{a} pass INTERCEPTED"; tags = ["TO"]; banner = "INTERCEPTION"
        elif kind == "punt":
            text = f"{a} punts 44 yards"
        elif kind == "fg":
            ok = r.random() > 0.25
            text = f"{a} {r.randint(24, 52)} yard field goal is {'GOOD' if ok else 'NO GOOD'}."
            if ok:
                scoring = poss; score[poss] += 3; tags = ["FG"]; banner = "FIELD GOAL"
        elif kind == "penalty":
            text = f"FLAG: holding on {ab}, 10 yards"; tags = ["FLAG"]; banner = "FLAG"
        else:
            text = f"Timeout by {ab}"; tags = ["TIMEOUT"]
        if kind in ("run", "pass") and r.random() < 0.07:
            text += " for a TOUCHDOWN!"; scoring = poss; score[poss] += 7; tags = ["TD"]; banner = "TOUCHDOWN"
        if r.random() < 0.15:
            poss ^= 1
        plays.append({"n": i + 1, "kind": kind, "quarter": qtr, "clock": f"{clock // 60}:{clock % 60:02d}",
                      "situation": f"{down}{['st', 'nd', 'rd', 'th'][down - 1]} & {togo} at {ab} {yl}", "text": text,
                      "possession": ab, "tags": tags, "banner": banner,
                      "scoringTeam": None if scoring is None else [g["home"], g["away"]][scoring],
                      "score": {"home": score[0], "away": score[1]}})
    g["plays"] = plays
    return plays


def aired(g, now):
    st = status_of(g, now)
    if st == "scheduled":
        return 0
    p = plays_for(g)
    if st == "final":
        return len(p)
    return max(0, min(len(p), int((now - g["start"]) / g["dur"] * len(p))))


def score_of(g, now):
    n = aired(g, now)
    if n == 0:
        return None, None
    s = plays_for(g)[n - 1]["score"]
    return s["home"], s["away"]


def summary(g, now):
    st = status_of(g, now)
    h, a = score_of(g, now)
    out = {"id": g["gid"], "season": 3, "week": g["week"], "label": g.get("label"), "stage": "bowl" if g["gid"] == "P-F" else ("semi" if g.get("playoff") else "reg"),
           "playoff": bool(g.get("playoff")), "status": st, "startsAt": iso(g["start"]), "startsAtMs": g["start"],
           "home": {**team_ref(g["home"]), "score": h}, "away": {**team_ref(g["away"]), "score": a}, "overtime": False,
           "venue": {"name": f"{T[g['home']][1]} Field", "town": T[g["home"]][1], "host": T[g["home"]][0]}}
    if st == "final":
        out["winner"] = g["home"] if h > a else (g["away"] if a > h else None)
    return out


def box_for(g, n):
    plays = plays_for(g)[:n]
    side = {}
    for k, ti in (("home", g["home"]), ("away", g["away"])):
        frac = max(0.05, n / 118)
        players = []
        for p in rosters[ti]:
            if p["id"] in stats and p["position"] in ("QB", "RB", "WR", "TE", "DL", "LB", "CB", "S", "K", "P"):
                s = {kk: int(vv * frac / 1.5) if kk != "gp" else None for kk, vv in stats[p["id"]].items()}
                s = {kk: vv for kk, vv in s.items() if vv}
                if s:
                    players.append({**player_ref(p), "stats": s})
        side[k] = {"team": team_ref(ti), "players": players,
                   "stats": {"fd": int(22 * frac), "plays": int(64 * frac), "yds": int(360 * frac), "py": int(240 * frac), "ry": int(120 * frac), "pen": int(6 * frac), "peny": int(48 * frac),
                             "to": int(2 * frac), "top": int(1800 * frac), "d3a": int(12 * frac), "d3c": int(5 * frac), "d4a": 1, "d4c": 0, "sk": int(3 * frac), "sky": int(20 * frac)}}
    return side


def quarters(g, n):
    plays = plays_for(g)[:n]
    qh, qa = [0, 0, 0, 0], [0, 0, 0, 0]
    prev = {"home": 0, "away": 0}
    for p in plays:
        q = min(4, p["quarter"]) - 1
        qh[q] += p["score"]["home"] - prev["home"]; qa[q] += p["score"]["away"] - prev["away"]
        prev = p["score"]
    return {"home": qh, "away": qa}


def detail(g, now, nplays=0):
    st = status_of(g, now)
    out = {"game": summary(g, now), "inactive": {"home": [player_ref(p) for p in rosters[g["home"]] if p["id"] in injuries][:2], "away": [player_ref(p) for p in rosters[g["away"]] if p["id"] in injuries][:1]},
           "venue": summary(g, now)["venue"]}
    if st == "scheduled":
        out["winProbability"] = {"home": round(0.5 + (hash(g["gid"]) % 30 - 15) / 100, 3), "basis": "pregame"}
        return out
    n = aired(g, now)
    plays = plays_for(g)
    out["quarters"] = quarters(g, n)
    out["box"] = box_for(g, n)
    out["progress"] = {"playsAired": n, "playsTotal": len(plays) if st == "final" else None}
    if st == "live":
        last = plays[n - 1] if n else plays[0]
        qn = last["quarter"]
        out["situation"] = {"quarter": qn, "clock": last["clock"], "clockSeconds": 0, "down": 2, "toGo": 7, "possession": last["possession"], "phase": "play", "timeouts": {"home": 2, "away": 3}}
        sc = last["score"]
        out["winProbability"] = {"home": round(min(0.97, max(0.03, 0.5 + (sc["home"] - sc["away"]) / 28)), 3), "basis": "live"}
        if nplays:
            out["recentPlays"] = plays[max(0, n - nplays):n]
    else:
        out["scoring"] = [p for p in plays if p["scoringTeam"]]
    return out


def record_rows(now):
    recs = {i: {"w": 0, "l": 0, "pf": 0, "pa": 0} for i in ids}
    for g in GAMES:
        if g.get("playoff") or status_of(g, now) != "final":
            continue
        h, a = score_of(g, now)
        recs[g["home"]]["pf"] += h; recs[g["home"]]["pa"] += a; recs[g["away"]]["pf"] += a; recs[g["away"]]["pa"] += h
        win, lose = (g["home"], g["away"]) if h >= a else (g["away"], g["home"])
        recs[win]["w"] += 1; recs[lose]["l"] += 1
    rows = []
    for i, r in recs.items():
        gp = r["w"] + r["l"]
        rows.append({"team": team_ref(i), "division": T[i][3], "wins": r["w"], "losses": r["l"], "ties": 0, "pct": round(r["w"] / gp, 3) if gp else 0.0,
                     "pointsFor": r["pf"], "pointsAgainst": r["pa"], "differential": r["pf"] - r["pa"], "home": f"{r['w']//2}-{r['l']//2}", "away": f"{r['w'] - r['w']//2}-{r['l'] - r['l']//2}",
                     "divisionRecord": "1-0", "streak": "W1" if r["w"] else "L1"})
    rows.sort(key=lambda x: (-x["pct"], -x["differential"]))
    return rows


def standings(now):
    rows = record_rows(now)
    overall = [{"rank": i + 1, **r} for i, r in enumerate(rows)]
    divs = []
    for d in ("East", "Central", "West"):
        divs.append({"id": d.lower(), "name": d, "teams": [{"rank": i + 1, **r} for i, r in enumerate([x for x in rows if x["division"] == d])]})
    return {"season": 3, "regularSeasonGamesPlayed": sum(1 for g in GAMES if not g.get("playoff") and status_of(g, now) == "final"), "regularSeasonGames": 84,
            "divisions": divs, "overall": overall, "playoffs": None, "champion": None}


CATS = {"passingYards": ("Passing Yards", "py"), "passingTD": ("Passing TD", "ptd"), "rushingYards": ("Rushing Yards", "ry"), "receivingYards": ("Receiving Yards", "recy"),
        "receptions": ("Receptions", "rec"), "sacks": ("Sacks", "sacks"), "interceptions": ("Interceptions", "ints"), "tackles": ("Tackles", "tk"), "fieldGoals": ("Field Goals Made", "fgm")}


def leaders(limit, want):
    out = {}
    for key, (label, sk) in CATS.items():
        if want and key != want:
            continue
        rows = []
        for pid, s in stats.items():
            if s.get(sk):
                p = next(x for x in rosters[pid.split("-")[0]] if x["id"] == pid)
                rows.append((s[sk], p, s.get("gp", 0)))
        rows.sort(key=lambda x: -x[0])
        out[key] = {"label": label, "leaders": [{"rank": i + 1, "value": v, **player_ref(p), "teamInfo": team_ref(p["team"]), "gamesPlayed": gp} for i, (v, p, gp) in enumerate(rows[:limit])]}
    return {"season": 3, "categories": out}


def ratings(i):
    r = random.Random(i)
    off, de = r.randint(62, 82), r.randint(62, 82)
    return {"ovr": round(off * .55 + de * .45), "off": off, "def": de}


def team_full(i, now):
    row = next(r for r in record_rows(now) if r["team"]["id"] == i)
    sched = sorted([g for g in GAMES if i in (g["home"], g["away"])], key=lambda g: g["start"])
    roster = [{**p, "injury": injuries.get(p["id"]), "stats": stat_line(p["id"])} for p in rosters[i]]
    return {**team_ref(i), "season": 3, "division": {"id": T[i][3].lower(), "name": T[i][3]}, "rating": ratings(i),
            "stadium": {"name": f"{T[i][1]} Field", "capacity": 42000}, "record": row, "roster": roster, "schedule": [summary(g, now) for g in sched]}


def find_game(gid):
    for g in GAMES:
        if g["gid"].upper() == gid.upper():
            return g
    return None


def live_game(now):
    for g in GAMES:
        if status_of(g, now) == "live":
            return g
    return None


# ---------------------------------------------------------------- http
class Err(Exception):
    def __init__(self, code, msg):
        self.code, self.msg = code, msg


def route(path, q, now):
    m = None
    if path == "":
        return {"name": "IFL Gamecast API (mock)", "version": 1}
    if path == "status":
        live = live_game(now)
        nxt = sorted([g for g in GAMES if g["start"] > now], key=lambda g: g["start"])
        return {"now": iso(now), "nowMs": now, "timezone": "America/New_York", "engineVersion": 5,
                "season": {"number": 3, "champion": None, "gamesPlayed": sum(1 for g in GAMES if status_of(g, now) == "final"), "gamesTotal": len(GAMES), "playoffsStarted": False, "done": False},
                "paused": False, "live": summary(live, now) if live else None, "next": summary(nxt[0], now) if nxt else None, "nextSeasonStartsAt": None, "estimatedSeasonEnd": iso(now + 3 * 86400_000)}
    if path == "teams":
        rows = {r["team"]["id"]: r for r in record_rows(now)}
        return {"season": 3, "teams": [{**team_ref(i), "division": {"id": T[i][3].lower(), "name": T[i][3]}, "record": f"{rows[i]['wins']}-{rows[i]['losses']}", "rating": ratings(i)} for i in ids]}
    m = re.fullmatch(r"teams/([A-Za-z]{3})", path)
    if m:
        i = m[1].upper()
        if i not in T:
            raise Err(404, f"Team {i} did not play in season 3.")
        return team_full(i, now)
    if path == "standings":
        return standings(now)
    if path == "games":
        lim, off = int(q.get("limit", [50])[0]), int(q.get("offset", [0])[0])
        lst = [g for g in GAMES if (not q.get("week") or str(g["week"]).upper() == q["week"][0].upper()) and (not q.get("team") or q["team"][0].upper() in (g["home"], g["away"]))
               and (not q.get("status") or status_of(g, now) == q["status"][0]) and (not q.get("playoff") or bool(g.get("playoff")) == (q["playoff"][0] == "true"))]
        lst.sort(key=lambda g: (g["start"], g["gid"]))
        return {"season": 3, "total": len(lst), "offset": off, "limit": lim, "games": [summary(g, now) for g in lst[off:off + lim]]}
    if path == "games/live":
        g = live_game(now)
        if not g:
            return {"live": False, "game": None}
        return {"live": True, **detail(g, now, int(q.get("plays", [5])[0]))}
    m = re.fullmatch(r"games/([A-Za-z0-9_-]+)", path)
    if m:
        g = find_game(m[1])
        if not g:
            raise Err(404, f'No game "{m[1]}" in season 3.')
        return detail(g, now)
    m = re.fullmatch(r"games/([A-Za-z0-9_-]+)/plays", path)
    if m:
        g = find_game(m[1])
        if not g:
            raise Err(404, f'No game "{m[1]}" in season 3.')
        n = aired(g, now); since = int(q.get("since", [0])[0])
        return {"game": g["gid"], "season": 3, "status": status_of(g, now), "total": n, "since": since, "next": max(since, n), "plays": plays_for(g)[since:n] if n else []}
    m = re.fullmatch(r"players/([A-Za-z0-9_-]+)", path)
    if m:
        pid = m[1].upper()
        for ti in ids:
            for p in rosters[ti]:
                if p["id"] == pid:
                    return {**player_ref(p), "season": 3, "team": team_ref(ti), "rating": p["rating"], "age": p["age"], "yearsPro": p["yearsPro"], "injury": injuries.get(pid), "seasonStats": stat_line(pid)}
        raise Err(404, f'No player "{m[1]}" in season 3.')
    if path == "leaders":
        return leaders(int(q.get("limit", [5])[0]), q.get("category", [None])[0])
    if path == "seasons":
        return {"seasons": [{"number": 3, "current": True, "done": False, "champion": None, "runnerUp": None, "startedAt": iso(NOW0 - 86400_000), "endedAt": None},
                            {"number": 2, "current": False, "done": True, "champion": team_ref("KYL"), "runnerUp": team_ref("GHB"), "startedAt": iso(NOW0 - 9 * 86400_000), "endedAt": iso(NOW0 - 3 * 86400_000)},
                            {"number": 1, "current": False, "done": True, "champion": team_ref("MIS"), "runnerUp": team_ref("PAR"), "startedAt": iso(NOW0 - 20 * 86400_000), "endedAt": iso(NOW0 - 14 * 86400_000)}]}
    m = re.fullmatch(r"seasons/(\d+)", path)
    if m:
        n = int(m[1])
        std = standings(now)
        bowl = find_game("P-F")
        return {"season": n, "current": n == 3, "champion": team_ref("KYL") if n == 2 else None, "teams": [team_ref(i) for i in ids], "iflBowl": summary(bowl, now), "standings": std["divisions"], "playoffs": None,
                "links": {}}
    raise Err(404, "Not found")


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(APP_DIR), **kw)

    def log_message(self, *a):
        pass

    def end_headers(self):
        self.send_header("Cache-Control", "no-store") if not self.path.startswith("/api/") else None
        super().end_headers()

    def cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")

    def do_OPTIONS(self):
        self.send_response(204); self.cors(); self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS"); self.end_headers()

    def send_json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code); self.cors()
        self.send_header("Content-Type", "application/json; charset=utf-8"); self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path == "/android-sim":   # the app with a fake window.IFLNative (records calls in window.__calls)
            stub = """<script>window.__open=0;window.__opened=0;(function(){const E=window.EventSource;window.EventSource=function(u,o){const e=new E(u,o);window.__open++;window.__opened++;const c=e.close.bind(e);let done=false;e.close=function(){if(!done){done=true;window.__open--}c()};return e};window.EventSource.prototype=E.prototype})();window.__calls=[];window.__perm=false;window.IFLNative={
              hasPermission(){return window.__perm},
              requestPermission(){window.__calls.push(['requestPermission']);setTimeout(()=>{window.__perm=true;window.onNativePerm&&window.onNativePerm(true)},50)},
              schedule(b,j){window.__calls.push(['schedule',b,JSON.parse(j)])},
              cancelAll(){window.__calls.push(['cancelAll'])},
              canExact(){return false},openAlarmSettings(){window.__calls.push(['openAlarmSettings'])}};</script>"""
            html = (APP_DIR / "index.html").read_text(encoding="utf-8").replace('<script src="app.js">', stub + '<script src="app.js">').replace("<head>", '<head><base href="/">')
            data = html.encode()
            self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
            return
        if PROXY and (u.path.startswith("/api/") or u.path.startswith("/logos/")):   # --proxy: forward to the real server (acts like being hosted on it)
            if u.path.endswith("/stream"):
                self.send_response(404); self.end_headers(); return
            import urllib.request
            ctype = "application/json"
            try:
                with urllib.request.urlopen(urllib.request.Request(PROXY + self.path, headers={"User-Agent": "ifl-dev-proxy"}), timeout=30) as r:
                    body = r.read(); code = r.status; ctype = r.headers.get("Content-Type", ctype)
            except Exception as e:
                body, code = json.dumps({"error": str(e)}).encode(), 502
            self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
            return
        if not u.path.startswith("/api/v1"):
            return super().do_GET()
        sub = u.path[len("/api/v1"):].strip("/")
        if sub == "games/live/stream" or re.fullmatch(r"games/[A-Za-z0-9_-]+/stream", sub):
            return self.stream(sub)
        try:
            self.send_json(200, route(sub, parse_qs(u.query), int(time.time() * 1000)))
        except Err as e:
            self.send_json(e.code, {"error": e.msg})

    def stream(self, sub):
        self.send_response(200); self.cors()
        self.send_header("Content-Type", "text/event-stream"); self.send_header("Cache-Control", "no-store"); self.end_headers()

        def ev(name, data, eid=None):
            s = (f"id: {eid}\n" if eid else "") + f"event: {name}\ndata: {json.dumps(data)}\n\n"
            self.wfile.write(s.encode()); self.wfile.flush()
        try:
            sent, cur = None, None
            while True:
                now = int(time.time() * 1000)
                g = live_game(now) if sub == "games/live/stream" else find_game(sub.split("/")[1])
                if g is None or status_of(g, now) == "scheduled":
                    nxt = sorted([x for x in GAMES if x["start"] > now], key=lambda x: x["start"])
                    ev("idle", {"now": iso(now), "next": summary(nxt[0], now) if nxt else None})
                    time.sleep(5); continue
                if cur != g["gid"]:
                    cur, sent = g["gid"], aired(g, now)
                    ev("state", {**detail(g, now), "progress": {"playsAired": sent}})
                n = aired(g, now)
                while sent < n:
                    p = plays_for(g)[sent]; sent += 1
                    ev("play", p, f"{g['gid']}:{p['n']}")
                    if p["scoringTeam"]:
                        ev("score", {"play": p["n"], "home": p["score"]["home"], "away": p["score"]["away"], "scoringTeam": p["scoringTeam"]})
                if status_of(g, now) == "final":
                    s = summary(g, now)
                    ev("final", {"game": g["gid"], "home": s["home"]["score"], "away": s["away"]["score"], "winner": s.get("winner"), "overtime": False, "plays": sent})
                    if sub != "games/live/stream":
                        return
                    time.sleep(5); continue
                self.wfile.write(b": ping\n\n"); self.wfile.flush()
                time.sleep(1)
        except (BrokenPipeError, ConnectionResetError, OSError):
            return


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    port = int(args[0]) if args else 8099
    print(f"mock IFL server on http://localhost:{port}  (app: {APP_DIR})")
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
