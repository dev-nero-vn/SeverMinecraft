# TIDEUS LINK — Linux Control Center

Node.js 22 service with a desktop-style web dashboard, owner login, live activity feed, and an optional Discord bot adapter.

## Implemented in this starter

- Owner dashboard login with an HMAC-signed, HttpOnly session cookie.
- Health and service-status endpoints.
- Live activity feed (polling from the dashboard).
- Discord commands: `!help`, `!menu`, `!ping`, `!status`, `!getlink`, and Owner-only `!bot on/off/status`.
- `!getlink` returns the actual attachment URL supplied by Discord.
- Dynamic command list is generated from the command registry.
- Valid command messages can be deleted when Discord grants the bot `Manage Messages`.
- Unknown-command suggestions are based on edit distance.
- Tests cover parsing, permissions, and command suggestions.

## Not yet connected

Zalo OA, Messenger Page, TikTok Business Messaging, OAuth account linking, remote API-key rotation, external video download, full redirect extraction, AI chat, image generation, and production deployment each require their own authorized provider credentials, platform approvals, and integration tests. The dashboard reports these as not connected; it does not fake successful connections.

Gemini API is not included. Do not paste API keys into chat or commit keys to Git. Any future provider secret must be entered directly into a server-side secret manager. The exposed key previously pasted should be revoked.

## Requirements

- Linux server
- Node.js 22+
- Discord bot application/token for live Discord tests
- Discord Message Content Intent enabled for prefix commands
- Bot installed in the target server with View Channels, Send Messages, Read Message History, and optionally Manage Messages permissions

## Run

```bash
cd tideus-link
cp .env.example .env
npm install
# Edit .env with a unique dashboard password, session secret, and Discord bot token.
npm run build
npm test
npm start
```

Open `http://SERVER:8787` from a trusted network. Put the service behind HTTPS before exposing it publicly. For production, use a process manager such as systemd and a reverse proxy with TLS.

## Security

- Never commit `.env`.
- Use a firewall and HTTPS.
- Restrict Owner IDs to trusted Discord IDs.
- Do not enable privileged Discord intents unless needed.
- Do not expose the dashboard directly to the public internet without TLS and additional network controls.
