# Smart Game Storage — SD Micro Tool

## Mục tiêu và nguyên tắc an toàn
Smart Game Storage theo dõi thư mục người dùng đã cấp quyền, phát hiện tệp mới/thay đổi và quản lý việc sao chép sang SD. Ứng dụng không root, không tạo symlink/đường dẫn giả, không sửa dữ liệu riêng của game, không tự xóa bản gốc.

**Quan trọng:** sao chép một tệp sang SD không khiến game tự động đọc tệp ở đường dẫn mới. Bản gốc được giữ lại cho tới khi người dùng tự xác nhận xóa; kể cả khi SHA-256 khớp, tính toàn vẹn bản sao không chứng minh game có thể sử dụng đường dẫn SD mới.

## Tính năng trong mã nguồn hiện tại
- Danh sách package nhận diện: Roblox, Free Fire, Free Fire MAX và Liên Quân Mobile.
- Thống kê app/data/cache bằng StorageStatsManager sau khi người dùng cấp Usage Access trong Cài đặt Android. Đây là thống kê tổng theo package, không mở khóa thư mục private.
- Chọn thư mục nguồn qua Storage Access Framework (SAF), lưu quyền lâu dài và lưu cấu hình theo package.
- Lập ảnh chụp metadata cho tệp được phép: URI, tên, dung lượng, thời gian sửa đổi, MIME type và thời điểm nhìn thấy lần đầu/cuối.
- Quét thủ công ngay trong ứng dụng và quét nền định kỳ bằng WorkManager.
- WorkManager có chu kỳ yêu cầu tối thiểu 15 phút; Android có thể trì hoãn tác vụ vì Doze, tiết kiệm pin hoặc hạn chế nền. Đây không phải theo dõi tức thời.
- Quét có giới hạn độ sâu 6 cấp và tối đa 1.500 tệp mỗi lượt để hạn chế tải trên thiết bị cấu hình thấp.
- Phát hiện tệp mới hoặc thay đổi dựa trên URI, kích thước và thời gian sửa đổi.
- Chế độ tự động theo thư mục: tự đưa tệp mới/đã thay đổi, không ẩn/không tạm, kích thước trên 0 và tối đa 512 MiB vào hàng đợi sao chép SD. Chỉ chạy nếu thư mục nguồn và đích còn quyền, SD còn dư tối thiểu kích thước tệp cộng 16 MiB.
- Chế độ tự động chỉ **sao chép** và giữ bản gốc. Nó không tự chuyển đường dẫn của game, không tự giải phóng bộ nhớ trong, và không khẳng định game hỗ trợ tài nguyên ở vị trí mới.
- Hàng đợi được lưu trong SQLite. Các công việc chạy tuần tự qua WorkManager foreground worker.
- Tiến trình lưu vào DB và notification: dung lượng, phần trăm, tốc độ và ETA; có tạm dừng, tiếp tục, hủy và thử lại tác vụ thất bại.
- Tạo file tạm .part trên SD, ghi theo luồng với bộ đệm RAM 64 KiB, tính SHA-256 trong lúc sao chép, đọc lại bản trên SD và so sánh hash.
- Chỉ ghi lịch sử trạng thái đã xác minh sau khi hash khớp. Nếu hủy/lỗi trước khi hoàn tất, file tạm được dọn và bản gốc giữ nguyên.
- Lịch sử lưu package, tên, dung lượng, SHA-256, URI nguồn/đích và trạng thái.
- Khôi phục: người dùng chọn thư mục đích; app sao chép bản trên SD bằng tên không trùng, không ghi đè file cũ và xác minh SHA-256.
- Báo cáo dung lượng giải phóng chỉ tăng sau khi người dùng chủ động xác nhận xóa bản nguồn và thao tác xóa thành công.

