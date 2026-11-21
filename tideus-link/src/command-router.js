const COMMANDS = [
  { name: "help", usage: "!help", description: "Hướng dẫn sử dụng", category: "Chung" },
  { name: "menu", usage: "!menu", description: "Danh sách lệnh khả dụng", category: "Chung" },
  { name: "ping", usage: "!ping", description: "Kiểm tra phản hồi", category: "Chung" },
  { name: "status", usage: "!status", description: "Trạng thái bộ máy", category: "Chung" },
  { name: "getlink", usage: "!getlink", description: "Lấy URL tệp đính kèm", category: "Tệp" },
  { name: "chat", usage: "!chat on | off | status", description: "Bật/tắt phiên chat riêng", category: "Trò chuyện" }
];

const chatSessions = new Map();

export function getCommandRegistry() {
  return COMMANDS.map((command) => ({ ...command }));
}

export function parseCommand(content, prefix = "!") {
  if (typeof content !== "string" || !content.trim().startsWith(prefix)) return null;
  const parts = content.trim().slice(prefix.length).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return null;
  return { name: parts[0].toLowerCase(), args: parts.slice(1), raw: content.trim() };
}

export function levenshtein(left, right) {
  const a = String(left);
  const b = String(right);
  const row = Array.from({ length: b.length + 1 }, (_, index) => index);
  for (let i = 1; i <= a.length; i += 1) {
    let previous = row[0];
    row[0] = i;
    for (let j = 1; j <= b.length; j += 1) {
      const old = row[j];
      row[j] = Math.min(row[j] + 1, row[j - 1] + 1, previous + (a[i - 1] === b[j - 1] ? 0 : 1));
      previous = old;
    }
  }
  return row[b.length];
}

export function suggestCommands(input, limit = 3) {
  const query = String(input || "").toLowerCase().trim();
  if (!query) return [];
  return COMMANDS
    .map((command) => ({ ...command, distance: levenshtein(query, command.name) }))
    .filter((command) => command.distance <= Math.max(2, Math.floor(query.length * 0.45)))
    .sort((a, b) => a.distance - b.distance || a.name.localeCompare(b.name))
    .slice(0, limit);
}

export function buildTextMenu() {
  const groups = new Map();
  for (const command of COMMANDS) {
    if (!groups.has(command.category)) groups.set(command.category, []);
    groups.get(command.category).push(command);
  }
  const lines = ["TIDEUS LINK — COMMAND MENU", "Các lệnh hiện được đăng ký:"];
  for (const [category, commands] of groups) {
    lines.push("", category);
    for (const command of commands) lines.push(`${command.usage} — ${command.description}`);
  }
  return lines.join("\n");
}

function scopeKey(event) {
  return [event.platform, event.accountId, event.conversationId].map((part) => String(part || "")).join(":");
}

export function clearChatSessions() {
  chatSessions.clear();
}

export function handleInboundMessage(event, services = {}) {
  const platform = String(event.platform || "unknown");
  const accountId = String(event.accountId || "default");
  const conversationId = String(event.conversationId || "unknown");
  const userId = String(event.userId || "");
  const text = String(event.text || "");
  const key = scopeKey({ platform, accountId, conversationId });
  const activeOwner = chatSessions.get(key);

  if (activeOwner && activeOwner !== userId) {
    return { handled: true, ignored: true, reply: null, reason: "private_chat_owned_by_another_user", deleteCommand: false };
  }

  const parsed = parseCommand(text);
  if (!parsed) {
    if (activeOwner === userId) {
      if (typeof services.aiReply !== "function") {
        return { handled: true, ignored: false, reply: "TIDEUS AI chưa được cấu hình; mình chưa thể trả lời nội dung chat.", status: "ai_not_configured" };
      }
      return { handled: true, ignored: false, reply: null, pendingAi: true };
    }
    return { handled: false, ignored: false, reply: null };
  }

  const command = COMMANDS.find((item) => item.name === parsed.name);
  if (!command) {
    return {
      handled: true,
      ignored: false,
      reply: `Không tìm thấy lệnh !${parsed.name}. Bạn muốn dùng lệnh nào?\n${suggestCommands(parsed.name).map((item) => item.usage).join("\n") || "Dùng !help để xem hướng dẫn."}`,
      status: "unknown_command",
      deleteCommand: false
    };
  }

  const base = { handled: true, ignored: false, deleteCommand: true, status: "ok" };
  switch (parsed.name) {
    case "help":
    case "menu":
      return { ...base, reply: buildTextMenu() };
    case "ping":
      return { ...base, reply: "TIDEUS LINK core đang phản hồi." };
    case "status":
      return { ...base, reply: "TIDEUS LINK Core: online. Các plugin nhắn tin sẽ chỉ được bật sau khi xác thực kết nối chính thức." };
    case "getlink": {
      const attachments = Array.isArray(event.attachments) ? event.attachments : [];
      const urls = attachments.map((item) => item?.url).filter((url) => typeof url === "string" && /^https:\/\//i.test(url));
      return { ...base, reply: urls.length ? urls.map((url, index) => `Ảnh/tệp ${index + 1}: ${url}`).join("\n") : "Tin nhắn chưa có tệp đính kèm HTTPS để lấy liên kết.", status: urls.length ? "ok" : "no_attachment" };
    }
    case "chat": {
      const action = (parsed.args[0] || "status").toLowerCase();
      if (action === "on") {
        chatSessions.set(key, userId);
        return { ...base, reply: "Đã bật phiên chat riêng. Chỉ tài khoản khởi tạo phiên này được bot phản hồi trong cuộc trò chuyện hiện tại." };
      }
      if (action === "off") {
        if (activeOwner !== userId) {
          return { ...base, deleteCommand: false, reply: "Bạn không phải người khởi tạo phiên chat này." };
        }
        chatSessions.delete(key);
        return { ...base, reply: "Đã tắt phiên chat riêng." };
      }
      if (action === "status") {
        return { ...base, deleteCommand: false, reply: activeOwner === userId ? "Phiên chat riêng của bạn đang bật." : "Phiên chat riêng của bạn đang tắt." };
      }
      return { ...base, deleteCommand: false, status: "invalid_argument", reply: "Cú pháp: !chat on | !chat off | !chat status" };
    }
    default:
      return { ...base, deleteCommand: false, status: "unavailable", reply: "Lệnh này hiện chưa được triển khai." };
  }
}
