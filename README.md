# SD Storage Manager

Ứng dụng Android quản lý thư mục SD và tải tệp trực tiếp vào thẻ nhớ.

## Tính năng
- Chọn thư mục trên thẻ SD bằng Storage Access Framework.
- Ghi luồng tải HTTPS trực tiếp vào file tạm .part nằm trên SD; khi tải xong đổi tên file ngay trên SD.
- Không tải file hoàn chỉnh vào bộ nhớ trong rồi sao chép sang SD.
- Duyệt và xóa tệp trong cây thư mục mà người dùng đã cấp quyền.
- Hiển thị riêng dung lượng bộ nhớ trong và thẻ SD.
- Giao diện tiếng Việt, launcher icon riêng, tải nền bằng foreground service.

## Giới hạn Android
- APK cài đặt và dữ liệu riêng của ứng dụng vẫn sử dụng một phần bộ nhớ trong.
- SAF chỉ cấp quyền cho thư mục do người dùng chọn; app không tự ý truy cập thư mục riêng của Roblox hoặc game khác.
- Không có tính năng giả mạo “chuyển cache game”, không đoán số GB tiết kiệm.
- Thẻ SD phải được nhận diện là removable storage. Android/nhà sản xuất có thể giới hạn một số thư mục.
- Dữ liệu mạng được truyền theo luồng với bộ đệm RAM; Android vẫn có thể dùng một lượng nhỏ cache hệ thống.

## Build
Mở repository bằng Android Studio hoặc chạy: gradle :app:assembleDebug

GitHub Actions tự build APK debug và đóng gói ZIP sau mỗi lần push lên main.

## Google Play
Bản Actions hiện tạo APK debug để cài thử. Trước khi xuất bản Google Play cần build AAB release có ký bằng khóa phát hành do chủ sở hữu quản lý, hoàn tất biểu mẫu Data safety, chính sách quyền riêng tư và kiểm thử trên thiết bị thật.

## Smart Game Storage
Xem [docs/SMART_GAME_STORAGE.md](docs/SMART_GAME_STORAGE.md). Tính năng thống kê game cần Usage Access; chuyển tệp chỉ hoạt động trong thư mục do người dùng cấp quyền. Không thể di chuyển dữ liệu riêng của game trên Android 11+.
