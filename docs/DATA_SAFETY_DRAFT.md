# Bản khai nháp Google Play Data safety — SD Micro Tool

Tài liệu này là bản rà soát kỹ thuật nội bộ, **không phải biểu mẫu đã nộp**. Cần xác nhận lại sau khi bật bất kỳ dịch vụ đồng bộ hoặc analytics nào.

- Không có quảng cáo hoặc SDK phân tích trong mã nguồn hiện tại.
- Không yêu cầu vị trí, danh bạ, SMS, lịch sử cuộc gọi hoặc microphone.
- INTERNET: chỉ dùng để tải URL HTTPS do người dùng nhập và kiểm tra manifest cập nhật.
- REQUEST_INSTALL_PACKAGES: chỉ dùng để yêu cầu Android mở trình cài đặt cho bản cập nhật của chính ứng dụng; người dùng xác nhận.
- SAF: người dùng chủ động chọn thư mục SD; app chỉ đọc/ghi trong phạm vi được cấp.
- APK cập nhật nằm tạm trong cache riêng trước khi mở Package Installer.

Temporary Cloud Sync chưa được bật. Khi triển khai, phải khai báo chính xác loại dữ liệu đồng bộ, mã hóa khi truyền/lưu, thời hạn, quyền xóa và nhà cung cấp xử lý dữ liệu trong Data safety form.
