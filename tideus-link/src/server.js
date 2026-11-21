import "dotenv/config";
import express from "express";
import helmet from "helmet";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { addActivity, getActivities, initializeActivityStore } from "./activity-store.js";
import { listPlugins } from "./plugins.js";
import { createSessionToken, parseCookies, safeStringEqual, verifySessionToken } from "./security.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const port = Number(process.env.PORT || 8787);
const host = process.env.HOST || "127.0.0.1";
const sessionSecret = process.env.DASHBOARD_SESSION_SECRET || (process.env.NODE_ENV === "production" ? "" : "development-only-session-secret-please-change");
const dashboardUser = process.env.DASHBOARD_USER || "owner";
const dashboardPassword = process.env.DASHBOARD_PASSWORD || "";
const cookieName = "tideus_session";
const loginAttempts = new Map();
let server;

if (process.env.NODE_ENV === "production") {
  if (!dashboardPassword || dashboardPassword.length < 16) throw new Error("Set a strong DASHBOARD_PASSWORD (16+ characters).");
  if (Buffer.byteLength(sessionSecret) < 32) throw new Error("Set DASHBOARD_SESSION_SECRET to at least 32 bytes.");
}

app.disable("x-powered-by");
app.use(helmet({ contentSecurityPolicy: {
  directives: {
    defaultSrc: ["'self'"],
    scriptSrc: ["'self'"],
    styleSrc: ["'self'"],
    imgSrc: ["'self'", "data:"],
    connectSrc: ["'self'"],
    objectSrc: ["'none'"],
    frameAncestors: ["'none'"],
    upgradeInsecureRequests: process.env.NODE_ENV === "production" ? [] : null
  }
}}));
app.use(express.json({ limit: "32kb" }));

function getClientKey(req) {
  return req.ip || req.socket.remoteAddress || "unknown";
}

function authenticated(req, res, next) {
  const token = parseCookies(req.headers.cookie)[cookieName];
  const session = verifySessionToken(token, sessionSecret);
  if (!session || session.subject !== dashboardUser) {
    return res.status(401).json({ error: "unauthorized", message: "Vui lòng đăng nhập lại." });
  }
  req.owner = session.subject;
  return next();
}

app.get("/api/health", (_req, res) => res.json({ ok: true, service: "tideus-link", timestamp: new Date().toISOString() }));

app.post("/api/login", async (req, res) => {
  const key = getClientKey(req);
  const attempt = loginAttempts.get(key) || { count: 0, blockedUntil: 0 };
  if (attempt.blockedUntil > Date.now()) {
    return res.status(429).json({ error: "rate_limited", message: "Quá nhiều lần đăng nhập. Hãy thử lại sau." });
  }
  const usernameOk = safeStringEqual(req.body?.username, dashboardUser);
  const passwordOk = safeStringEqual(req.body?.password, dashboardPassword);
  if (!usernameOk || !passwordOk || !dashboardPassword) {
    attempt.count += 1;
    if (attempt.count >= 5) {
      attempt.count = 0;
      attempt.blockedUntil = Date.now() + 60_000;
    }
    loginAttempts.set(key, attempt);
    await addActivity({ source: "dashboard", actor: "unknown", action: "login_failed", status: "warning", detail: "Thông tin đăng nhập không hợp lệ." });
    return res.status(401).json({ error: "invalid_credentials", message: "Tên đăng nhập hoặc mật khẩu không đúng." });
  }
  loginAttempts.delete(key);
  const token = createSessionToken(dashboardUser, sessionSecret, 8 * 60 * 60);
  res.cookie(cookieName, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 8 * 60 * 60 * 1000
  });
  await addActivity({ source: "dashboard", actor: dashboardUser, action: "login_success", status: "success", detail: "Owner đăng nhập dashboard." });
  return res.json({ ok: true, user: dashboardUser });
});

app.post("/api/logout", authenticated, async (req, res) => {
  res.clearCookie(cookieName, { httpOnly: true, secure: process.env.NODE_ENV === "production", sameSite: "strict", path: "/" });
  await addActivity({ source: "dashboard", actor: req.owner, action: "logout", status: "info", detail: "Owner đăng xuất." });
  return res.json({ ok: true });
});

app.get("/api/session", authenticated, (req, res) => res.json({ ok: true, user: req.owner }));

app.get("/api/status", authenticated, (_req, res) => {
  res.json({
    service: "online",
    runtime: process.version,
    platform: process.platform,
    uptimeSeconds: Math.floor(process.uptime()),
    ai: { enabled: false, status: "not_configured" },
    plugins: listPlugins()
  });
});

app.get("/api/plugins", authenticated, (_req, res) => res.json({ plugins: listPlugins() }));
app.get("/api/activities", authenticated, (req, res) => res.json({ activities: getActivities(req.query.limit) }));

app.post("/api/chat/send", authenticated, async (req, res) => {
  const message = String(req.body?.message || "").trim();
  if (!message) return res.status(400).json({ error: "empty_message", message: "Hãy nhập nội dung." });
  if (message.length > 4000) return res.status(413).json({ error: "message_too_long", message: "Tin nhắn quá dài." });
  await addActivity({ source: "ai-chat", actor: req.owner, action: "chat_request_received", status: "warning", detail: "AI chưa được kết nối; yêu cầu chưa được gửi tới nhà cung cấp." });
  return res.status(503).json({ error: "ai_not_configured", message: "TIDEUS AI chưa được cấu hình. Tin nhắn chưa được gửi và không có tác vụ nào được thực hiện." });
});

app.use(express.static(path.resolve(__dirname, "../public"), { index: "index.html", extensions: ["html"] }));
app.use((req, res) => {
  if (req.path.startsWith("/api/")) return res.status(404).json({ error: "not_found" });
  return res.sendFile(path.resolve(__dirname, "../public/index.html"));
});

async function start() {
  if (process.env.NODE_ENV !== "production" && !process.env.DASHBOARD_PASSWORD) {
    console.warn("Set DASHBOARD_PASSWORD in .env before using this dashboard.");
  }
  await initializeActivityStore();
  await addActivity({ source: "system", actor: "system", action: "service_started", status: "success", detail: "TIDEUS LINK Linux Control Center đã khởi động." });
  server = app.listen(port, host, () => console.log(`TIDEUS LINK listening on http://${host}:${port}`));
}

async function stop() {
  if (!server) process.exit(0);
  server.close(() => process.exit(0));
}

process.on("SIGINT", stop);
process.on("SIGTERM", stop);

if (process.env.NODE_ENV !== "test") start().catch((error) => {
  console.error("Failed to start TIDEUS LINK:", error.message);
  process.exitCode = 1;
});

export { app };
