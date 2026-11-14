// TheRabbits server (Cloudflare Worker + KV): cosmetics sync, codes, licenses, creator codes, friends and parties.
//
// Players prove who they are the same way a Minecraft server checks them: the game tells Mojang it is
// joining "serverId", and this worker asks Mojang (hasJoined) which account that was. After that the
// client gets a token (valid 30 days) that it sends as "Authorization: Bearer <token>".
//
// Public
//   GET  /config                        -> { locked: [ids], licenseRequired }
//   GET  /cosmetics?uuids=a,b,...       -> { a: {cape, hat, arms, shield}, ... }
//   GET  /capes?uuids=...               -> { a: capeId }                         (older clients)
//   GET  /challenge                     -> { serverId }
//   POST /login   { name, serverId }    -> { token, uuid, name }
//   POST /cape    { name, serverId, cape, hat?, arms?, shield? }               (older clients)
// Player (token)
//   GET  /me                            -> { uuid, name, owned, licensed, creator, friendCode, locked, licenseRequired }
//   POST /cosmetics/set { cape, hat, arms, shield }
//   POST /redeem  { code }              -> { ok, type, message, unlocked }
//   POST /heartbeat
//   GET  /friends                       POST /friends/add { code }   POST /friends/remove { uuid }
//   GET  /party   POST /party/create    POST /party/join { code }    POST /party/leave
// Admin ("Authorization: Bearer <ADMIN_KEY>", ADMIN_KEY is a secret you set on the worker)
//   GET  /admin                         -> the admin panel (web page)
//   GET  /admin/api/state   POST /admin/api/codes   DELETE /admin/api/codes?code=   POST /admin/api/settings
//
// KV binding name: CAPES.

const CAPES = ["none", "sheen_black", "sheen_white", "pulse", "scanline", "black_nebula"];
const HATS = ["none", "fedora", "crown", "ears", "royal_crown", "gold_crown", "cat_hat", "bucket_hat", "santa_hat", "frog_hat", "bunny_hat"];
const ARMS = ["none", "claws"];
const SHIELDS = ["none", "neon_shield"];
const AURAS = ["none", "aura", "snow_aura"];
const SLOTS = { cape: CAPES, hat: HATS, arms: ARMS, shield: SHIELDS, aura: AURAS };
const ALL = [...CAPES, ...HATS, ...ARMS, ...SHIELDS, ...AURAS].filter((x) => x !== "none");
const CODE_TYPES = ["cosmetic", "license", "creator"];

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
    },
  });
const isUuid = (s) => /^[0-9a-f]{32}$/.test(s);
const rand = (n, alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789") => {
  const b = crypto.getRandomValues(new Uint8Array(n));
  return [...b].map((x) => alphabet[x % alphabet.length]).join("");
};
const hex = (n) => [...crypto.getRandomValues(new Uint8Array(n))].map((b) => b.toString(16).padStart(2, "0")).join("");
const normCode = (s) => String(s || "").trim().toUpperCase().replace(/\s+/g, "");

async function getJson(env, key, fallback) {
  const v = await env.CAPES.get(key);
  if (v === null) return fallback;
  try { return JSON.parse(v); } catch { return fallback; }
}
const putJson = (env, key, value, opts) => env.CAPES.put(key, JSON.stringify(value), opts);

async function config(env) {
  const c = await getJson(env, "config", {});
  return { locked: Array.isArray(c.locked) ? c.locked.filter((x) => ALL.includes(x)) : [], licenseRequired: !!c.licenseRequired };
}

async function readBody(request) {
  const text = await request.text().catch(() => "");
  if (!text.trim()) return {};
  try { return JSON.parse(text) || {}; } catch { return null; }
}

