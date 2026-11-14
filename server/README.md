# TheRabbits cosmetics server

A tiny Cloudflare Worker that remembers which TheRabbits cape each player wears, so every
TheRabbits player can see each other's capes. Free on Cloudflare's free plan.

## Set it up (in the browser, about 5 minutes)
1. Make a free account at https://dash.cloudflare.com
2. **Storage & Databases -> KV -> Create** a namespace named `CAPES`.
3. **Compute (Workers) -> Create -> Create Worker**, name it `therabbits-cosmetics`, click **Deploy**.
4. Click **Edit code**, delete everything, paste the contents of `worker.js`, click **Deploy**.
5. In the worker: **Settings -> Bindings -> Add -> KV namespace**: variable name `CAPES`, namespace `CAPES`. Save.
6. Copy the worker's URL (looks like `https://therabbits-cosmetics.<you>.workers.dev`).

## Connect the client
In game: **Right Shift -> Cosmetics -> Capes -> gear -> Server URL**, paste the URL.
Your cape is shared automatically when you pick one (you need to be in a world or server).
Everyone who uses TheRabbits with the same Server URL sees each other's capes.
To make it automatic for everyone, put the URL in `CapesModule` as the default before building.
