import test from "node:test";
import assert from "node:assert/strict";
import { createSessionToken, parseCookies, safeStringEqual, verifySessionToken } from "../src/security.js";
import { buildTextMenu, clearChatSessions, handleInboundMessage, parseCommand, suggestCommands } from "../src/command-router.js";
import { getPlugin, listPlugins } from "../src/plugins.js";

const secret = "test-session-secret-which-is-at-least-32-bytes";

test("session token is signed, expires, and rejects tampering", () => {
  const now = 1_800_000_000_000;
  const token = createSessionToken("owner", secret, 60, now);
  assert.equal(verifySessionToken(token, secret, now)?.subject, "owner");
  assert.equal(verifySessionToken(token, secret, now + 61_000), null);
  assert.equal(verifySessionToken(token + "x", secret, now), null);
});

test("cookie parser handles multiple cookies and malformed values", () => {
  assert.deepEqual(parseCookies("a=hello; tideus_session=abc%3D; bad=%E0%A4%A"), { a: "hello", tideus_session: "abc=" });
  assert.equal(safeStringEqual("same", "same"), true);
  assert.equal(safeStringEqual("same", "other"), false);
});

test("commands parse case-insensitively and menu is generated from the registry", () => {
  assert.deepEqual(parseCommand("!GETLINK"), { name: "getlink", args: [], raw: "!GETLINK" });
  assert.match(buildTextMenu(), /!getlink/);
  assert.ok(suggestCommands("getlnk").some((item) => item.name === "getlink"));
});

test("getlink only returns HTTPS attachment URLs", () => {
  const result = handleInboundMessage({
    platform: "zalo", accountId: "oa-1", conversationId: "thread-1", userId: "user-1",
    text: "!getlink", attachments: [{ url: "https://cdn.example/image.jpg" }, { url: "http://unsafe.example/file" }]
  });
  assert.equal(result.status, "ok");
  assert.match(result.reply, /https:\/\/cdn\.example\/image\.jpg/);
  assert.doesNotMatch(result.reply, /unsafe\.example/);
});

test("chat session is private to the user who started it", () => {
  clearChatSessions();
  const base = { platform: "messenger", accountId: "page-1", conversationId: "thread-1" };
  const started = handleInboundMessage({ ...base, userId: "alice", text: "!chat on" });
  assert.match(started.reply, /Đã bật/);
  const other = handleInboundMessage({ ...base, userId: "bob", text: "Xin chào" });
  assert.equal(other.ignored, true);
  assert.equal(other.reply, null);
  const owner = handleInboundMessage({ ...base, userId: "alice", text: "Xin chào" });
  assert.equal(owner.status, "ai_not_configured");
  const stopped = handleInboundMessage({ ...base, userId: "alice", text: "!chat off" });
  assert.match(stopped.reply, /Đã tắt/);
  clearChatSessions();
});

test("unknown command suggests a close registered command", () => {
  const result = handleInboundMessage({
    platform: "zalo", accountId: "oa-1", conversationId: "thread-1", userId: "user-1", text: "!getlnk"
  });
  assert.equal(result.status, "unknown_command");
  assert.match(result.reply, /!getlink/);
});

test("only the supported platforms are listed; Discord is excluded", () => {
  const ids = listPlugins().map((plugin) => plugin.id);
  assert.deepEqual(ids, ["zalo", "messenger", "tiktok"]);
  assert.equal(getPlugin("discord"), null);
});
