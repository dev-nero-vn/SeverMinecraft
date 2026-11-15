const status = document.getElementById("release-status");
fetch("https://api.github.com/repos/dev-nero-vn/SeverMinecraft/releases/tags/v0.1.0")
  .then(r => r.ok ? r.json() : Promise.reject())
  .then(release => {
    const assets = new Set((release.assets || []).map(a => a.name));
    const ready = assets.has("controller-debug.apk") && assets.has("host-debug.apk");
    status.textContent = ready
      ? "Đã có hai APK thử nghiệm. Hãy đọc ghi chú phát hành trước khi cài."
      : "Chưa có đủ hai APK trong bản phát hành. Vui lòng quay lại sau khi GitHub Actions build thành công.";
    status.classList.toggle("ready", ready);
  })
  .catch(() => { status.textContent = "Bản build đầu tiên chưa được xuất bản."; });