async function mojangCheck(env, name, serverId) {
  if (typeof name !== "string" || !/^[A-Za-z0-9_]{1,16}$/.test(name)) return { error: "bad name", status: 400 };
  if (typeof serverId !== "string" || !/^[0-9a-f]{20}$/.test(serverId)) return { error: "bad serverId", status: 400 };
  if (!(await env.CAPES.get("ch:" + serverId))) return { error: "challenge expired", status: 403 };
  await env.CAPES.delete("ch:" + serverId);
  const check = await fetch("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
    encodeURIComponent(name) + "&serverId=" + encodeURIComponent(serverId));
  if (check.status !== 200) return { error: "login check failed", status: 403 };
  const profile = await check.json();
  const uuid = String(profile.id || "").toLowerCase();
  if (!isUuid(uuid)) return { error: "login check failed", status: 403 };
  const realName = String(profile.name || name);
  await env.CAPES.put("name:" + uuid, realName);
  return { uuid, name: realName };
}

async function player(env, request) {
  const auth = request.headers.get("authorization") || "";
  const m = auth.match(/^Bearer ([0-9a-f]{40})$/);
  if (!m) return null;
  return getJson(env, "tok:" + m[1], null);
}

async function friendCodeOf(env, uuid) {
  let code = await env.CAPES.get("fcof:" + uuid);
  if (code) return code;
  for (let i = 0; i < 5; i++) {
    const c = rand(6);
    if (await env.CAPES.get("fc:" + c)) continue;
    await env.CAPES.put("fc:" + c, uuid);
    await env.CAPES.put("fcof:" + uuid, c);
    return c;
  }
  return null;
}

const owned = (env, uuid) => getJson(env, "owned:" + uuid, []);

async function allowed(env, uuid, id) {
  if (!id || id === "none") return true;
  const { locked } = await config(env);
  if (!locked.includes(id)) return true;
  return (await owned(env, uuid)).includes(id);
}

async function setCosmetics(env, uuid, body) {
  const changes = {};
  for (const [slot, list] of Object.entries(SLOTS)) {
    const v = body[slot];
    if (v === undefined || v === null) continue;
    if (!list.includes(v)) return { error: "unknown " + slot, status: 400 };
    if (!(await allowed(env, uuid, v))) return { error: "locked: " + v, status: 403 };
    changes[slot] = v;
  }
  for (const [slot, v] of Object.entries(changes)) {
    if (v === "none") await env.CAPES.delete(slot + ":" + uuid);
    else await env.CAPES.put(slot + ":" + uuid, v);
  }
  return { ok: true };
}

async function nameOf(env, uuid) { return (await env.CAPES.get("name:" + uuid)) || uuid.slice(0, 8); }
async function online(env, uuid) { return !!(await env.CAPES.get("on:" + uuid)); }
async function people(env, uuids) {
  return Promise.all(uuids.map(async (u) => ({ uuid: u, name: await nameOf(env, u), online: await online(env, u) })));
}

async function redeem(env, me, raw) {
  const code = normCode(raw);
  if (!/^[A-Z0-9-]{3,40}$/.test(code)) return { error: "That code isn't valid", status: 400 };
  const rec = await getJson(env, "code:" + code, null);
  if (!rec) return { error: "That code doesn't exist", status: 404 };
  if (rec.usesLeft === 0) return { error: "That code has been used up", status: 410 };
  if (await env.CAPES.get("used:" + code + ":" + me.uuid)) return { error: "You already used this code", status: 409 };

  let message = "Code redeemed!";
  const unlocked = [];
  if (rec.type === "license") {
    await env.CAPES.put("lic:" + me.uuid, "1");
    message = "License activated - enjoy TheRabbits Client!";
  }
  if (rec.type === "creator") {
    await env.CAPES.put("creator:" + me.uuid, rec.creator);
    const n = parseInt((await env.CAPES.get("cstat:" + rec.creator)) || "0", 10) + 1;
    await env.CAPES.put("cstat:" + rec.creator, String(n));
    message = "You're now supporting " + rec.creator + "!";
  }
  if (Array.isArray(rec.rewards) && rec.rewards.length) {
    const have = await owned(env, me.uuid);
    for (const r of rec.rewards) if (ALL.includes(r) && !have.includes(r)) { have.push(r); unlocked.push(r); }
    await putJson(env, "owned:" + me.uuid, have);
    if (rec.type === "cosmetic") message = unlocked.length ? "Unlocked " + unlocked.length + " cosmetic(s)!" : "You already own everything in this code";
  }
  await env.CAPES.put("used:" + code + ":" + me.uuid, "1");
  rec.redeemed = (rec.redeemed || 0) + 1;
  if (rec.usesLeft > 0) rec.usesLeft -= 1;
  await putJson(env, "code:" + code, rec);
  return { ok: true, type: rec.type, message, unlocked };
}

