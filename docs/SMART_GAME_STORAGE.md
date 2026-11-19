# Smart Game Storage — SD Micro Tool

## Chức năng hiện có trong bản Android
- Nhận diện có giới hạn các package đã khai báo: Roblox, Free Fire, Free Fire MAX và Liên Quân Mobile.
- Đọc thống kê appBytes/dataBytes/cacheBytes qua StorageStatsManager sau khi người dùng tự bật Usage Access trong Cài đặt Android.
- Người dùng chọn thư mục nguồn bằng Storage Access Framework. App chỉ quét và quản lý cây thư mục mà Android đã cấp quyền.
- Quét tối đa 500 tệp, độ sâu thư mục tối đa 8 cấp để tránh quét quá lâu trên điện thoại cấu hình thấp.
- Chuyển từng tệp do người dùng chọn sang thư mục SD đã được cấp quyền.
- Tệp tạm được tạo trực tiếp trên SD; tiến trình hiển thị dung lượng, %, tốc độ và ETA.
- Có tạm dừng, tiếp tục và hủy ở ranh giới khối dữ liệu.
- Sau khi ghi xong, app đọc lại tệp trên SD và so sánh SHA-256.
- Chỉ hỏi xóa bản gốc sau khi xác minh thành công. Bản gốc không tự bị xóa.
- Khi lỗi/hủy, bản gốc vẫn giữ nguyên; file tạm chưa hoàn tất được xóa.

## Phân loại tương thích
- **Thống kê:** đọc kích thước ứng dụng, dữ liệu và cache khi Usage Access được cấp.
- **Chuyển thủ công:** chỉ áp dụng cho tệp trong thư mục mà người dùng đã chọn và Android cho phép đọc/ghi.
- **Không hỗ trợ dữ liệu riêng:** app không thể đọc/di chuyển cache riêng hoặc Android/data/Android/obb của Roblox, Free Fire hay Liên Quân trên Android 11+.
- **Tự động chuyển game:** không bật trong bản này. Android không cung cấp luồng sự kiện đáng tin cậy để ứng dụng bên thứ ba theo dõi file riêng của game; giao diện không giả lập nút tự động.

## Giới hạn
Android 11 trở lên chặn ACTION_OPEN_DOCUMENT_TREE truy cập Android/data và Android/obb. Chức năng chuyển tệp chỉ dành cho file người dùng chủ động cấp quyền; nó không biến SD thành bộ nhớ chạy thay thế của game và không làm game tự đọc tài nguyên trên SD.

## Kiểm thử trước phát hành
- Build debug qua GitHub Actions.
- Cài mới trên thiết bị Android 11+.
- Cấp/không cấp Usage Access.
- Thử với thư mục nguồn chia sẻ mà người dùng sở hữu.
- Kiểm tra chuyển file nhỏ/lớn, tên trùng, thiếu dung lượng, rút SD, pause/resume/cancel.
- So sánh SHA-256 đích và kiểm tra bản gốc chỉ xóa sau xác nhận.
- Chạy thử Roblox/Free Fire trên máy thật chỉ để xác nhận app thống kê không làm ảnh hưởng game; không tuyên bố đã chuyển tài nguyên riêng nếu chưa có API hỗ trợ.
