# NEXORA Game Space

**NEXORA** là ứng dụng Android Gaming Hub theo định hướng Landscape, phong cách Extreme Gaming / Cyber Future, nhận diện tím neon + cyan.

> Trạng thái hiện tại: nền tảng ban đầu. Đã có splash animation, quét và mở game Android được hệ thống phân loại. Các mô-đun Game Turbo nâng cao chưa được tuyên bố là hoàn thành.

## Định hướng
- Ứng dụng Android độc lập, ưu tiên xử lý trên thiết bị.
- Bố cục ngang (Landscape).
- Splash animation neon và tùy chọn bật/tắt hiệu ứng.
- Thư viện game quét ứng dụng Android có danh mục GAME và mở bằng launcher intent.
- Không giả lập số liệu hiệu năng, không vượt qua bảo vệ nhiệt hoặc quyền Android.

## Build
Yêu cầu JDK 17, Gradle 8.13 và Android SDK 36.

```bash
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions tạo `NEXORA.apk` và `NEXORA-Android.zip` (ZIP có APK bên trong) khi push lên `main` hoặc chạy thủ công workflow **NEXORA Android Build**.

Đây là bản debug để kiểm thử, chưa phải bản release đã ký. Bản phát hành cần keystore riêng, kiểm thử thiết bị thật và quy trình ký an toàn.

## Các mô-đun còn trong lộ trình
Floating Game Assistant, Game Profiles, FPS/performance monitor, screen capture, audio/network center, analytics và OEM compatibility. Những tính năng phụ thuộc Android/OEM/phần cứng chỉ được bật khi có hỗ trợ thực tế.

Thiết bị kiểm thử đầu tiên: Samsung Galaxy A07.