async function leaveParty(env, uuid) {
  const code = await env.CAPES.get("pof:" + uuid);
  if (!code) return;
  await env.CAPES.delete("pof:" + uuid);
  const p = await getJson(env, "party:" + code, null);
  if (!p) return;
  p.members = p.members.filter((m) => m !== uuid);
  if (!p.members.length) { await env.CAPES.delete("party:" + code); return; }
  if (p.leader === uuid) p.leader = p.members[0];
  await putJson(env, "party:" + code, p, { expirationTtl: 86400 });
}

async function handlePlayer(env, request, url, me) {
  const path = url.pathname, method = request.method;
  if (method === "GET" && path === "/me") {
    const cfg = await config(env);
    return json({
      uuid: me.uuid, name: me.name, owned: await owned(env, me.uuid), licensed: !!(await env.CAPES.get("lic:" + me.uuid)),
      creator: await env.CAPES.get("creator:" + me.uuid), friendCode: await friendCodeOf(env, me.uuid), ...cfg,
    });
  }
  if (method === "POST" && path === "/heartbeat") {
    await env.CAPES.put("on:" + me.uuid, "1", { expirationTtl: 400 });
    return json({ ok: true });
  }
  const body = method === "POST" ? await readBody(request) : {};
  if (body === null) return json({ error: "bad json" }, 400);
  if (method === "POST" && path === "/cosmetics/set") {
    const r = await setCosmetics(env, me.uuid, body);
    return json(r, r.status || 200);
  }
  if (method === "POST" && path === "/redeem") {
    const r = await redeem(env, me, body.code);
    return json(r, r.status || 200);
  }
  if (method === "GET" && path === "/friends") {
    return json({ friendCode: await friendCodeOf(env, me.uuid), friends: await people(env, await getJson(env, "fr:" + me.uuid, [])) });
  }
  if (method === "POST" && path === "/friends/add") {
    const other = await env.CAPES.get("fc:" + normCode(body.code));
    if (!other) return json({ error: "No player has that friend code" }, 404);
    if (other === me.uuid) return json({ error: "That's your own code" }, 400);
    for (const [a, b] of [[me.uuid, other], [other, me.uuid]]) {
      const list = await getJson(env, "fr:" + a, []);
      if (!list.includes(b)) { list.push(b); await putJson(env, "fr:" + a, list.slice(-200)); }
    }
    return json({ ok: true, message: "Added " + (await nameOf(env, other)) + " as a friend" });
  }
  if (method === "POST" && path === "/friends/remove") {
    const other = String(body.uuid || "");
    for (const [a, b] of [[me.uuid, other], [other, me.uuid]]) {
      if (!isUuid(a)) continue;
      await putJson(env, "fr:" + a, (await getJson(env, "fr:" + a, [])).filter((x) => x !== b));
    }
    return json({ ok: true });
  }
  if (method === "GET" && path === "/party") {
    const code = await env.CAPES.get("pof:" + me.uuid);
    const p = code ? await getJson(env, "party:" + code, null) : null;
    if (!p) return json({ party: null });
    return json({ party: { code, leader: p.leader, members: await people(env, p.members) } });
  }
  if (method === "POST" && path === "/party/create") {
    await leaveParty(env, me.uuid);
    const code = rand(6);
    await putJson(env, "party:" + code, { leader: me.uuid, members: [me.uuid] }, { expirationTtl: 86400 });
    await env.CAPES.put("pof:" + me.uuid, code, { expirationTtl: 86400 });
    return json({ ok: true, code });
  }
  if (method === "POST" && path === "/party/join") {
    const code = normCode(body.code);
    const p = await getJson(env, "party:" + code, null);
    if (!p) return json({ error: "No party with that code" }, 404);
    if (p.members.length >= 16) return json({ error: "That party is full" }, 409);
    await leaveParty(env, me.uuid);
    if (!p.members.includes(me.uuid)) p.members.push(me.uuid);
    await putJson(env, "party:" + code, p, { expirationTtl: 86400 });
    await env.CAPES.put("pof:" + me.uuid, code, { expirationTtl: 86400 });
    return json({ ok: true, code });
  }
  if (method === "POST" && path === "/party/leave") {
    await leaveParty(env, me.uuid);
    return json({ ok: true });
  }
  return null;
}

