/* LanWoW web — stessa app Android in versione PWA. */
"use strict";

const VERSION = "2.10";
const REPO = "zeo93/LanWoW";
const REGIONS = ["eu", "us", "kr", "tw"];

// Credenziali WarcraftLogs dedicate alla web app (API gratuita a sola lettura)
const WCL_CLIENT_ID = "019f42cb-052d-72b1-ab62-4995e8a4127c";
const WCL_CLIENT_SECRET = "693GmRQris7L2FO1gsA37hhEEPQcrAsYy8spRk4U";

// [etichetta, metrica, byBracket] — byBracket = parse per livello di chiave
const METRICS = [
  ["Predefinita", null, false], ["DPS", "dps", false], ["HPS", "hps", false],
  ["Boss DPS", "bossdps", false], ["Punteggio M+", "playerscore", false],
  ["DPS per livello chiave", "dps", true], ["HPS per livello chiave", "hps", true],
];

const view = document.getElementById("view");
const headerTitle = document.getElementById("header-title");
const btnBack = document.getElementById("btn-back");

// ------------------------------------------------------------------ util

const $ = (sel, el) => (el || document).querySelector(sel);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g,
  (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const fmt0 = (n) => Number(n).toLocaleString("it-IT", { maximumFractionDigits: 0 });
const fmt1 = (n) => Number(n).toLocaleString("it-IT",
  { minimumFractionDigits: 1, maximumFractionDigits: 1 });

function realmSlug(realm) {
  return realm.trim().normalize("NFD").replace(/\p{M}/gu, "")
    .replace(/['’]/g, "").toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

function prettify(slug) {
  return slug.split("-").filter(Boolean)
    .map((w) => w[0].toUpperCase() + w.slice(1)).join(" ");
}

const CLASS_COLORS = {
  "warrior": "#C69B6D", "paladin": "#F48CBA", "hunter": "#AAD372",
  "rogue": "#FFF468", "priest": "#FFFFFF", "death knight": "#C41E3A",
  "shaman": "#0070DD", "mage": "#3FC7EB", "warlock": "#8788EE",
  "monk": "#00FF98", "druid": "#FF7C0A", "demon hunter": "#A330C9",
  "evoker": "#33937F",
};
const classColor = (cls) => CLASS_COLORS[(cls || "").toLowerCase()] || "#FFFFFF";

const CLASS_IT = {
  "warrior": ["Guerriero", "Guerrieri"], "paladin": ["Paladino", "Paladini"],
  "hunter": ["Cacciatore", "Cacciatori"], "rogue": ["Ladro", "Ladri"],
  "priest": ["Sacerdote", "Sacerdoti"],
  "death knight": ["Cavaliere della Morte", "Cavalieri della Morte"],
  "shaman": ["Sciamano", "Sciamani"], "mage": ["Mago", "Maghi"],
  "warlock": ["Stregone", "Stregoni"], "monk": ["Monaco", "Monaci"],
  "druid": ["Druido", "Druidi"],
  "demon hunter": ["Cacciatore di Demoni", "Cacciatori di Demoni"],
  "evoker": ["Evocatore", "Evocatori"],
};
const classIt = (cls, plural) =>
  (CLASS_IT[(cls || "").toLowerCase()] || [cls, cls])[plural ? 1 : 0];

function rankColor(rank) {
  if (rank <= 10) return "#E5CC80";
  if (rank <= 100) return "#A335EE";
  if (rank <= 5000) return "#0070DD";
  return "#1EFF00";
}

function parseColor(pct) {
  if (pct >= 100) return "#E5CC80";
  if (pct >= 99) return "#E268A8";
  if (pct >= 95) return "#FF8000";
  if (pct >= 75) return "#A335EE";
  if (pct >= 50) return "#0070DD";
  if (pct >= 25) return "#1EFF00";
  return "#9D9D9D";
}

async function getJson(url, opts) {
  const res = await fetch(url, opts);
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error(body && body.message ? body.message : "HTTP " + res.status);
  }
  return body;
}

const store = {
  get(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key)) ?? fallback; }
    catch { return fallback; }
  },
  set(key, value) { localStorage.setItem(key, JSON.stringify(value)); },
};

