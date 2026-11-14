# TheRabbits server

A small Cloudflare Worker for TheRabbits Client. It runs on Cloudflare's free plan and does:
- **Cosmetics sync** – everyone with the same Server URL sees each other's capes, hats, claws and shields.
- **Codes** – cosmetic unlock codes, license keys and creator codes. Only you can make them (admin key).
- **Friends & parties** – every player gets a friend code; parties have join codes.
- **Admin panel** – a web page at `<your worker URL>/admin`.

## Set it up (in the browser, about 5 minutes)
1. Make a free account at https://dash.cloudflare.com
2. **Storage & Databases -> KV -> Create** a namespace named `CAPES`.
3. **Compute (Workers) -> Create -> Create Worker**, name it `therabbits-cosmetics`, click **Deploy**.
4. Click **Edit code**, delete everything, paste the contents of `worker.js`, click **Deploy**.
5. In the worker: **Settings -> Bindings -> Add -> KV namespace**: variable name `CAPES`, namespace `CAPES`. Save.
6. In the worker: **Settings -> Variables and Secrets -> Add**: type **Secret**, name `ADMIN_KEY`,
   value = a long password only you know. Save. (This is what lets you make codes.)
7. Copy the worker's URL (looks like `https://therabbits-cosmetics.<you>.workers.dev`).

## Connect the client
In game: **Right Shift -> Cosmetics -> Capes -> gear -> Server URL**, paste the URL.
To make it automatic for everyone, put the URL as the default in `CapesModule` before building.

## Make codes (admin panel)
Open `https://therabbits-cosmetics.<you>.workers.dev/admin`, paste your `ADMIN_KEY`, click **Connect**.
- **Cosmetic unlock** – pick which cosmetics the code gives. Lock those cosmetics in
  *Locked cosmetics* so only people with a code can wear them (everything else stays free).
- **License key** – tick *Require a license key to use the client*; players then have to enter a key
  before they can play. (Keys need a premium Minecraft account, because the server checks the
  account with Mojang. A license check inside a mod can't stop someone who edits the jar – it keeps
  honest players honest.)
- **Creator code** – type the creator's name; the *Creator codes* table shows how many players support each one.
- **Uses** – leave empty for unlimited, or set a number. Each player can only use a code once.

## Players
**Right Shift -> Cosmetics -> Account & Codes**: Connect, redeem codes, see your friend code,
add friends by their code, create/join/leave a party.
