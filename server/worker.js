// TheRabbits cosmetics server (Cloudflare Worker + KV).
//
//   GET  /cosmetics?uuids=<uuid>,...      -> { "<uuid>": {cape, hat, arms}, ... } (uuids without dashes, max 100)
//   GET  /capes?uuids=<uuid>,<uuid>,...   -> { "<uuid>": "<capeId>", ... }   (older clients)
//   GET  /challenge                       -> { "serverId": "<random>" }      (valid for 2 minutes, single use)
//   POST /cape  { name, serverId, cape, hat?, arms? }  -> { ok: true, uuid }
//
// Setting a cape uses the same check a Minecraft server does: the game tells Mojang it is joining
// "serverId", and this worker asks Mojang (hasJoined) which account that was. Nobody can set
// someone else's cape. KV binding name: CAPES.

const CAPES = ["none", "sheen_black", "sheen_white", "pulse", "scanline"];
const HATS = ["none", "fedora", "crown", "ears", "royal_crown"];
const ARMS = ["none", "claws"];

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json", "access-control-allow-origin": "*" },
  });

const isUuid = (s) => /^[0-9a-f]{32}$/.test(s);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/capes") {
      const ids = (url.searchParams.get("uuids") || "").toLowerCase().split(",").filter(isUuid).slice(0, 100);
      const out = {};
      await Promise.all(ids.map(async (id) => { out[id] = (await env.CAPES.get("cape:" + id)) || "none"; }));
      return json(out);
    }

    if (request.method === "GET" && url.pathname === "/cosmetics") {
      const ids = (url.searchParams.get("uuids") || "").toLowerCase().split(",").filter(isUuid).slice(0, 100);
      const out = {};
      await Promise.all(ids.map(async (id) => {
        const [cape, hat, arms] = await Promise.all(["cape:", "hat:", "arms:"].map((k) => env.CAPES.get(k + id)));
        out[id] = { cape: cape || "none", hat: hat || "none", arms: arms || "none" };
      }));
      return json(out);
    }

    if (request.method === "GET" && url.pathname === "/challenge") {
      const bytes = crypto.getRandomValues(new Uint8Array(10));
      const serverId = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
      await env.CAPES.put("ch:" + serverId, "1", { expirationTtl: 120 });
      return json({ serverId });
    }

    if (request.method === "POST" && url.pathname === "/cape") {
      let body;
      try { body = await request.json(); } catch { return json({ error: "bad json" }, 400); }
      const { name, serverId, cape } = body || {};
      const hat = body.hat === undefined ? null : body.hat;
      const arms = body.arms === undefined ? null : body.arms;
      if (hat !== null && !HATS.includes(hat)) return json({ error: "unknown hat" }, 400);
      if (arms !== null && !ARMS.includes(arms)) return json({ error: "unknown arms" }, 400);
      if (typeof name !== "string" || !/^[A-Za-z0-9_]{1,16}$/.test(name)) return json({ error: "bad name" }, 400);
      if (typeof serverId !== "string" || !/^[0-9a-f]{20}$/.test(serverId)) return json({ error: "bad serverId" }, 400);
      if (!CAPES.includes(cape)) return json({ error: "unknown cape" }, 400);
      if (!(await env.CAPES.get("ch:" + serverId))) return json({ error: "challenge expired" }, 403);
      await env.CAPES.delete("ch:" + serverId);

      const check = await fetch(
        "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
          encodeURIComponent(name) + "&serverId=" + encodeURIComponent(serverId));
      if (check.status !== 200) return json({ error: "login check failed" }, 403);
      const profile = await check.json();
      const uuid = String(profile.id || "").toLowerCase();
      if (!isUuid(uuid)) return json({ error: "login check failed" }, 403);

      if (cape === "none") await env.CAPES.delete("cape:" + uuid);
      else await env.CAPES.put("cape:" + uuid, cape);
      for (const [k, v] of [["hat:", hat], ["arms:", arms]]) {
        if (v === null) continue;
        if (v === "none") await env.CAPES.delete(k + uuid);
        else await env.CAPES.put(k + uuid, v);
      }
      return json({ ok: true, uuid });
    }

    if (url.pathname === "/") return json({ name: "TheRabbits cosmetics server", ok: true });
    return json({ error: "not found" }, 404);
  },
};