// ------------------------------------------------------------------ API raider.io

const rio = {
  search: (region, term) =>
    getJson(`https://raider.io/api/search?term=${encodeURIComponent(term)}&region=${region}`),
  profile: (region, realm, name) =>
    getJson("https://raider.io/api/v1/characters/profile?region=" + region
      + "&realm=" + encodeURIComponent(realmSlug(realm))
      + "&name=" + encodeURIComponent(name.trim())
      + "&fields=" + encodeURIComponent("gear,guild,mythic_plus_scores_by_season:current,"
        + "raid_progression,mythic_plus_best_runs,mythic_plus_ranks")),
  score: (region, realm, name) =>
    getJson("https://raider.io/api/v1/characters/profile?region=" + region
      + "&realm=" + encodeURIComponent(realm)
      + "&name=" + encodeURIComponent(name.trim())
      + "&fields=" + encodeURIComponent("mythic_plus_scores_by_season:current")),
  cutoffs: (region, season) =>
    getJson(`https://raider.io/api/v1/mythic-plus/season-cutoffs?region=${region}&season=${season}`),
  staticData: (expansionId) =>
    getJson(`https://raider.io/api/v1/mythic-plus/static-data?expansion_id=${expansionId}`),
};

function seasonScore(profile) {
  const s = (profile.mythic_plus_scores_by_season || [])[0];
  const all = s && s.segments && s.segments.all;
  if (all) return { value: all.score || 0, color: all.color || "#ffffff" };
  return { value: (s && s.scores && s.scores.all) || 0, color: "#ffffff" };
}

async function currentSeason(region) {
  const cached = store.get("season_" + region);
  if (cached && Date.now() - cached.ts < 86400e3) return cached.season;
  const now = Date.now();
  for (let expId = 11; expId <= 15; expId++) {
    let data;
    try { data = await rio.staticData(expId); } catch { continue; }
    for (const s of data.seasons || []) {
      if (!s.is_main_season || !s.starts || !s.ends) continue;
      const st = Date.parse(s.starts[region] || "");
      const en = Date.parse(s.ends[region] || "");
      if (st && en && now >= st && now < en) {
        const season = { slug: s.slug, name: s.name, startMs: st, endMs: en };
        store.set("season_" + region, { ts: now, season });
        return season;
      }
    }
  }
  throw new Error("stagione M+ corrente non trovata");
}

// ------------------------------------------------------------------ API WarcraftLogs

async function wclToken() {
  const cached = store.get("wcl_token");
  if (cached && Date.now() < cached.expiry - 60e3) return cached.token;
  const res = await fetch("https://www.warcraftlogs.com/oauth/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "grant_type=client_credentials&client_id=" + WCL_CLIENT_ID
      + "&client_secret=" + WCL_CLIENT_SECRET,
  });
  if (!res.ok) throw new Error("autenticazione WarcraftLogs fallita");
  const o = await res.json();
  store.set("wcl_token", { token: o.access_token, expiry: Date.now() + o.expires_in * 1000 });
  return o.access_token;
}

async function wclQuery(query, variables) {
  const token = await wclToken();
  const o = await getJson("https://www.warcraftlogs.com/api/v2/client", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: "Bearer " + token },
    body: JSON.stringify({ query, variables }),
  });
  if (o.errors && o.errors.length) throw new Error(o.errors[0].message);
  return o.data;
}

async function wclExpansions() {
  const cached = store.get("wcl_expansions");
  if (cached && Date.now() - cached.ts < 86400e3) return cached.data;
  const data = await wclQuery("{worldData{expansions{id name zones{id name difficulties{id name}}}}}");
  const exps = data.worldData.expansions;
  store.set("wcl_expansions", { ts: Date.now(), data: exps });
  return exps;
}