## Ma trận tương thích
| Hệ thống / game | Có thể làm | Không được khẳng định |
|---|---|---|
| Roblox | Nhận diện package; đọc tổng thống kê khi có Usage Access; theo dõi thư mục chia sẻ do người dùng chọn; sao chép tệp trong phạm vi cấp quyền | Di chuyển cache/private assets của Roblox hoặc làm Roblox tự đọc dữ liệu từ SD |
| Free Fire / Free Fire MAX | Nhận diện package; đọc tổng thống kê; theo dõi thư mục chia sẻ do người dùng chọn; sao chép tệp được phép | Di chuyển thư mục dữ liệu riêng, OBB hoặc cache private; đảm bảo game chạy với asset đã chép |
| Liên Quân Mobile | Cùng phạm vi package stats và thư mục người dùng cấp | Di chuyển dữ liệu private hoặc thay đường dẫn game |
| Thư mục chia sẻ do người dùng chọn | Quét, theo dõi định kỳ, sao chép, xác minh, khôi phục trong phạm vi quyền SAF | Theo dõi tức thời nếu DocumentProvider không phát thông báo; đọc ngoài thư mục được cấp |

## Giới hạn Android 11+
Android 11+ không cho ACTION_OPEN_DOCUMENT_TREE/ACTION_OPEN_DOCUMENT truy cập Android/data/ và Android/obb/ của ứng dụng khác. Không có API công khai thông thường cho ứng dụng bên thứ ba đọc/di chuyển cache private của Roblox hoặc Free Fire. Không có cách hợp pháp trong app không root để ép các game này dùng đường dẫn tùy ý trên SD nếu game không hỗ trợ.

StorageStatsManager chỉ cho số liệu dung lượng ứng dụng/package sau khi người dùng tự cấp Usage Access; quyền này không đồng nghĩa quyền đọc file. API có thể mất vài giây và cần chạy trên worker thread.

## Hành vi tự động
- Lần đầu chọn thư mục: app lưu quyền và quét metadata làm mốc ban đầu.
- Chỉ sau khi người dùng bật tự động cho thư mục đó, các thay đổi được phát hiện về sau mới có thể vào hàng đợi sao chép.
- Khi đổi thư mục nguồn, chế độ tự động reset về tắt để tránh tự động sao chép toàn bộ nội dung thư mục mới ngoài ý muốn.
- Khi SD đầy, không có quyền, bị rút hoặc không xác định được dung lượng trống: tệp không được đưa vào hàng đợi tự động.
- Khi cập nhật tệp, bản sao cũ không bị tự xóa; người dùng cần kiểm tra lịch sử và quản lý bản sao.
- Tác vụ nền phụ thuộc chính sách pin/Doze của Android và nhà sản xuất.

## Kiểm thử trước phát hành
Các kiểm tra sau cần chạy trên điện thoại thật (ưu tiên Samsung Galaxy A07) trước khi phát hành:
1. Chọn thư mục bình thường trên SD, quét lần đầu và xác nhận auto-copy mặc định tắt.
2. Bật auto-copy, tạo tệp mới trong thư mục được giám sát, chờ lượt quét nền hoặc bấm quét ngay; xác nhận tệp vào queue và bản gốc vẫn còn.
3. Thay đổi kích thước/timestamp, xác nhận phát hiện thay đổi một lần, không lặp queue liên tục.
4. Kiểm tra queue nhiều tệp, pause/resume/cancel, tệp tên trùng, tệp lớn và file provider lỗi.
5. Thẻ SD thiếu dung lượng hoặc bị rút; xác nhận task lỗi an toàn, không xóa nguồn.
6. So SHA-256 tệp nguồn và tệp SD; kiểm tra lịch sử và restore vào thư mục internal shared storage.
7. Xác nhận báo cáo freed bytes chỉ tăng khi xóa bản nguồn thật sự thành công.
8. Cấp/thu hồi Usage Access; xác nhận chỉ thay đổi phần thống kê, không thay đổi quyền file.
9. Mở Roblox và Free Fire trước/sau các thao tác thống kê; tuyệt đối không thử xóa/chuyển private cache.
10. Cài đặt/cập nhật APK trên Android 11+ và thử quy trình quyền SAF sau nâng cấp.

## Trạng thái thử nghiệm
- GitHub Actions compile/package: được theo dõi theo từng run; xem link run trong phần bàn giao.
- Kiểm thử thiết bị thật Galaxy A07: chưa thực hiện trong môi trường build hiện tại.
- Kiểm thử Roblox/Free Fire thực tế: chưa thực hiện; không tuyên bố tương thích di chuyển private assets.
- Release signing: chỉ được xem là đã cấu hình khi keystore do chủ ứng dụng quản lý được cung cấp qua Actions secrets và workflow xác minh chữ ký thành công.
