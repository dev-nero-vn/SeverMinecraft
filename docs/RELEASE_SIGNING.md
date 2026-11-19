# Ký bản phát hành SD Micro Tool

**Không đưa keystore, mật khẩu hoặc base64 keystore vào repository.** Keystore là khóa sở hữu ứng dụng; mất khóa có thể khiến bản cài mới không cập nhật được bản đang dùng.

## 1. Tạo khóa trên máy Windows của bạn

Mở PowerShell trong thư mục dự án và chạy:

```powershell
keytool -genkeypair -v -keystore sdmicrotool-release.jks -alias sdmicrotool -keyalg RSA -keysize 4096 -validity 10000 -sigalg SHA256withRSA -dname "CN=SD Micro Tool, OU=Release, O=SD Micro Tool"
```

Tự đặt mật khẩu mạnh khi keytool hỏi. Hãy sao lưu `sdmicrotool-release.jks` vào nơi an toàn ngoại tuyến; không gửi khóa cho người khác.

## 2. Thêm GitHub Actions secrets

Vào repository **Settings → Secrets and variables → Actions → New repository secret**, tạo bốn secret:

- `SIGNING_KEYSTORE_BASE64`: nội dung base64 của file JKS. PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("sdmicrotool-release.jks"))`
- `SIGNING_STORE_PASSWORD`: mật khẩu keystore
- `SIGNING_KEY_ALIAS`: `sdmicrotool`
- `SIGNING_KEY_PASSWORD`: mật khẩu key

Không dán mật khẩu vào issue, commit, chat công khai hoặc file trong repo.

## 3. Quy trình phát hành

Mỗi lần push lên `main`, Actions luôn build APK debug. Khi đủ bốn secrets, workflow còn:
- Tăng versionCode theo GitHub Actions run number.
- Build APK release và AAB release bằng cùng keystore.
- Kiểm chứng chữ ký bằng apksigner.
- Tạo GitHub Release có APK, AAB và ZIP.
- Cập nhật `version.json` gồm version, versionCode, downloadUrl, changelog, mandatory, fingerprint chứng chỉ và SHA-256 APK.

Nếu thiếu secrets, bước release được bỏ qua có cảnh báo; không có bản release đã ký.

## 4. Bảo toàn chữ ký

Không thay hoặc tạo lại keystore sau khi phát hành bản đầu. Bản cập nhật phải dùng đúng khóa đã ký bản cài trước đó. APK updater kiểm tra package name, versionCode, SHA-256 APK (nếu manifest cung cấp), và fingerprint chứng chỉ trước khi mở trình cài đặt Android.