function wclRankings(region, realm, name, zoneId, difficulty, metric, byBracket) {
  const vars = { name: name.trim(), server: realm, region };
  if (zoneId) vars.zone = zoneId;
  if (difficulty) vars.difficulty = difficulty;
  if (metric) vars.metric = metric;
  if (byBracket) vars.bracket = true;
  return wclQuery(
    "query($name:String!,$server:String!,$region:String!,$zone:Int,$difficulty:Int,"
    + "$metric:CharacterPageRankingMetricType,$bracket:Boolean){characterData{"
    + "character(name:$name,serverSlug:$server,serverRegion:$region){name zoneRankings("
    + "zoneID:$zone,difficulty:$difficulty,metric:$metric,byBracket:$bracket)}}}", vars)
    .then((data) => {
      const ch = data.characterData && data.characterData.character;
      if (!ch || ch.zoneRankings == null) {
        throw new Error("personaggio non trovato su WarcraftLogs (o profilo nascosto)");
      }
      return ch.zoneRankings;
    });
}

// ------------------------------------------------------------------ preferiti

const favs = {
  list: () => store.get("favorites", []),
  key: (e) => (e.region + "|" + e.realm + "|" + e.name).toLowerCase(),
  isFav(e) { return this.list().some((f) => this.key(f) === this.key(e)); },
  toggle(e) {
    const all = this.list();
    const i = all.findIndex((f) => this.key(f) === this.key(e));
    if (i >= 0) all.splice(i, 1); else all.push(e);
    store.set("favorites", all);
    return i < 0;
  },
  remove(e) {
    store.set("favorites", this.list().filter((f) => this.key(f) !== this.key(e)));
  },
};

// ------------------------------------------------------------------ rendering base

function setHeader(title, showBack) {
  headerTitle.textContent = title;
  btnBack.hidden = !showBack;
}

function card(html, cls) {
  return `<div class="card ${cls || ""}">${html}</div>`;
}

function row(label, value, color) {
  return `<div class="row"><span class="label">${label}</span>`
    + `<span class="value" style="color:${color || "inherit"}">${value}</span></div>`;
}

const spinner = '<div class="spinner"></div>';

// ------------------------------------------------------------------ viste

function renderHome() {
  setHeader("LanWoW", false);
  const region = store.get("region", "eu");
  view.innerHTML = card(`
    <label class="field">Regione
      <select id="region">${REGIONS.map((r) =>
        `<option ${r === region ? "selected" : ""}>${r}</option>`).join("")}</select>
    </label>
    <label class="field">Nome personaggio
      <input id="name" autocapitalize="off" autocorrect="off"
        value="${esc(store.get("last_name", ""))}">
    </label>
    <label class="field">Realm (facoltativo)
      <input id="realm" autocapitalize="off" autocorrect="off"
        value="${esc(store.get("last_realm", ""))}">
    </label>
    <div class="btn-row">
      <button class="primary" id="do-search">Cerca</button>
      <button class="outline" id="do-clear">Pulisci</button>
    </div>`)
    + '<div id="search-out"></div>';

  $("#do-search").onclick = doSearch;
  $("#name").onkeydown = (e) => { if (e.key === "Enter") doSearch(); };
  $("#realm").onkeydown = (e) => { if (e.key === "Enter") doSearch(); };
  $("#do-clear").onclick = () => {
    store.set("last_name", ""); store.set("last_realm", "");
    $("#name").value = ""; $("#realm").value = "";
    $("#search-out").innerHTML = "";
  };
}

