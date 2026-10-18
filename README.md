# AuraLink (Fabric 1.21.11, server-side only)

Two commands for players:

| Command   | What it does |
|-----------|--------------|
| `/link`   | Gives a code. Enter it in Discord with `/link code:XXXXXX` to connect your accounts. |
| `/redeem` | Gives you the diamonds you earned from inviting people to the Discord. |

Players don't need any mod installed - it only runs on the server.
All the real logic (codes, balances) lives in the Discord bot; the mod just
passes the request along and shows the bot's reply.

## 1. Set up the bot

On your bot host (the same place you set `DISCORD_TOKEN`), add:

- `LINK_API_SECRET` = a long random password (30+ characters). Keep it private.
- `LINK_API_PORT` = a port the bot can listen on. (If your host already sets
  `SERVER_PORT`, you can skip this - the bot uses it automatically.)

Restart the bot. The console should say:
`Minecraft link API listening on port ...`

Your host must let that port be reached from the internet (on panels this is
usually an "allocation" in the Network tab). Note the bot's IP and the port.

## 2. Install the mod

1. Build it (see below) and put `auralink-1.0.0.jar` in the Minecraft server's
   `mods` folder, next to Fabric API.
2. Start the server once. It creates `config/auralink.json`.
3. Edit that file:
   ```json
   {
     "apiUrl": "http://BOT-IP:PORT/api",
     "apiSecret": "the same LINK_API_SECRET as the bot",
     "cooldownSeconds": 5
   }
   ```
4. Restart the server.

## 3. Try it

1. In Minecraft: `/link` -> you get a code.
2. In Discord: `/link code:ABC123` -> "Linked!"
3. Invite someone to the Discord (they join with your invite link).
4. In Minecraft: `/redeem` -> diamonds appear in your inventory.

`/diamonds` in Discord shows how many you have waiting.

## Building the jar

**Option A - no installs (GitHub):** make a GitHub repository, upload everything
in this folder (including the hidden `.github` folder), open the repo's
**Actions** tab, click the finished "build" run, and download **Artifacts**.
Use the `auralink-1.0.0.jar` inside (not the `-sources` one).

**Option B - on your PC:** install JDK 21 or newer, open a terminal in this
folder and run `./gradlew build` (`gradlew.bat build` on Windows).
The jar is in `build/libs/`.

## Notes

- The connection is plain HTTP. Requests are *signed* with the secret (so
  nobody can fake them) but not encrypted.
- To change the messages players see, edit the bot - the mod just displays
  whatever the bot replies.
