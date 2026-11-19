# NEXORA Game Space — Lộ trình

## Nền tảng ban đầu
- [x] Ứng dụng Android Kotlin độc lập.
- [x] Giao diện khóa Landscape.
- [x] Splash screen animation neon.
- [x] Bật/tắt hiệu ứng.
- [x] Quét ứng dụng được Android phân loại là game.
- [x] Mở game qua launcher intent.
- [x] GitHub Actions build debug APK và ZIP.

## Các giai đoạn tiếp theo
1. Gaming Hub: tìm kiếm, ghim game, danh mục và trạng thái trống.
2. Floating Game Assistant: overlay kéo/ẩn/hiện, xin quyền SYSTEM_ALERT_WINDOW theo cơ chế Android.
3. Game Profiles: cấu hình theo game và chỉ tự áp dụng tùy chọn có quyền.
4. Performance Monitor: chỉ hiển thị dữ liệu đo được; phân biệt số đo, ước tính và không hỗ trợ.
5. Screen Capture: MediaProjection và sự đồng ý của người dùng theo từng phiên.
6. Audio & Network Center.
7. Session Analytics.
8. OEM compatibility và kiểm thử Galaxy A07.

## Ranh giới kỹ thuật
Ứng dụng thông thường không thể tự thay đổi kernel, xung CPU/GPU, thuật toán cảm ứng OEM, giới hạn nhiệt hoặc dữ liệu riêng của game. Những chức năng phụ thuộc Android/OEM/phần cứng chỉ được bật khi có hỗ trợ thực tế. Không root, không vượt quyền bảo vệ Android, không vô hiệu hóa cơ chế bảo vệ nhiệt.

## Kiểm thử trước phát hành
- Build sạch trên GitHub Actions.
- Cài và mở APK trên thiết bị thật.
- Kiểm tra Landscape, splash animation và tùy chọn hiệu ứng.
- Kiểm tra quét/mở game và trường hợp game không được hệ thống phân loại.
- Đo pin, nhiệt, bộ nhớ và kiểm tra crash/ANR.