async function doSearch() {
  const region = $("#region").value;
  const name = $("#name").value.trim();
  const realm = $("#realm").value.trim();
  const out = $("#search-out");
  store.set("region", region);
  store.set("last_name", name);
  store.set("last_realm", realm);
  if (!name) {
    out.innerHTML = '<div class="error">Compila almeno regione e nome del personaggio.</div>';
    return;
  }
  if (realm) {
    location.hash = `#/char/${region}/${encodeURIComponent(realmSlug(realm))}/${encodeURIComponent(name)}`;
    return;
  }
  out.innerHTML = spinner;
  try {
    const data = await rio.search(region, name);
    const found = (data.matches || [])
      .filter((m) => m.type === "character" && m.data
        && m.data.region && m.data.region.slug === region)
      .slice(0, 10);
    if (!found.length) {
      out.innerHTML = '<div class="error">Nessun personaggio trovato con questo nome.</div>';
      return;
    }
    out.innerHTML = `<div class="muted">Risultati (${found.length}) — tocca per aprire la scheda</div>`
      + found.map((m, i) => {
        const d = m.data;
        const cls = d.class ? d.class.name : "";
        return card(`<div class="result-row">
          <div class="info">
            <div style="color:${classColor(cls)};font-weight:bold;font-size:17px">${esc(d.name)}</div>
            <div class="muted" style="margin:0">${esc(d.realm.name)} (${region.toUpperCase()})${cls ? " · " + esc(cls) : ""}</div>
          </div>
          <div class="score" id="score-${i}">…</div>
        </div>`, "clickable")
          .replace('<div class="card', `<div data-i="${i}" class="card`);
      }).join("");

    found.forEach((m, i) => {
      const d = m.data;
      const el = () => $(`#score-${i}`);
      rio.score(region, d.realm.slug, d.name).then((p) => {
        const s = seasonScore(p);
        const t = el();
        if (t) { t.textContent = fmt0(s.value); t.style.color = s.color; }
      }).catch(() => { const t = el(); if (t) t.textContent = ""; });
      const cardEl = $(`[data-i="${i}"]`);
      cardEl.onclick = () => {
        location.hash = `#/char/${region}/${encodeURIComponent(d.realm.slug)}/${encodeURIComponent(d.name)}`;
      };
    });
  } catch (e) {
    out.innerHTML = `<div class="error">Errore: ${esc(e.message)}</div>`;
  }
}

