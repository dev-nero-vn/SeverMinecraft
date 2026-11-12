# Minecraft Platform — Bản thử nghiệm

Giao diện quản lý máy chủ Minecraft bằng tiếng Việt, tối ưu cho điện thoại và máy tính.

## Trạng thái hiện tại

Đây là **bản thử nghiệm giao diện tĩnh**:
- Thanh điều hướng bên trái với nhiều nhóm chức năng.
- Giao diện thích ứng cho điện thoại, máy tính bảng và máy tính.
- Có thể lưu bản nháp cấu hình máy chủ trong bộ nhớ trình duyệt.
- Biểu mẫu máy chủ riêng có trường đặt số người chơi tối đa mong muốn; con số này chỉ là cấu hình nháp, chưa phải giới hạn đang áp dụng trên máy chủ thật.
- Có thể chọn tệp Modpack trên thiết bị để xem tên và dung lượng.

Các chức năng sau **chưa hoạt động với máy chủ Minecraft thật** vì backend và bộ kết nối Vivo chưa được cài đặt:
- Khởi động/tắt máy chủ Minecraft.
- Cấm/gỡ cấm người chơi và cấp quyền trong game.
- Cài Modpack, Mod, Plugin vào máy chủ.
- Đọc CPU, RAM, nhiệt độ và người chơi trực tuyến.
- Danh bạ server có dữ liệu trực tiếp.

## Xuất bản thử nghiệm

Kho mã nguồn dùng GitHub Pages. Trong GitHub, mở **Settings → Pages** và chọn **Source: GitHub Actions**. Sau đó vào **Actions**, mở quy trình “Xuất bản website thử nghiệm” và kiểm tra lần chạy.

Địa chỉ dự kiến sau khi bật Pages:
https://dev-nero-vn.github.io/SeverMinecraft/

## Chạy thử trên điện thoại

Mở địa chỉ website bằng trình duyệt Android. Có thể chọn **Thêm vào màn hình chính** để tạo lối tắt dạng ứng dụng. Đây là giao diện web; nó chưa chạy Minecraft Server trên điện thoại.

## Kiến trúc mục tiêu

- GitHub: lưu mã nguồn và lịch sử thay đổi.
- GitHub Pages: xuất bản giao diện tĩnh.
- Vivo 1718: máy chủ vật lý thử nghiệm, Android 8.1, RAM 4 GB, bộ nhớ 32 GB.
- Backend trên Vivo: sẽ được thiết kế và cài ở giai đoạn tiếp theo, sau khi kiểm tra thiết bị và mạng.
- Giới hạn mục tiêu giai đoạn đầu: tối đa 20 cấu hình máy chủ; số máy chủ chạy đồng thời phải được kiểm tra theo tài nguyên thực tế.

**Chưa có mật khẩu máy chủ hoặc dữ liệu bí mật nào được nhúng trong website.**

## Các yêu cầu chức năng bổ sung đã thống nhất

- Máy chủ riêng cho phép chủ máy chủ nhập số người chơi tối đa mong muốn.
- Máy chủ cộng đồng và máy chủ riêng có mã mời và mã QR để chia sẻ địa chỉ tham gia.
- Danh sách cho phép, cấm/gỡ cấm, vai trò quản trị và nhật ký xử lý người chơi.
- Sao lưu thế giới theo lịch và sao lưu thủ công; có thể khôi phục về bản trước.
- Tự khởi động lại sau sự cố, có lịch bảo trì và lịch khởi động/tắt máy chủ.
- Cảnh báo khi máy chủ ngoại tuyến, bộ nhớ gần đầy, RAM/CPU hoặc nhiệt độ vượt ngưỡng.
- Xuất/nhập thế giới và cấu hình để chuyển sang thiết bị chủ khác.
- Mẫu máy chủ dựng sẵn cho sinh tồn, sáng tạo và các chế độ phổ biến.
- Trang trạng thái công khai cho từng máy chủ, hiển thị phiên bản, chế độ và số người chơi thực tế.
- Bảo vệ chống phá hoại, phân quyền xây dựng và lưu lịch sử thao tác.

Các mục trên là yêu cầu sản phẩm/định hướng triển khai; chúng chưa được kết nối với máy chủ Minecraft thật trong bản giao diện tĩnh.


## Tài liệu kế hoạch đầy đủ

Xem [Lộ trình chức năng bằng tiếng Việt](./ROADMAP_VI.md) để đọc toàn bộ phạm vi tính năng và tiêu chí nghiệm thu.
