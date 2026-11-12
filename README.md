# Minecraft Platform — Bản thử nghiệm

Giao diện quản lý máy chủ Minecraft bằng tiếng Việt, tối ưu cho điện thoại và máy tính.

## Trạng thái hiện tại

Đây là **bản thử nghiệm giao diện tĩnh**:
- Thanh điều hướng bên trái với nhiều nhóm chức năng.
- Giao diện thích ứng cho điện thoại, máy tính bảng và máy tính.
- Có thể lưu bản nháp cấu hình máy chủ trong bộ nhớ trình duyệt.\n- Biểu mẫu máy chủ riêng có trường đặt số người chơi tối đa mong muốn; con số này chỉ là cấu hình nháp, chưa phải giới hạn đang áp dụng trên máy chủ thật.
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