async function renderCharacter(region, realm, name) {
  setHeader(decodeURIComponent(name), true);
  view.innerHTML = spinner;
  let p;
  try {
    p = await rio.profile(region, decodeURIComponent(realm), decodeURIComponent(name));
  } catch (e) {
    view.innerHTML = `<div class="error">Errore: ${esc(e.message)}</div>`;
    return;
  }
  setHeader(p.name, true);
  const entry = { region, realm: realmSlug(p.realm), name: p.name, cls: p.class || "" };
  const s = seasonScore(p);

  let html = card(`<div class="profile">
    <img src="${esc(p.thumbnail_url || "")}" alt="">
    <div>
      <div class="name" style="color:${classColor(p.class)}">${esc(p.name)}
        <button id="fav-star" class="fav-remove">${favs.isFav(entry) ? "★" : "☆"}</button></div>
      ${p.guild ? `<div class="guild">&lt;${esc(p.guild.name)}&gt;</div>` : ""}
      <div>${esc(p.race)} ${esc(p.class)} — ${esc(p.active_spec_name)}</div>
      <div>${esc(p.realm)} (${region.toUpperCase()}) · ${p.faction === "alliance" ? "Alleanza" : p.faction === "horde" ? "Orda" : esc(p.faction)}</div>
      ${p.gear ? `<div>Item level equipaggiato: ${fmt1(p.gear.item_level_equipped)}</div>` : ""}
    </div></div>`);

  let mp = `<div class="section-title">Mythic+</div>`
    + row("Punteggio stagione corrente", fmt0(s.value), s.color);
  const best = p.mythic_plus_best_runs || [];
  if (best.length) {
    mp += '<div class="subtitle">Migliori run</div>' + best.slice(0, 8).map((r) =>
      row(esc(r.dungeon), "+".repeat(r.num_keystone_upgrades || 0) + r.mythic_level
        + `  (${fmt0(r.score)})`)).join("");
  } else if (!s.value) {
    mp += '<div class="muted">Nessuna run Mythic+ nella stagione corrente.</div>';
  }
  html += card(mp);

  // classifica per classe/ruolo (mondo/regione/reame)
  const ranks = p.mythic_plus_ranks || {};
  const rankRows = [
    ["class", "Tutti i " + classIt(p.class, true)],
    ["class_tank", classIt(p.class, false) + " Difensori"],
    ["class_healer", classIt(p.class, false) + " Guaritori"],
    ["class_dps", classIt(p.class, false) + " DPS"],
  ].filter(([k]) => ranks[k] && ranks[k].world > 0);
  if (rankRows.length) {
    html += card('<div class="section-title">Classifica</div>'
      + '<div class="rank-row header"><span class="rank-label"></span>'
      + '<span>Tutte le regioni</span><span>Regione</span><span>Reame</span></div>'
      + rankRows.map(([k, label]) => {
        const r = ranks[k];
        const cell = (v) =>
          `<span style="color:${rankColor(v)}">${fmt0(v)}</span>`;
        return `<div class="rank-row"><span class="rank-label">${esc(label)}</span>`
          + cell(r.world) + cell(r.region) + cell(r.realm) + "</div>";
      }).join(""));
  }

  const raids = p.raid_progression || {};
  const raidKeys = Object.keys(raids);
  if (raidKeys.length) {
    html += card('<div class="section-title">Progressione raid</div>'
      + raidKeys.map((k) =>
        row(esc(prettify(k)), esc((raids[k].summary || "").trim() || "—"))).join(""));
  }

  html += `<div class="btn-row"><button class="primary" id="go-logs">Log</button></div>`;
  view.innerHTML = html;

  $("#fav-star").onclick = () => {
    const added = favs.toggle(entry);
    $("#fav-star").textContent = added ? "★" : "☆";
  };
  $("#go-logs").onclick = () => {
    location.hash = `#/logs/${region}/${encodeURIComponent(entry.realm)}/${encodeURIComponent(p.name)}`;
  };
}

function renderFavorites() {
  setHeader("Preferiti", true);
  const all = favs.list();
  if (!all.length) {
    view.innerHTML = '<div class="muted">Nessun preferito: cerca un personaggio e tocca la stella nella sua scheda.</div>';
    return;
  }
  view.innerHTML = all.map((e, i) => card(`<div class="result-row">
    <div class="info">
      <div style="color:${classColor(e.cls)};font-weight:bold;font-size:17px">${esc(e.name)}</div>
      <div class="muted" style="margin:0">${esc(prettify(e.realm))} (${e.region.toUpperCase()})${e.cls ? " · " + esc(e.cls) : ""}</div>
    </div>
    <button class="fav-remove" data-remove="${i}">★</button>
  </div>`, "clickable").replace('<div class="card', `<div data-open="${i}" class="card`)).join("");

  all.forEach((e, i) => {
    $(`[data-open="${i}"]`).onclick = (ev) => {
      if (ev.target.dataset.remove !== undefined) return;
      location.hash = `#/char/${e.region}/${encodeURIComponent(e.realm)}/${encodeURIComponent(e.name)}`;
    };
    $(`[data-remove="${i}"]`).onclick = (ev) => {
      ev.stopPropagation();
      favs.remove(e);
      renderFavorites();
    };
  });
}

// ------------------------------------------------------------------ log con filtri

