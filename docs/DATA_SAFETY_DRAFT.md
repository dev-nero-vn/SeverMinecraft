# Bản khai nháp Google Play Data safety — SD Micro Tool

Đây là bản rà soát kỹ thuật nội bộ, không phải biểu mẫu đã nộp. Cần đối chiếu đúng phiên bản cuối và thư viện được đóng gói trước khi khai báo trên Play Console.

## Xử lý cục bộ
- URI thư mục SAF, metadata file, danh sách queue, lịch sử và hash được lưu cục bộ trong database riêng của ứng dụng.
- Usage Access được dùng để truy vấn thống kê dung lượng app/data/cache của package được hiển thị.
- Metadata cục bộ không được upload lên backend trong phiên bản này.
- Tệp người dùng chọn được sao chép giữa các URI SAF trên cùng thiết bị; không gửi nội dung file lên cloud.
- Temporary Cloud Sync chưa bật.

## Truyền dữ liệu mạng
- INTERNET: tải HTTPS do người dùng cung cấp và kiểm tra/tải manifest APK release.
- Không có quảng cáo hoặc analytics trong mã nguồn app đã rà soát.
- Không nhúng GitHub token trong APK.
- Không upload APK, tài nguyên game hoặc dữ liệu game lên GitHub JSON.

## Quyền nhạy cảm / quyền hệ thống
- PACKAGE_USAGE_STATS: người dùng tự bật trong Cài đặt; dùng đọc thống kê package. Quyền này không cho phép đọc dữ liệu file private.
- POST_NOTIFICATIONS: thông báo trạng thái theo dõi và tiến trình queue.
- FOREGROUND_SERVICE / FOREGROUND_SERVICE_DATA_SYNC: duy trì các tác vụ chuyển tệp do người dùng hoặc chế độ tự động đã bật khởi chạy.
- SAF persistable URI grants: chỉ các thư mục người dùng tự chọn.
- REQUEST_INSTALL_PACKAGES: trình cài đặt Android cho bản cập nhật của chính ứng dụng; người dùng xác nhận cài đặt.

## Cần xác nhận trước khi nộp
1. Chốt Data safety theo SDK và các thư viện trong APK/AAB cuối.
2. Xác định chính xác dữ liệu nào được thu thập/chia sẻ (hiện tại không truyền metadata tracking ra ngoài thiết bị).
3. Host chính sách quyền riêng tư bằng URL công khai.
4. Bổ sung thông tin liên hệ hỗ trợ.
5. Kiểm thử trên thiết bị thật và rà lại permissions/manifest merged.
