# Chính sách quyền riêng tư — SD Micro Tool

**Ngày cập nhật:** 19/11/2026

SD Micro Tool quản lý thư mục thẻ SD mà người dùng chủ động cấp quyền và tải tệp do người dùng cung cấp liên kết.

## Dữ liệu ứng dụng xử lý
- Quyền truy cập thư mục SD do người dùng chọn thông qua Android Storage Access Framework.
- URL tải xuống, tên tệp và tiến trình tải trong lúc thao tác.
- Thông tin phiên bản ứng dụng để kiểm tra `version.json`.
- Ứng dụng không cần danh bạ, vị trí, SMS, danh bạ cuộc gọi hoặc microphone.

## Tải xuống
Tệp được ghi theo luồng vào file tạm trong thư mục SD đã chọn, sau đó đổi tên khi hoàn thành. APK cập nhật được tải vào vùng cache riêng của ứng dụng để Android Package Installer đọc; đây là file cài đặt tạm, không phải tài nguyên người dùng.

## Cập nhật ứng dụng
Ứng dụng yêu cầu máy chủ GitHub công khai cung cấp `version.json` và APK release. Trước khi cài, ứng dụng xác minh package name và chữ ký APK khớp với ứng dụng đã cài. Android luôn hiển thị bước xác nhận cài đặt.

## Đồng bộ tạm thời
Tính năng Temporary Cloud Sync chưa được bật trong bản này. Nếu được triển khai sau này, chỉ dữ liệu đồng bộ được mã hóa và có thời hạn mới được chuyển qua máy chủ trung gian; không tải APK, game hoặc tài nguyên game lên GitHub.

## Chia sẻ và lưu giữ
Ứng dụng không bán dữ liệu người dùng. Các tệp do người dùng tải được giữ trên thẻ SD cho tới khi người dùng xóa hoặc thiết bị/ứng dụng khác quản lý chúng. Cache cập nhật nằm trong vùng cache riêng và có thể bị Android dọn dẹp.

## Liên hệ
Nhà phát triển có thể bổ sung địa chỉ hỗ trợ chính thức trước khi phát hành Google Play.
