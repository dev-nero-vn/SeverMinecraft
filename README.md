# Tirror

**Tirror** là bộ ứng dụng Windows + Android để chiếu màn hình Android lên PC qua Wi‑Fi nội bộ.

## Tính năng

- PC hiển thị mã ghép nối sáu chữ số có thời hạn và QR.
- Android nhập mã hoặc quét QR; PC phải xác nhận yêu cầu ghép nối.
- Thiết bị đã tin cậy được lưu để chọn và kết nối lại.
- Android dùng quyền MediaProjection của hệ thống, mã hóa H.264 bằng MediaCodec và truyền video qua TCP nội bộ.
- Windows xác thực thiết bị bằng HMAC, nhận H.264 và giải mã bằng FFmpeg trong một cửa sổ xem màn hình riêng, có thể phóng to hoặc thu nhỏ.
- Giao diện Windows: Trang chủ, Thiết bị, Lịch sử, Cài đặt, Trợ giúp, Giới thiệu. Cửa sổ xem màn hình Android tự bật riêng khi bắt đầu chiếu.
- Giao diện Android tối ưu cảm ứng: Trang chủ/nhập mã/quét QR, Thiết bị, Lịch sử, Cài đặt, Trợ giúp, Giới thiệu và màn hình Đang chiếu.
- Không hỗ trợ USB. Kết nối chỉ qua Wi‑Fi.

## Build tự động

Mỗi lần push lên nhánh `main`, GitHub Actions sẽ:
1. Chạy kiểm thử ghép nối và bộ nhận H.264.
2. Build Tirror.exe trên Windows runner và smoke-test lúc khởi động.
3. Build APK Android và cài/mở trên Android emulator.
4. Đóng gói EXE + APK vào artifact `Tirror-APK-EXE-ZIP`.

APK CI là debug-signed để thử nghiệm. EXE Windows có thông tin nhà phát triển LEHAITRIEU trong thuộc tính file. Chữ ký số Authenticode chỉ được gắn khi cấu hình chứng thư Code Signing đáng tin cậy trong GitHub Actions; không dùng chứng thư tự ký cho phát hành công khai. Kiểm thử trên emulator không thay thế việc thử ghép nối với điện thoại Android thật và PC Windows thật trên cùng Wi‑Fi.

## Cách sử dụng

1. Mở Tirror trên PC và cho phép ứng dụng trên Windows Firewall ở mạng Private.
2. Mở Tirror trên Android cùng mạng Wi‑Fi.
3. Nhập mã hiển thị trên PC hoặc quét QR.
4. Chấp thuận yêu cầu ghép nối trên PC.
5. Chọn PC đã lưu, nhấn Chiếu lại/Bắt đầu chiếu và cấp quyền chia sẻ màn hình Android.
6. Cửa sổ xem màn hình sẽ tự bật riêng trên Windows; có thể đóng cửa sổ xem mà không ngắt phiên chiếu.

## Giới hạn hiện tại

- Chiếu video Android → Windows, chưa có điều khiển ngược chuột/bàn phím từ PC.
- Chưa chuyển tiếp âm thanh.
- MediaProjection bắt buộc Android xác nhận quyền chia sẻ màn hình trước phiên chiếu.
- Hiệu năng thực tế phụ thuộc bộ mã hóa phần cứng Android, độ ổn định Wi‑Fi và khả năng giải mã PC.

## Kiểm tra cục bộ

Từ thư mục source:
```bash
python -m pip install -r desktop/requirements.txt pytest
python -m compileall -q desktop tests
python -m pytest -q
```
