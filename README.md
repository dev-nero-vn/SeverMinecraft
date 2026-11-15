# Nero Remote

Ứng dụng Android đôi để xem và điều khiển một thiết bị Android khác mà chủ máy đã cấu hình tin cậy.

## Thành phần
- `controller`: APK điều khiển.
- `host`: APK được cài trên điện thoại ở nhà.
- `shared`: ghép nối, báo hiệu Supabase Realtime và WebRTC.
- `website/`: trang tải hai APK.
- `supabase/`: tài liệu cấu hình backend.

## Kết nối
- Internet: Supabase Realtime để báo hiệu, WebRTC cho video và lệnh.
- WebRTC dùng STUN mặc định; TURN cần cấu hình để vượt các mạng NAT khó.
- LAN và Wi-Fi Direct là các chế độ cần triển khai/kiểm thử riêng, chưa được đánh dấu hoàn thành trong bản nền này.

## Quyền Android
Host cần quyền Trợ năng để thực hiện thao tác cảm ứng và quyền MediaProjection để chia sẻ màn hình. Host chạy foreground service có thông báo hiển thị. MediaProjection là quyền do Android kiểm soát; ứng dụng không thể ghi nhớ sự chấp thuận vĩnh viễn qua mọi phiên/khởi động lại. Sau 5 phút không có thao tác từ Controller, luồng video được tạm ngừng nhưng phiên capture được giữ mở khi Android cho phép; nút tiếp tục trong Controller có thể đánh thức luồng.

## Bảo mật
Mã ghép nối là bí mật truy cập; hãy dùng mã dài, không đăng công khai. Kênh Realtime công khai được đặt tên bằng SHA-256 của mã; video và dữ liệu WebRTC được bảo vệ bởi DTLS-SRTP. Thu hồi mã bằng cách đổi mã trên Host. Luôn có thông báo khi chia sẻ màn hình và nút dừng Host.

## Build
GitHub Actions cài JDK, Android SDK và Gradle rồi build:
- `:controller:assembleDebug`
- `:host:assembleDebug`

APK debug dùng để kiểm thử, chưa phải bản phát hành ký bằng khóa riêng. Không đưa APK lên trang tải như bản ổn định trước khi kiểm thử trên hai máy thật.

## Backend
Supabase project URL: `https://geacjfwatxmfzezkholp.supabase.co`
Ứng dụng dùng publishable/anon key, không dùng service-role key trong APK.
