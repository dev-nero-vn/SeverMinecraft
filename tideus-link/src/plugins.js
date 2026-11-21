const PLUGINS = [
  {
    id: "zalo",
    name: "Zalo Official Account",
    kind: "Messaging",
    status: "not_connected",
    description: "Kết nối OA qua quy trình cấp quyền chính thức và webhook đã xác minh.",
    requirements: ["Zalo OA", "Ứng dụng được cấp quyền", "Webhook HTTPS và chữ ký hợp lệ"]
  },
  {
    id: "messenger",
    name: "Messenger / Facebook Page",
    kind: "Messaging",
    status: "not_connected",
    description: "Nhận và trả lời tin nhắn của Page đã được chủ sở hữu cho phép.",
    requirements: ["Facebook Page", "Meta App", "Quyền nhắn tin và webhook được phê duyệt"]
  },
  {
    id: "tiktok",
    name: "TikTok Business Messaging",
    kind: "Messaging",
    status: "not_connected",
    description: "Tích hợp tin nhắn doanh nghiệp khi tài khoản và ứng dụng đủ điều kiện.",
    requirements: ["TikTok Business", "Ứng dụng được duyệt", "Quyền Business Messaging"]
  }
];

export function listPlugins() {
  return PLUGINS.map((plugin) => ({ ...plugin, requirements: [...plugin.requirements] }));
}

export function getPlugin(id) {
  const plugin = PLUGINS.find((item) => item.id === id);
  return plugin ? { ...plugin, requirements: [...plugin.requirements] } : null;
}
