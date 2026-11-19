# Chính sách quyền riêng tư — SD Micro Tool

**Ngày cập nhật:** 19/11/2026

SD Micro Tool quản lý thẻ SD và các thư mục mà người dùng chủ động cấp quyền. Ứng dụng không root và không truy cập trái phép vùng dữ liệu riêng của ứng dụng khác.

## Dữ liệu được xử lý trên thiết bị
- URI và tên thư mục do người dùng chọn qua Android Storage Access Framework.
- Metadata của tệp trong thư mục được cấp quyền: tên, kích thước, thời gian sửa đổi, MIME type và URI tài liệu.
- Tên package game đã chọn, trạng thái theo dõi và tùy chọn tự động sao chép.
- Lịch sử chuyển: URI nguồn/đích, tên, dung lượng, SHA-256, thời điểm, trạng thái và dung lượng nguồn được giải phóng nếu người dùng xác nhận xóa.
- Thống kê app/data/cache của package khi người dùng tự bật Usage Access trong Cài đặt Android.
- URL và tên file người dùng nhập để tải tệp; thông tin phiên bản khi kiểm tra cập nhật.

Metadata theo dõi, hàng đợi và lịch sử được lưu trong cơ sở dữ liệu riêng của ứng dụng trên thiết bị. Ứng dụng không gửi metadata theo dõi game lên máy chủ trong phiên bản hiện tại.

## Quyền truy cập tệp và game
Người dùng chọn thư mục thông qua SAF. Ứng dụng chỉ hoạt động trong phạm vi quyền được Android cấp. Trên Android 11 trở lên, SAF không cho chọn Android/data/ và Android/obb/ của ứng dụng khác. Usage Access chỉ cung cấp thống kê dung lượng; không cấp quyền đọc file private.

## Theo dõi và tự động sao chép
Nếu người dùng bật chế độ tự động cho một thư mục đã cấp quyền, SD Micro Tool dùng WorkManager để quét định kỳ và có thể sao chép tệp mới/đã thay đổi sang SD. Android có thể trì hoãn tác vụ nền. Chế độ này giữ nguyên bản gốc, không thay đổi đường dẫn game và không xóa dữ liệu khi chưa có xác nhận.

## Chuyển, khôi phục và xóa
Tệp được sao chép theo luồng vào SD, xác minh SHA-256 bằng cách đọc lại bản đích, rồi ghi lịch sử. Khôi phục tạo bản sao mới tại thư mục người dùng chọn. Xóa bản gốc chỉ diễn ra sau xác nhận riêng; hệ thống ghi nhận dung lượng giải phóng khi thao tác xóa thành công.

## Kết nối mạng
Internet được dùng để tải tệp HTTPS do người dùng nhập và kiểm tra/tải bản cập nhật ứng dụng từ máy chủ phát hành. Tính năng đồng bộ Temporary Cloud Sync không được bật trong bản này. Không upload APK, game assets hoặc lịch sử Smart Game Storage lên GitHub.

## Lưu giữ và gỡ cài đặt
Database, cấu hình và lịch sử riêng của SD Micro Tool được Android lưu trong vùng dữ liệu riêng của ứng dụng và bị xóa khi gỡ ứng dụng. Các tệp người dùng đã ghi vào thư mục SD bên ngoài không tự bị xóa cùng ứng dụng; người dùng cần tự quản lý chúng.

## Chia sẻ và liên hệ
Ứng dụng hiện không tích hợp quảng cáo hoặc SDK phân tích bên thứ ba trong mã nguồn app đã rà soát. Không bán dữ liệu người dùng. Nhà phát triển cần bổ sung email hỗ trợ chính thức trước khi phát hành Google Play.