async function renderLogs(region, realm, name) {
  setHeader("Log — " + decodeURIComponent(name), true);
  view.innerHTML = spinner;
  let exps;
  try {
    exps = await wclExpansions();
  } catch (e) {
    view.innerHTML = `<div class="error">Errore: ${esc(e.message)}</div>`;
    return;
  }
  view.innerHTML = card(`
    <label class="field">Espansione
      <select id="f-exp">${exps.map((x, i) =>
        `<option value="${i}">${esc(x.name)}</option>`).join("")}</select>
    </label>
    <label class="field">Zona <select id="f-zone"></select></label>
    <div style="display:flex;gap:8px">
      <label class="field" style="flex:1">Difficoltà <select id="f-diff"></select></label>
      <label class="field" style="flex:1">Metrica
        <select id="f-metric">${METRICS.map((m, i) =>
          `<option value="${i}">${m[0]}</option>`).join("")}</select>
      </label>
    </div>`)
    + '<div id="logs-out"></div>';

  const state = { zones: [], zoneId: 0, zoneName: "", difficulty: 0,
    metric: null, bracket: false, seq: 0 };

  const fillZones = (expIndex) => {
    state.zones = (exps[expIndex].zones || [])
      .filter((z) => !z.name.includes("PTR") && !z.name.includes("Beta"));
    $("#f-zone").innerHTML = state.zones.map((z, i) =>
      `<option value="${i}">${esc(z.name)}</option>`).join("");
    fillDiffs(0);
  };
  const fillDiffs = (zoneIndex) => {
    const z = state.zones[zoneIndex];
    if (!z) { $("#logs-out").innerHTML = ""; return; }
    state.zoneId = z.id;
    state.zoneName = z.name;
    const diffs = z.difficulties || [];
    $("#f-diff").innerHTML = '<option value="0">Predefinita</option>'
      + diffs.map((d) => `<option value="${d.id}">${esc(d.name)}</option>`).join("");
    state.difficulty = 0;
    load();
  };
  const load = async () => {
    const seq = ++state.seq;
    const out = $("#logs-out");
    out.innerHTML = spinner;
    try {
      const r = await wclRankings(region, decodeURIComponent(realm),
        decodeURIComponent(name), state.zoneId, state.difficulty,
        state.metric, state.bracket);
      if (seq !== state.seq) return;
      let html = `<div class="section-title">${esc(state.zoneName)}</div>`;
      if (r.bestPerformanceAverage != null) {
        html += row("Media best perf.", fmt1(r.bestPerformanceAverage),
          parseColor(r.bestPerformanceAverage));
      }
      if (r.medianPerformanceAverage != null) {
        html += row("Media mediana", fmt1(r.medianPerformanceAverage),
          parseColor(r.medianPerformanceAverage));
      }
      const list = r.rankings || [];
      if (!list.length) {
        html += '<div class="muted">Nessun log trovato per questa selezione.</div>';
      }
      for (const it of list) {
        const boss = it.encounter ? it.encounter.name : "?";
        if (!it.totalKills && it.rankPercent == null) {
          html += row(esc(boss), "—");
        } else {
          const pct = it.rankPercent || 0;
          html += row(`${esc(boss)}  (${it.totalKills} kill)`,
            Math.floor(pct) + "%", parseColor(pct));
        }
      }
      out.innerHTML = card(html);
    } catch (e) {
      if (seq === state.seq) {
        out.innerHTML = `<div class="error">Errore: ${esc(e.message)}</div>`;
      }
    }
  };

  $("#f-exp").onchange = (e) => fillZones(+e.target.value);
  $("#f-zone").onchange = (e) => fillDiffs(+e.target.value);
  $("#f-diff").onchange = (e) => { state.difficulty = +e.target.value; load(); };
  $("#f-metric").onchange = (e) => {
    state.metric = METRICS[+e.target.value][1];
    state.bracket = METRICS[+e.target.value][2];
    load();
  };
  fillZones(0);
}

// ------------------------------------------------------------------ titolo M+