async function handleAdmin(env, request, url) {
  if (!env.ADMIN_KEY) return json({ error: "Set the ADMIN_KEY secret on the worker first" }, 503);
  if ((request.headers.get("authorization") || "") !== "Bearer " + env.ADMIN_KEY) return json({ error: "Wrong admin key" }, 401);
  const path = url.pathname, method = request.method;
  if (method === "GET" && path === "/admin/api/state") {
    const codes = [];
    let cursor;
    do {
      const page = await env.CAPES.list({ prefix: "code:", cursor });
      for (const k of page.keys) { const c = await getJson(env, k.name, null); if (c) codes.push(c); }
      cursor = page.list_complete ? null : page.cursor;
    } while (cursor);
    const creators = {};
    const cs = await env.CAPES.list({ prefix: "cstat:" });
    for (const k of cs.keys) creators[k.name.slice(6)] = parseInt((await env.CAPES.get(k.name)) || "0", 10);
    codes.sort((a, b) => (b.created || 0) - (a.created || 0));
    return json({ codes, creators, cosmetics: { capes: CAPES, hats: HATS, arms: ARMS, shields: SHIELDS, auras: AURAS }, ...(await config(env)) });
  }
  const body = method === "POST" ? await readBody(request) : {};
  if (body === null) return json({ error: "bad json" }, 400);
  if (method === "POST" && path === "/admin/api/codes") {
    const type = CODE_TYPES.includes(body.type) ? body.type : null;
    if (!type) return json({ error: "type must be cosmetic, license or creator" }, 400);
    const code = normCode(body.code) || (type === "license" ? "TRLH-" + rand(4) + "-" + rand(4) + "-" + rand(4) : "TRLH-" + rand(6));
    if (!/^[A-Z0-9-]{3,40}$/.test(code)) return json({ error: "Codes can use letters, numbers and - (3-40 long)" }, 400);
    if (await env.CAPES.get("code:" + code)) return json({ error: "That code already exists" }, 409);
    const rewards = Array.isArray(body.rewards) ? body.rewards.filter((r) => ALL.includes(r)) : [];
    if (type === "cosmetic" && !rewards.length) return json({ error: "Pick at least one cosmetic to unlock" }, 400);
    const creator = String(body.creator || "").trim().slice(0, 32);
    if (type === "creator" && !creator) return json({ error: "Enter the creator's name" }, 400);
    const uses = parseInt(body.uses, 10);
    const rec = { code, type, rewards, creator: creator || null, note: String(body.note || "").slice(0, 120),
      usesLeft: Number.isFinite(uses) && uses > 0 ? uses : -1, redeemed: 0, created: Date.now() };
    await putJson(env, "code:" + code, rec);
    return json({ ok: true, code: rec });
  }
  if (method === "DELETE" && path === "/admin/api/codes") {
    await env.CAPES.delete("code:" + normCode(url.searchParams.get("code")));
    return json({ ok: true });
  }
  if (method === "POST" && path === "/admin/api/settings") {
    const c = await getJson(env, "config", {});
    if (Array.isArray(body.locked)) c.locked = body.locked.filter((x) => ALL.includes(x));
    if (typeof body.licenseRequired === "boolean") c.licenseRequired = body.licenseRequired;
    await putJson(env, "config", c);
    return json({ ok: true, ...(await config(env)) });
  }
  return json({ error: "not found" }, 404);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname, method = request.method;
    if (method === "OPTIONS") return json({});

    if (method === "GET" && path === "/admin") return new Response(ADMIN_HTML, { headers: { "content-type": "text/html; charset=utf-8" } });
    if (path.startsWith("/admin/api/")) return handleAdmin(env, request, url);

    if (method === "GET" && path === "/config") return json(await config(env));

    if (method === "GET" && (path === "/cosmetics" || path === "/capes")) {
      const ids = (url.searchParams.get("uuids") || "").toLowerCase().split(",").filter(isUuid).slice(0, 100);
      const out = {};
      await Promise.all(ids.map(async (id) => {
        const [cape, hat, arms, shield, aura] = await Promise.all(["cape:", "hat:", "arms:", "shield:", "aura:"].map((k) => env.CAPES.get(k + id)));
        out[id] = path === "/capes" ? cape || "none" : { cape: cape || "none", hat: hat || "none", arms: arms || "none", shield: shield || "none", aura: aura || "none" };
      }));
      return json(out);
    }

    if (method === "GET" && path === "/challenge") {
      const serverId = hex(10);
      await env.CAPES.put("ch:" + serverId, "1", { expirationTtl: 120 });
      return json({ serverId });
    }

    if (method === "POST" && path === "/login") {
      const body = await readBody(request);
      if (!body) return json({ error: "bad json" }, 400);
      const who = await mojangCheck(env, body.name, body.serverId);
      if (who.error) return json({ error: who.error }, who.status);
      const token = hex(20);
      await putJson(env, "tok:" + token, who, { expirationTtl: 30 * 86400 });
      await friendCodeOf(env, who.uuid);
      return json({ token, ...who });
    }

    if (method === "POST" && path === "/cape") {   // older clients: one-shot login + set
      const body = await readBody(request);
      if (!body) return json({ error: "bad json" }, 400);
      const who = await mojangCheck(env, body.name, body.serverId);
      if (who.error) return json({ error: who.error }, who.status);
      const r = await setCosmetics(env, who.uuid, { cape: body.cape, hat: body.hat, arms: body.arms, shield: body.shield, aura: body.aura });
      return json(r.error ? r : { ok: true, uuid: who.uuid }, r.status || 200);
    }

    const me = await player(env, request);
    if (me) {
      const r = await handlePlayer(env, request, url, me);
      if (r) return r;
    } else if (["/me", "/heartbeat", "/redeem", "/friends", "/party"].some((p) => path.startsWith(p)) || path === "/cosmetics/set") {
      return json({ error: "not logged in" }, 401);
    }

    if (path === "/") return json({ name: "TheRabbits server", ok: true });
    return json({ error: "not found" }, 404);
  },
};

