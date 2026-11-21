# TIDEUS LINK — Linux Control Center

TIDEUS LINK is being built as a Linux-hosted, desktop-style control center for connected messaging platforms.

## Implemented foundation

- Desktop-style Vietnamese web dashboard.
- Owner login using an HMAC-signed, HttpOnly session cookie.
- Health and service-status endpoints.
- Persistent activity journal stored outside the source tree.
- Plugin registry for Zalo Official Account, Messenger/Facebook Page, and TikTok Business Messaging.
- Plugin connection states are truthful: each provider remains **not connected** until its official OAuth/webhook integration is configured and verified.
- Core command router with per-user/per-conversation chat-session isolation and smart command suggestions.
- `!menu`, `!help`, `!status`, `!ping`, `!getlink`, and `!chat on/off/status` handlers are implemented in the platform-neutral command core.
- Local automated tests for sessions, command recognition, chat ownership, plugin status, and security primitives.

## Not yet connected / not represented as complete

Zalo OA OAuth + webhook signature verification, Messenger Page OAuth + webhook verification, TikTok Business Messaging access, media download pipelines, production reply delivery, AI chat, GitHub AI developer tools, and app auto-update require real provider credentials, approval, server deployment, and end-to-end tests. The dashboard does not fake successful connections or message delivery.

No API key pasted into chat is used. The previously exposed provider key should be revoked. Do not commit secrets to GitHub. Enter future provider credentials directly into a server-side secret store after the relevant provider requirements are confirmed.

## Requirements

- Linux server
- Node.js 22+
- HTTPS reverse proxy before exposing the dashboard publicly
- Strong `DASHBOARD_PASSWORD` and random `DASHBOARD_SESSION_SECRET`

## Run locally

```bash
cd tideus-link
cp .env.example .env
npm install
# Edit .env and set a unique dashboard password and random session secret.
npm run build
npm test
npm start
```

Open `http://127.0.0.1:8787` or use a trusted internal network. Do not expose the service to the public internet without TLS, firewall rules, and a reverse proxy.

## Linux deployment

A Dockerfile and Compose file are provided. Persist `/app/data`, keep `.env` outside Git, and use a managed runtime with restart-on-failure. Back up the data directory securely.

## Security

- `.env` is ignored by Git.
- HttpOnly, SameSite cookies; Secure cookies in production.
- Login throttling.
- Provider webhooks must verify each provider's signature before processing events.
- Store OAuth tokens encrypted at rest and grant only required scopes.
- Do not log message secrets, API keys, or full URLs containing credentials.
- The app must only process conversations and accounts that the Owner has explicitly connected and authorized.
