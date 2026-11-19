# SD Micro Tool

Ứng dụng Android quản lý thẻ SD, bộ nhớ và các tệp người dùng chủ động cấp quyền.

## Chức năng
- Chọn thư mục SD bằng Storage Access Framework.
- Tải HTTPS theo luồng trực tiếp vào file tạm trên SD.
- Duyệt và quản lý tệp trong thư mục được cấp quyền.
- Dashboard dung lượng bộ nhớ trong và thẻ SD.
- Smart Game Storage: nhận diện package Roblox, Free Fire, Free Fire MAX và Liên Quân; thống kê app/data/cache khi có Usage Access.
- Theo dõi thư mục được cấp quyền bằng quét định kỳ WorkManager.
- Tự động sao chép tệp mới/đã thay đổi sang SD khi người dùng bật; bản gốc luôn được giữ.
- Hàng đợi chuyển lưu trữ, thông báo foreground, tiến trình, tốc độ, ETA, tạm dừng, tiếp tục, hủy và thử lại.
- Xác minh SHA-256, lịch sử, khôi phục và báo cáo dung lượng đã giải phóng thực tế.
- In-app updater kiểm tra version manifest, package, checksum và chứng thư ký trước khi mở Android Package Installer.

## Giới hạn Android
- Yêu cầu Android 11 trở lên, không root.
- Android 11+ chặn SAF truy cập Android/data/ và Android/obb/ của game khác.
- Smart Game Storage chỉ quản lý các thư mục/tệp người dùng chọn và Android cho phép truy cập.
- Chế độ tự động sao chép không thay đổi đường dẫn mà game sử dụng và không tự giải phóng bộ nhớ trong.
- Không tuyên bố chuyển được cache private hoặc tài nguyên riêng của Roblox/Free Fire/Liên Quân.
- Tác vụ nền WorkManager có chu kỳ tối thiểu 15 phút và có thể bị Android trì hoãn vì pin/Doze.
- Dữ liệu thống kê package yêu cầu người dùng cấp Usage Access; quyền đó không mở khóa file private.
- APK, database và dữ liệu riêng của SD Micro Tool vẫn dùng một phần bộ nhớ trong.

## Tài liệu
- docs/SMART_GAME_STORAGE.md
- docs/RELEASE_SIGNING.md
- docs/DATA_SAFETY_DRAFT.md
- PRIVACY_POLICY.md

## Build
Mở repository bằng Android Studio hoặc chạy gradle :app:assembleDebug.

GitHub Actions build APK debug và đóng gói ZIP sau mỗi lần push lên main.

## Release
Bản release APK/AAB phải dùng keystore cố định do chủ ứng dụng quản lý. Không commit keystore hoặc mật khẩu vào repository. Làm theo docs/RELEASE_SIGNING.md.

## Google Play
Trước khi phát hành cần có AAB Release có chữ ký, hoàn thiện Data safety theo bản cuối, địa chỉ/email hỗ trợ, chính sách quyền riêng tư được host công khai và kiểm thử thiết bị thật. Bản debug không phải bản phát hành Play.