async function renderTitle() {
  setHeader("Titolo M+", true);
  const region = store.get("region", "eu");
  view.innerHTML = card(`
    <label class="field">Regione
      <select id="t-region">${REGIONS.map((r) =>
        `<option ${r === region ? "selected" : ""}>${r}</option>`).join("")}</select>
    </label>`)
    + '<div id="title-out">' + spinner + "</div>";
  $("#t-region").onchange = (e) => loadTitle(e.target.value);
  loadTitle(region);
}

async function loadTitle(region) {
  const out = $("#title-out");
  out.innerHTML = spinner;
  try {
    const season = await currentSeason(region);
    const [cutoffsResp, seasonCfg, forecastData] = await Promise.all([
      rio.cutoffs(region, season.slug),
      getJson("data/season.json").catch(() => null),
      getJson("data/cutoff-forecast.json").catch(() => null),
    ]);
    const cutoffs = cutoffsResp.cutoffs || {};
    const known = seasonCfg && seasonCfg.slug === season.slug
      ? Date.parse(seasonCfg.endDate + "T00:00:00Z") : 0;
    const effEnd = known ? Math.min(season.endMs, known)
      : Math.min(season.endMs, season.startMs + 22 * 7 * 86400e3);

    const factionRows = (pct, mapValue) => {
      const block = cutoffs[pct];
      if (!block) return "";
      return [["horde", "Orda", block.hordeColor],
              ["alliance", "Alleanza", block.allianceColor],
              ["all", "Tutti", block.allColor]]
        .filter(([k]) => block[k] && block[k].quantileMinValue > 0)
        .map(([k, label, color]) =>
          row(label, mapValue(block[k].quantileMinValue), color)).join("");
    };

    let html =
      card('<div class="section-title">Top 0,1% — Titolo</div>'
        + factionRows("p999", (v) => fmt0(v)))
      + card('<div class="section-title">Top 1%</div>'
        + factionRows("p990", (v) => fmt0(v)));

    const week = Math.floor((Date.now() - season.startMs) / (7 * 86400e3)) + 1;
    const totalWeeks = Math.round((effEnd - season.startMs) / (7 * 86400e3));
    const dt = (ms) => new Date(ms).toLocaleDateString("it-IT");
    html += card(`<div class="section-title">Stagione: ${esc(season.name)}</div>`
      + row("Periodo", `${dt(season.startMs)} – ~${dt(effEnd)}`)
      + row("Avanzamento stagione",
        `Settimana ${Math.min(week, totalWeeks)} di ~${totalWeeks}`, "#f8b700"));

    // previsione: fattore del sito mplus-title (JSON aggiornato ogni giorno
    // da una GitHub Action) o, in mancanza, stima sulla fase della stagione
    const fc = forecastData && forecastData[region.toUpperCase()];
    let factor = null;
    let method;
    if (fc && fc.current > 0 && fc.estimation >= fc.current) {
      factor = fc.estimation / fc.current;
      method = "Previsione calcolata con la logica di mplus-title.vercel.app "
        + "(fattore applicato a fazioni e top 1%), aggiornata "
        + new Date(forecastData.updatedAt).toLocaleDateString("it-IT") + ".";
    } else {
      const t = Math.max(0.05, Math.min(1,
        (Date.now() - season.startMs) / (effEnd - season.startMs)));
      factor = 1 / (0.70 + 0.30 * t);
      method = "Stima basata sulla fase della stagione (mplus-title non disponibile).";
    }
    html += card('<div class="section-title">Previsione fine stagione</div>'
      + '<div class="subtitle">Top 0,1% — Titolo</div>'
      + factionRows("p999", (v) => "~" + fmt0(Math.max(v, v * factor)))
      + '<div class="subtitle">Top 1%</div>'
      + factionRows("p990", (v) => "~" + fmt0(Math.max(v, v * factor)))
      + `<div class="muted">${method}</div>`
      + '<div class="muted">Il titolo va al top 0,1% della propria fazione nella regione. '
      + "La previsione è indicativa: l'ultima settimana il cutoff può salire più del previsto.</div>");

    out.innerHTML = html;
  } catch (e) {
    out.innerHTML = `<div class="error">Errore: ${esc(e.message)}</div>`;
  }
}

