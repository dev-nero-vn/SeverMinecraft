# Deploy TIDEUS LINK on Linux

1. Install Docker Engine and Docker Compose plugin.
2. Clone the repository and enter `tideus-link`.
3. Create a private `.env` file from `.env.example`.
4. Set `DASHBOARD_PASSWORD` to a unique 16+ character password.
5. Set `DASHBOARD_SESSION_SECRET` to at least 32 random bytes.
6. Run `docker compose up -d --build`.
7. Verify `docker compose ps` and `curl http://127.0.0.1:8787/api/health`.
8. Configure a reverse proxy with HTTPS before remote access.

Do not publish port 8787 directly to the public internet. The Compose file binds to loopback by default; expose it only through an HTTPS reverse proxy and firewall rules.

The data volume stores the activity journal. Back it up privately. Do not include `.env` or provider secrets in Git.

Provider connections remain disabled until each official OAuth flow, webhook signature verification, and reply-delivery test is implemented and verified.