const ADMIN_HTML = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>TheRabbits Admin</title>
<style>
:root{--bg:#0d0d0f;--card:#17171a;--line:#2a2a30;--text:#ececf0;--dim:#8d8d96;--acc:#ffffff;--ok:#5ad48a;--bad:#ff5a64}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.45 system-ui,-apple-system,Segoe UI,sans-serif}
header{padding:28px 32px 8px}h1{margin:0;font-size:26px;letter-spacing:.5px}header p{margin:4px 0 0;color:var(--dim)}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:18px;padding:18px 32px 40px}
section{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:18px}
h2{margin:0 0 12px;font-size:16px;text-transform:uppercase;letter-spacing:1.5px;color:var(--dim)}
label{display:block;margin:10px 0 4px;color:var(--dim);font-size:13px}
input,select{width:100%;padding:9px 11px;border-radius:9px;border:1px solid var(--line);background:#0f0f12;color:var(--text);font:inherit}
button{padding:9px 16px;border-radius:9px;border:0;background:var(--acc);color:#000;font-weight:600;cursor:pointer;font:inherit;font-weight:600}
button.ghost{background:transparent;color:var(--text);border:1px solid var(--line)}button.del{background:transparent;color:var(--bad);border:1px solid #4a2228;padding:5px 10px}
.row{display:flex;gap:10px;align-items:center}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:6px;margin-top:6px}
.chip{display:flex;gap:7px;align-items:center;padding:6px 9px;border:1px solid var(--line);border-radius:8px;font-size:13px}
.chip input{width:auto}table{width:100%;border-collapse:collapse;font-size:13px}td,th{padding:8px 6px;border-bottom:1px solid var(--line);text-align:left;vertical-align:top}
th{color:var(--dim);font-weight:500}code{background:#0f0f12;padding:2px 6px;border-radius:6px;user-select:all}
.msg{margin-top:10px;min-height:20px;font-size:14px}.ok{color:var(--ok)}.bad{color:var(--bad)}.wide{grid-column:1/-1}
.pill{font-size:11px;padding:2px 7px;border-radius:99px;border:1px solid var(--line);color:var(--dim)}
</style></head><body>
<header><h1>TheRabbits Admin</h1><p>Make codes, lock cosmetics and manage license keys.</p></header>
<main>
<section><h2>Admin key</h2>
<label>Your ADMIN_KEY (saved in this browser only)</label>
<div class="row"><input id="key" type="password" placeholder="paste your admin key"><button onclick="saveKey()">Connect</button></div>
<div id="keymsg" class="msg"></div></section>

<section><h2>Create a code</h2>
<label>Type</label>
<select id="type" onchange="typeChanged()"><option value="cosmetic">Cosmetic unlock</option><option value="license">License key</option><option value="creator">Creator code</option></select>
<label>Code (leave empty to generate one)</label><input id="code" placeholder="e.g. TRLH-WINTER">
<div id="creatorBox" style="display:none"><label>Creator name</label><input id="creator" placeholder="e.g. trlh"></div>
<label id="rewardLabel">Cosmetics this code unlocks</label><div id="rewards" class="grid"></div>
<div class="row"><div style="flex:1"><label>Uses (empty = unlimited)</label><input id="uses" type="number" min="1"></div>
<div style="flex:2"><label>Note (only you see this)</label><input id="note"></div></div>
<div style="margin-top:14px"><button onclick="createCode()">Create code</button></div>
<div id="createmsg" class="msg"></div></section>

<section><h2>Locked cosmetics &amp; license</h2>
<p style="color:var(--dim);margin:0 0 6px">Locked cosmetics can only be worn after redeeming a code that unlocks them. Unlocked ones are free for everyone.</p>
<div id="locked" class="grid"></div>
<label class="chip" style="margin-top:14px"><input type="checkbox" id="lic"> Require a license key to use the client</label>
<div style="margin-top:12px"><button onclick="saveSettings()">Save</button></div><div id="setmsg" class="msg"></div></section>

<section><h2>Creator codes</h2><table id="creators"><tr><th>Creator</th><th>Supporters</th></tr></table></section>

<section class="wide"><h2>Codes</h2><table id="codes"><tr><th>Code</th><th>Type</th><th>Gives</th><th>Used</th><th>Left</th><th>Note</th><th></th></tr></table></section>
</main>
<script>
let KEY='';try{KEY=localStorage.getItem('trlh_admin')||''}catch(e){}
let STATE=null;
const $=id=>document.getElementById(id);
const nice=s=>s.replace(/_/g,' ').replace(/\\b\\w/g,c=>c.toUpperCase());
function msg(id,text,ok){const e=$(id);e.textContent=text;e.className='msg '+(ok?'ok':'bad')}
async function api(path,opt={}){opt.headers=Object.assign({'authorization':'Bearer '+KEY,'content-type':'application/json'},opt.headers||{});
 const r=await fetch(path,opt);let j={};try{j=await r.json()}catch(e){}if(!r.ok)throw new Error(j.error||('HTTP '+r.status));return j}
function saveKey(){KEY=$('key').value.trim();try{localStorage.setItem('trlh_admin',KEY)}catch(e){}load()}
function typeChanged(){const t=$('type').value;$('creatorBox').style.display=t==='creator'?'block':'none';
 $('rewardLabel').textContent=t==='cosmetic'?'Cosmetics this code unlocks':'Bonus cosmetics (optional)'}
function allCos(){const c=STATE.cosmetics;return[...c.capes,...c.hats,...c.arms,...c.shields,...(c.auras||[])].filter(x=>x!=='none')}
function chips(el,name,checked){el.innerHTML='';for(const id of allCos()){const l=document.createElement('label');l.className='chip';
 l.innerHTML='<input type="checkbox" name="'+name+'" value="'+id+'"'+(checked.includes(id)?' checked':'')+'> '+nice(id);el.appendChild(l)}}
function picked(name){return[...document.querySelectorAll('input[name='+name+']:checked')].map(x=>x.value)}
async function load(){if(!KEY){msg('keymsg','Enter your admin key',false);return}
 try{STATE=await api('/admin/api/state');msg('keymsg','Connected',true)}catch(e){msg('keymsg',e.message,false);return}
 chips($('rewards'),'rw',[]);chips($('locked'),'lk',STATE.locked);$('lic').checked=STATE.licenseRequired;
 const t=$('codes');t.innerHTML='<tr><th>Code</th><th>Type</th><th>Gives</th><th>Used</th><th>Left</th><th>Note</th><th></th></tr>';
 for(const c of STATE.codes){const tr=document.createElement('tr');
  const gives=(c.type==='license'?'License':'')+(c.type==='creator'?'Supports '+c.creator:'')+(c.rewards&&c.rewards.length?((c.type!=='cosmetic'?' + ':'')+c.rewards.map(nice).join(', ')):'');
  tr.innerHTML='<td><code>'+c.code+'</code></td><td><span class="pill">'+c.type+'</span></td><td>'+gives+'</td><td>'+(c.redeemed||0)+'</td><td>'+(c.usesLeft<0?'∞':c.usesLeft)+'</td><td>'+(c.note||'')+'</td><td><button class="del">Delete</button></td>';
  tr.querySelector('button').onclick=async()=>{if(!confirm('Delete '+c.code+'?'))return;await api('/admin/api/codes?code='+encodeURIComponent(c.code),{method:'DELETE'});load()};t.appendChild(tr)}
 const ct=$('creators');ct.innerHTML='<tr><th>Creator</th><th>Supporters</th></tr>';
 for(const [n,v] of Object.entries(STATE.creators)){const tr=document.createElement('tr');tr.innerHTML='<td>'+n+'</td><td>'+v+'</td>';ct.appendChild(tr)}}
async function createCode(){try{const r=await api('/admin/api/codes',{method:'POST',body:JSON.stringify({type:$('type').value,code:$('code').value,
 creator:$('creator').value,rewards:picked('rw'),uses:$('uses').value,note:$('note').value})});
 msg('createmsg','Created '+r.code.code,true);$('code').value='';load()}catch(e){msg('createmsg',e.message,false)}}
async function saveSettings(){try{await api('/admin/api/settings',{method:'POST',body:JSON.stringify({locked:picked('lk'),licenseRequired:$('lic').checked})});
 msg('setmsg','Saved',true);load()}catch(e){msg('setmsg',e.message,false)}}
$('key').value=KEY;typeChanged();if(KEY)load();
</script></body></html>`;