// ------------------------------------------------------------------ router + aggiornamenti

function route() {
  const parts = location.hash.replace(/^#\/?/, "").split("/");
  switch (parts[0]) {
    case "char": renderCharacter(parts[1], parts[2], parts[3]); break;
    case "logs": renderLogs(parts[1], parts[2], parts[3]); break;
    case "favs": renderFavorites(); break;
    case "title": renderTitle(); break;
    default: renderHome();
  }
  window.scrollTo(0, 0);
}

btnBack.onclick = () => history.back();
document.getElementById("btn-favs").onclick = () => { location.hash = "#/favs"; };
document.getElementById("btn-title").onclick = () => { location.hash = "#/title"; };
window.addEventListener("hashchange", route);

async function checkUpdate() {
  try {
    const rel = await getJson(`https://api.github.com/repos/${REPO}/releases/latest`);
    const latest = (rel.tag_name || "").replace(/^v/, "");
    const isNewer = (() => {
      const a = latest.split(".").map(Number);
      const b = VERSION.split(".").map(Number);
      for (let i = 0; i < Math.max(a.length, b.length); i++) {
        if ((a[i] || 0) !== (b[i] || 0)) return (a[i] || 0) > (b[i] || 0);
      }
      return false;
    })();
    if (isNewer) {
      document.getElementById("update-title").textContent =
        `Nuova versione ${latest} disponibile`;
      document.getElementById("update-body").textContent = rel.body || "";
      document.getElementById("update-banner").hidden = false;
      document.getElementById("update-reload").onclick = async () => {
        if ("serviceWorker" in navigator) {
          const regs = await navigator.serviceWorker.getRegistrations();
          await Promise.all(regs.map((r) => r.unregister()));
        }
        location.reload();
      };
    }
  } catch { /* offline o rate limit: pazienza */ }
}

// ------------------------------------------------------------------ installazione

function setupInstallBanner() {
  const isStandalone = window.matchMedia("(display-mode: standalone)").matches
    || navigator.standalone === true;
  if (isStandalone || store.get("install_dismissed")) {
    return;
  }
  const ua = navigator.userAgent;
  const isIos = /iphone|ipad|ipod/i.test(ua)
    || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);

  const banner = document.createElement("div");
  banner.id = "install-banner";
  const dismiss = () => { store.set("install_dismissed", true); banner.remove(); };

  if (isIos) {
    banner.innerHTML = `<div>
        <b>Installa LanWoW sulla schermata Home</b>
        <div>Tocca <svg class="share-icon" viewBox="0 0 24 24" width="15" height="15"><path fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" d="M12 15V3m0 0L8 7m4-4l4 4M5 11v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-9"/></svg> <b>Condividi</b> qui sotto in Safari,
        poi <b>“Aggiungi alla schermata Home”</b>.</div>
      </div>
      <button id="install-close">✕</button>`;
    document.body.prepend(banner);
    $("#install-close").onclick = dismiss;
    return;
  }

  // Android/desktop Chrome: prompt di installazione nativo
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    banner.innerHTML = `<div>
        <b>Installa LanWoW</b>
        <div>Aggiungila alla schermata Home come app.</div>
      </div>
      <button id="install-go">Installa</button>
      <button id="install-close">✕</button>`;
    document.body.prepend(banner);
    $("#install-go").onclick = async () => {
      banner.remove();
      e.prompt();
      const choice = await e.userChoice.catch(() => null);
      if (!choice || choice.outcome !== "accepted") {
        store.set("install_dismissed", true);
      }
    };
    $("#install-close").onclick = dismiss;
  });
}

document.getElementById("footer-version").textContent = VERSION;
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("sw.js").catch(() => {});
}
setupInstallBanner();
route();
checkUpdate();
