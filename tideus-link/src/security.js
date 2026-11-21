import { createHmac, timingSafeEqual } from "node:crypto";

export function safeStringEqual(left, right) {
  const a = Buffer.from(String(left ?? ""));
  const b = Buffer.from(String(right ?? ""));
  if (a.length !== b.length || a.length === 0) return false;
  return timingSafeEqual(a, b);
}

function signature(payload, secret) {
  return createHmac("sha256", secret).update(payload).digest("base64url");
}

export function createSessionToken(subject, secret, ttlSeconds = 60 * 60 * 8, now = Date.now()) {
  if (!secret || Buffer.byteLength(secret) < 32) throw new Error("DASHBOARD_SESSION_SECRET must contain at least 32 bytes");
  const payload = Buffer.from(JSON.stringify({ sub: String(subject), exp: Math.floor(now / 1000) + ttlSeconds })).toString("base64url");
  return `${payload}.${signature(payload, secret)}`;
}

export function verifySessionToken(token, secret, now = Date.now()) {
  if (!token || !secret || Buffer.byteLength(secret) < 32) return null;
  const parts = String(token).split(".");
  if (parts.length !== 2) return null;
  const [payload, supplied] = parts;
  const expected = signature(payload, secret);
  if (!safeStringEqual(supplied, expected)) return null;
  try {
    const data = JSON.parse(Buffer.from(payload, "base64url").toString("utf8"));
    if (!data.sub || !Number.isFinite(data.exp) || data.exp <= Math.floor(now / 1000)) return null;
    return { subject: String(data.sub), expiresAt: data.exp };
  } catch {
    return null;
  }
}

export function parseCookies(header = "") {
  const result = {};
  for (const segment of String(header).split(";")) {
    const index = segment.indexOf("=");
    if (index < 0) continue;
    const key = segment.slice(0, index).trim();
    const value = segment.slice(index + 1).trim();
    if (!key) continue;
    try { result[key] = decodeURIComponent(value); } catch { /* Ignore malformed cookie values. */ }
  }
  return result;
}
