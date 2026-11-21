const app = document.querySelector("#app");
const state = { user: null, page: "overview", status: null, plugins: [], activities: [] };

const icons = { overview:"⌂", live:"◷", chats:"▤", plugins:"⌘", ai:"✧", settings:"⚙" };
const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;" }[char]));
async function api(url, options = {}) {
  const response = await fetch(url, { credentials:"same-origin", headers:{ "Content-Type":"application/json", ...(options.headers || {}) }, ...options });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || data.error || `HTTP ${response.status}`);
  return data;
}
function toast(message) {
  const node = document.createElement("div");
  node.className = "toast";
  node.textContent = message;
  document.body.append(node);
  setTimeout(() => node.remove(), 3600);
}
function renderLogin(error = "") {
  app.innerHTML = `<section class="login-wrap"><form class="login-card" id="login-form">
    <div class="login-brand"><div class="brand-mark">T</div><div><strong>TIDEUS LINK</strong><span>Linux Control Center</span></div></div>
    <div class="eyebrow">SECURE OWNER ACCESS</div><h2>Đăng nhập quản trị</h2><p class="muted" style="font-size:12px;line-height:1.7">Đăng nhập để quản lý plugin, nhật ký hoạt động và các cuộc trò chuyện đã được kết nối.</p>
    <div class="field"><label>Tên đăng nhập</label><input name="username" autocomplete="username" required></div>
    <div class="field"><label>Mật khẩu</label><input name="password" type="password" autocomplete="current-password" required></div>
    <button class="primary" type="submit">Đăng nhập vào hệ thống</button><div class="error" id="login-error">${escapeHtml(error)}</div>
  </form></section>`;
  document.querySelector("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await api("/api/login", { method:"POST", body:JSON.stringify({ username:form.get("username"), password:form.get("password") }) });
      await loadApp();
    } catch (err) { document.querySelector("#login-error").textContent = err.message; }
  });
}
function shell(content) {
  const nav = [
    ["overview","Tổng quan"],["live","Live Activity"],["chats","Quản lý chat"],["plugins","Plugin Center"],["ai","AI Chat"],["settings","Cài đặt"]
  ];
  app.innerHTML = `<div class="layout">
    <aside class="sidebar">
      <div class="brand"><div class="brand-mark">T</div><div><div class="brand-name">TIDEUS LINK</div><div class="brand-sub">LINUX CONTROL CENTER</div></div></div>
      <div><div class="nav-group-title">WORKSPACE</div><nav class="nav-list">${nav.map(([id,label])=>`<button class="nav-btn ${state.page===id?"active":""}" data-page="${id}"><span class="nav-icon">${icons[id]}</span>${label}</button>`).join("")}</nav></div>
      <div class="sidebar-bottom"><div class="nav-group-title">SERVER</div><div class="server-pill"><span class="dot"></span><div><b>Linux Engine</b><div class="muted" style="font-size:9px;margin-top:3px">Control center online</div></div></div></div>
    </aside>
    <section class="main">
      <header class="topbar"><div class="breadcrumb">TIDEUS LINK <span style="color:#52647e">/</span> <b>${nav.find(([id])=>id===state.page)?.[1] || "Workspace"}</b></div>
        <div class="top-actions"><span class="status-tag">● SERVICE ONLINE</span><div class="owner-chip"><span class="avatar">O</span> OWNER</div><button class="icon-btn" id="logout">Đăng xuất</button></div></header>
      <div class="content">${content}</div>
    </section>
  </div>`;
  document.querySelectorAll("[data-page]").forEach(button => button.addEventListener("click", () => { state.page = button.dataset.page; renderPage(); }));
  document.querySelector("#logout").addEventListener("click", async () => { await api("/api/logout",{method:"POST"}).catch(()=>{}); state.user=null; renderLogin(); });
}
function pageHeading(title, description, tag = "LINUX CONTROL CENTER") {
  return `<div class="page-heading"><div><div class="eyebrow">${tag}</div><h1>${title}</h1><p>${description}</p></div><span class="status-tag">● HỆ THỐNG TRỰC TUYẾN</span></div>`;
}
function activityRows(items) {
  if (!items.length) return `<div class="empty">Chưa có hoạt động nào. Nhật ký sẽ xuất hiện khi có sự kiện thật.</div>`;
  return `<div class="activity-list">${items.map(item=>`<div class="activity-item"><div class="activity-symbol">◈</div><div><strong>${escapeHtml(item.action)} · ${escapeHtml(item.source)}</strong><p>${escapeHtml(item.detail)}<br>Người thực hiện: ${escapeHtml(item.actor)}</p></div><span class="activity-time">${new Date(item.timestamp).toLocaleString("vi-VN")}</span></div>`).join("")}</div>`;
}
function overviewPage() {
  const plugins = state.plugins;
  const connected = plugins.filter(p=>p.status==="connected").length;
  return pageHeading("Trung tâm điều khiển","Theo dõi dịch vụ Linux, các plugin và hoạt động gần đây.")+
  `<div class="grid stats-grid">
    <div class="card"><div class="stat-top"><span>Linux Service</span><span class="stat-icon">⌘</span></div><div class="stat-num" style="color:var(--green)">ONLINE</div><div class="stat-hint">Web control center đang chạy</div></div>
    <div class="card"><div class="stat-top"><span>Plugin đã kết nối</span><span class="stat-icon">⌁</span></div><div class="stat-num">${connected}/${plugins.length}</div><div class="stat-hint">Chỉ tính kết nối đã xác minh</div></div>
    <div class="card"><div class="stat-top"><span>Hoạt động ghi nhận</span><span class="stat-icon">◷</span></div><div class="stat-num">${state.activities.length}</div><div class="stat-hint">Sự kiện gần đây trong nhật ký</div></div>
    <div class="card"><div class="stat-top"><span>TIDEUS AI</span><span class="stat-icon">✧</span></div><div class="stat-num" style="font-size:21px">Chưa kết nối</div><div class="stat-hint">Không giả lập phản hồi AI</div></div>
  </div>
  <div class="grid two-col">
    <section class="card"><div class="card-head"><h3>Hoạt động gần đây</h3><button class="small-link" data-go="live">Xem nhật ký →</button></div>${activityRows(state.activities.slice(0,5))}</section>
    <section class="card"><div class="card-head"><h3>Plugin kết nối</h3><button class="small-link" data-go="plugins">Quản lý →</button></div><div class="platform-list">${plugins.map(plugin=>`<div class="platform-row"><div class="platform-logo">${plugin.id==="zalo"?"Z":plugin.id==="messenger"?"M":"T"}</div><div class="platform-copy"><strong>${escapeHtml(plugin.name)}</strong><span>${escapeHtml(plugin.status==="connected"?"Đã kết nối":"Chưa kết nối")}</span></div><span class="status off">CHƯA KẾT NỐI</span></div>`).join("")}</div></section>
  </div>`;
}
function livePage() {
  return pageHeading("Live Activity","Nhật ký sự kiện thực tế từ dashboard và các plugin đã kết nối.","MONITORING")+
  `<section class="card"><div class="card-head"><h3>Nhật ký hệ thống</h3><button class="small-link" id="refresh-activities">Làm mới ↻</button></div>${activityRows(state.activities)}</section>`;
}
function pluginsPage() {
  return pageHeading("Plugin Center","Kết nối các nền tảng nhắn tin qua luồng cấp quyền chính thức.","INTEGRATIONS")+
  `<div class="notice" style="margin-bottom:16px">Trạng thái “Chưa kết nối” là trạng thái thật. Mỗi nền tảng cần ứng dụng được đăng ký, quyền phù hợp và webhook HTTPS đã xác minh. Discord đã được loại khỏi dự án.</div>
  <div class="grid plugin-grid">${state.plugins.map(plugin=>`<section class="card plugin-card"><div class="platform-logo">${plugin.id==="zalo"?"Z":plugin.id==="messenger"?"M":"T"}</div><h3>${escapeHtml(plugin.name)}</h3><p>${escapeHtml(plugin.description)}</p><span class="status off">● CHƯA KẾT NỐI</span><h4 style="font-size:10px;margin:17px 0 5px">Yêu cầu kết nối</h4><ul class="requirements">${plugin.requirements.map(item=>`<li>${escapeHtml(item)}</li>`).join("")}</ul><button class="plugin-btn" disabled title="OAuth và webhook chưa được cấu hình">Kết nối sẽ được mở sau khi cấu hình API</button></section>`).join("")}</div>`;
}
function chatsPage() {
  return pageHeading("Quản lý chat","Tập trung các cuộc trò chuyện từ những tài khoản đã cấp quyền.","MESSAGING")+
  `<div class="chat-layout"><aside class="chat-list"><h3>Cuộc trò chuyện</h3><div class="chat-empty">Chưa có cuộc trò chuyện được đồng bộ.<br><br>Kết nối Zalo OA, Messenger Page hoặc TikTok Business để nhận sự kiện trong phạm vi API cho phép.</div></aside><section class="chat-room"><div class="chat-room-head">Hộp thư đa nền tảng</div><div class="chat-body"><div class="chat-placeholder"><div class="ai-orb">▤</div><h2>Chưa có tin nhắn</h2><p>TIDEUS LINK sẽ hiển thị tin nhắn sau khi plugin chính thức được kết nối và webhook được xác minh. Ứng dụng không tự đọc các cuộc trò chuyện cá nhân ngoài phạm vi được cấp quyền.</p></div></div></section></div>`;
}
function aiPage() {
  return pageHeading("TIDEUS AI Chat","Không gian trò chuyện kiểu trợ lý để giao việc và nhận báo cáo.","AI WORKSPACE")+
  `<div class="chat-layout"><aside class="chat-list"><h3>Lịch sử hội thoại</h3><div class="chat-empty">Chưa có cuộc trò chuyện AI.</div></aside><section class="chat-room"><div class="chat-room-head">TIDEUS AI · Trợ lý phát triển</div><div class="chat-body"><div class="chat-placeholder"><div class="ai-orb">✧</div><h2>AI chưa được kết nối</h2><p>Khung chat đã sẵn sàng về giao diện, nhưng chưa có nhà cung cấp AI được cấu hình và kiểm thử. Tin nhắn sẽ không được giả vờ xử lý.</p><span class="status warn">AI NOT CONFIGURED</span></div></div><form class="chat-input" id="ai-form"><textarea name="message" placeholder="Mô tả yêu cầu của bạn..." disabled></textarea><button type="submit" disabled>↑</button></form></section></div>`;
}
function settingsPage() {
  return pageHeading("Cài đặt hệ thống","Thông tin môi trường và nguyên tắc bảo mật.","SETTINGS")+
  `<div class="grid settings-grid"><section class="card"><h3>Thông tin máy chủ</h3><div class="setting-row"><span>Runtime</span><b>${escapeHtml(state.status?.runtime||"Node.js")}</b></div><div class="setting-row"><span>Platform</span><b>${escapeHtml(state.status?.platform||"Linux")}</b></div><div class="setting-row"><span>Uptime</span><b>${Math.floor(state.status?.uptimeSeconds||0)} giây</b></div></section><section class="card"><h3>Bảo mật</h3><div class="setting-row"><span>Owner session</span><span class="status ok">Được bảo vệ</span></div><div class="setting-row"><span>AI API Key</span><span class="status off">Chưa cấu hình</span></div></section></div>`;
}
function renderPage() {
  let content = state.page==="overview"?overviewPage():state.page==="live"?livePage():state.page==="chats"?chatsPage():state.page==="plugins"?pluginsPage():state.page==="ai"?aiPage():settingsPage();
  shell(content);
  document.querySelectorAll("[data-go]").forEach(button=>button.addEventListener("click",()=>{state.page=button.dataset.go;renderPage();}));
  document.querySelector("#refresh-activities")?.addEventListener("click",refreshData);
  document.querySelector("#ai-form")?.addEventListener("submit",async event=>{event.preventDefault();toast("AI chưa được kết nối; chưa có yêu cầu nào được thực thi.");});
}
async function refreshData() {
  const [status, plugins, activities] = await Promise.all([api("/api/status"),api("/api/plugins"),api("/api/activities?limit=100")]);
  state.status=status;state.plugins=plugins.plugins;state.activities=activities.activities;
}
async function loadApp() {
  try { const session=await api("/api/session");state.user=session.user;await refreshData();renderPage(); }
  catch { renderLogin(); }
}
loadApp().catch(error=>renderLogin(error.message));
setInterval(async()=>{if(state.user){try{const result=await api("/api/activities?limit=100");state.activities=result.activities;if(state.page==="live"||state.page==="overview")renderPage();}catch{/* Session may have expired; the next API action will prompt login. */}}},5000);
