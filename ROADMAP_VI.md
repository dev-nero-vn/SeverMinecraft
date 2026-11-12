# Lộ trình chức năng — Minecraft Platform

Tài liệu này ghi lại phạm vi sản phẩm đã thống nhất và các phần đề xuất bổ sung. Giao diện hiện tại là bản thử nghiệm tĩnh; nội dung dưới đây là yêu cầu phát triển, không phải danh sách tính năng đã vận hành.

## 1. Mục tiêu sản phẩm

Xây dựng nền tảng Minecraft bằng tiếng Việt, dùng trên điện thoại và máy tính, gồm:
- Danh bạ máy chủ Minecraft tại Việt Nam.
- Dịch vụ cho người dùng tự tạo và quản lý máy chủ kiểu Aternos.
- Hai loại máy chủ: cộng đồng và riêng.
- Hỗ trợ Java/Bedrock và chơi chung PC–điện thoại trong phạm vi tương thích thực tế.
- Bảng điều khiển quản trị từ xa.
- Hệ thống Modpack, Mod, Plugin, thế giới và sao lưu.

## 2. Danh bạ máy chủ Việt Nam

- Tìm kiếm theo tên, địa chỉ, quốc gia/tỉnh thành, phiên bản, nền tảng và chế độ chơi.
- Lọc Java, Bedrock, Survival, SMP, Lifesteal, SkyBlock, BedWars, Prison, Creative, Minigames và Modded.
- Hồ sơ riêng cho từng máy chủ: mô tả, phiên bản, phần mềm, địa chỉ, cổng, biểu tượng, trạng thái và số người chơi.
- Chủ máy chủ có thể gửi, xác minh, sửa hoặc yêu cầu gỡ hồ sơ.
- Người chơi có thể yêu thích, đánh giá, báo cáo và chia sẻ máy chủ.
- Thu thập dữ liệu từ nguồn được phép, API công khai và dữ liệu cộng đồng; loại bỏ bản ghi trùng và ghi rõ thời điểm kiểm tra.
- Không tuyên bố có mọi máy chủ trên Việt Nam nếu chưa có dữ liệu xác minh.

## 3. Hai loại máy chủ

### 3.1. Máy chủ cộng đồng
- Người tạo đặt tên và tạo một thế giới chung.
- Thế giới lưu lâu dài, không phải bản đồ mô phỏng.
- Người chơi tham gia để sinh tồn, xây dựng và khám phá tự do.
- Có thể công khai trên danh bạ hoặc chỉ chia sẻ bằng lời mời.
- Có quy tắc, danh sách cho phép, phân quyền xây dựng và công cụ chống phá hoại.

### 3.2. Máy chủ riêng
- Chủ máy chủ chọn phiên bản Minecraft, Java hoặc Bedrock và phần mềm máy chủ.
- Chọn Vanilla, Paper, Purpur, Spigot, Fabric, Forge, NeoForge hoặc Quilt khi tương thích.
- Đặt tên, mô tả, biểu tượng, chế độ chơi, quyền truy cập và cấu hình.
- **Đặt số người chơi tối đa** trong biểu mẫu tạo máy chủ.
- Có thể chỉnh sửa giới hạn người chơi sau khi tạo.
- Khi đạt giới hạn, từ chối người chơi mới hoặc đưa vào hàng đợi nếu chủ máy chủ bật tính năng hàng đợi.
- Con số chủ máy chủ nhập là giới hạn mong muốn; hệ thống phải kiểm tra tài nguyên thực tế và không cho đặt mức vượt khả năng máy chủ.

## 4. Chơi chung PC và điện thoại

- Java PC và Bedrock trên Android/iPhone/Windows có thể cùng chơi qua cầu nối như Geyser/Floodgate khi cấu hình phù hợp.
- Dùng lớp tương thích phiên bản phù hợp, ví dụ ViaVersion và các thành phần liên quan.
- Có bảng ma trận ghi rõ phiên bản Java/Bedrock nào đã kiểm thử.
- Không hứa mọi phiên bản lịch sử, mọi Modpack hay mọi Mod Java đều tương thích Bedrock.
- Hiển thị hướng dẫn kết nối riêng cho Java và Bedrock, gồm địa chỉ và cổng chính xác sau khi máy chủ thật hoạt động.

## 5. Quản lý máy chủ

- Tạo, khởi động, dừng, khởi động lại, xóa và đổi tên.
- Console trực tiếp, nhật ký khởi động và nhật ký lỗi.
- Quản lý tệp, cấu hình máy chủ và thuộc tính thế giới.
- Hiển thị địa chỉ, cổng, phiên bản, biểu tượng và thông điệp máy chủ.
- Trạng thái rõ ràng: đang tạo, đang khởi động, trực tuyến, ngoại tuyến, lỗi, đang bảo trì.
- Mã mời và mã QR để chia sẻ kết nối.
- Lịch khởi động, tắt và bảo trì.
- Tự khởi động lại sau sự cố với giới hạn thử lại; không lặp vô hạn khi lỗi cấu hình.
- Tùy chọn ngủ khi không có người chơi để tiết kiệm tài nguyên.
- Hàng đợi khởi chạy khi máy chủ vật lý đang quá tải.

## 6. Modpack, Mod, Plugin và nội dung

- Nhập Modpack bằng tệp và đường dẫn.
- Dự kiến hỗ trợ Modrinth `.mrpack`, gói xuất CurseForge, Server Pack ZIP, tệp JAR và nhiều JAR.
- Quản lý Forge, Fabric, NeoForge, Quilt và phần mềm Plugin như Paper/Purpur/Spigot.
- Đọc manifest, phiên bản, Mod Loader, phần phụ thuộc và phân loại tệp Client/Server.
- Kiểm tra xung đột và tương thích trước khi cài.
- Chỉ tải tệp từ nguồn hợp lệ, tôn trọng quyền phân phối và điều khoản của từng nền tảng.
- Sao lưu trước khi thay đổi; ghi lại lịch sử cài đặt và cho phép hoàn tác khi có thể.
- Phân biệt rõ Mod cần cài ở máy người chơi với Mod chỉ chạy phía máy chủ.
- Không tuyên bố Mod Java Client có thể tự động chạy trên Bedrock.

## 7. Người chơi, kiểm duyệt và phân quyền

- Xem người chơi trực tuyến và lịch sử tham gia.
- Ban, ban tạm thời, gỡ ban, kick, whitelist và ghi lý do.
- Quản lý theo tên và định danh ổn định khi có dữ liệu xác thực.
- Vai trò: Chủ sở hữu, Quản trị viên, Điều hành viên, Hỗ trợ, Người chơi.
- Quyền chi tiết theo từng thao tác; không mặc định cấp OP cho mọi quản trị viên website.
- Nhật ký ai đã ban, kick, đổi quyền hoặc chỉnh cấu hình.
- Đồng bộ các thao tác qua plugin/API/RCON phù hợp, bảo vệ kênh quản trị và không mở RCON công khai.

## 8. Bảo vệ thế giới và sao lưu

- Sao lưu thủ công và theo lịch.
- Giữ nhiều phiên bản sao lưu theo chính sách dung lượng.
- Khôi phục toàn bộ thế giới hoặc cấu hình đã chọn.
- Xuất và nhập thế giới để chuyển sang máy chủ khác.
- Kiểm tra tính toàn vẹn bản sao lưu.
- Nhắc trước khi xóa hoặc ghi đè thế giới.
- Lưu bản sao dự phòng ở vị trí khác khi hạ tầng cho phép.

## 9. Theo dõi và cảnh báo

- Theo dõi CPU, RAM, dung lượng lưu trữ, nhiệt độ thiết bị, mạng và tiến trình.
- Theo dõi số người chơi và giới hạn người chơi mỗi máy chủ.
- Cảnh báo khi ngoại tuyến, lỗi khởi động, gần hết dung lượng hoặc vượt ngưỡng tài nguyên.
- Lịch sử tài nguyên để đánh giá máy chịu tải thực tế.
- Tự giới hạn số máy chủ chạy đồng thời theo ngân sách RAM/CPU; không lấy giới hạn số cấu hình làm số máy chạy cùng lúc.

## 10. Tài khoản, bảo mật và quản trị nền tảng

- Tài khoản nền tảng riêng; đăng nhập Microsoft chỉ dùng khi cần xác minh quyền sở hữu trò chơi.
- Mật khẩu được băm an toàn; hỗ trợ xác thực bổ sung cho quản trị viên.
- Phiên đăng nhập, thu hồi thiết bị, giới hạn thử đăng nhập và nhật ký bảo mật.
- Tách quyền quản trị nền tảng khỏi quyền quản trị từng máy chủ Minecraft.
- Mã kết nối giữa website và máy chủ Vivo phải được lưu an toàn, có thể thu hồi và không nhúng vào mã nguồn công khai.
- Có trang trạng thái dịch vụ, lịch bảo trì và thông báo sự cố.

## 11. Hạ tầng giai đoạn đầu

- Điện thoại Vivo 1718: Android 8.1, RAM 4 GB, bộ nhớ trong 32 GB, làm thiết bị thử nghiệm chuyên dụng.
- Chạy trực tiếp trên Android, không cài máy ảo đầy đủ.
- Mục tiêu tối đa 20 cấu hình máy chủ được tạo trên thiết bị giai đoạn đầu.
- Số máy chủ chạy đồng thời phải được giới hạn riêng sau khi đo CPU, RAM, nhiệt độ và độ ổn định.
- GitHub lưu mã nguồn và lịch sử thay đổi.
- GitHub Pages chỉ phục vụ giao diện tĩnh; backend và tiến trình Minecraft phải chạy trên Vivo hoặc máy chủ thực tế khác.
- Khi thay máy chủ vật lý, phải có khả năng xuất/nhập thế giới, cấu hình và bản sao lưu.

## 12. Tiêu chí nghiệm thu bắt buộc

1. Tạo máy chủ riêng và lưu đúng số người chơi tối đa đã nhập.
2. Giới hạn người chơi phải được áp dụng trên máy chủ thật, không chỉ hiện trên giao diện.
3. Tạo máy chủ cộng đồng và lưu thế giới qua lần khởi động lại.
4. Kết nối thử bằng Java PC và Bedrock điện thoại trên phiên bản đã công bố hỗ trợ.
5. Hiển thị đúng địa chỉ, cổng, trạng thái và số người chơi.
6. Ban/kick/gỡ ban từ trong game và từ bảng điều khiển, kiểm tra kết quả thực tế.
7. Nhập một Modpack thử nghiệm, xác minh Mod Loader và phụ thuộc, sao lưu trước khi cài.
8. Sao lưu và khôi phục thế giới thử nghiệm thành công.
9. Kiểm tra mất mạng, dừng tiến trình, đầy dung lượng và khởi động lại.
10. Không hiển thị số liệu giả hoặc báo Online khi máy chủ chưa thực sự chạy.

## 13. Trạng thái triển khai

- Đã có: giao diện tĩnh tiếng Việt, thanh điều hướng, biểu mẫu lưu bản nháp, giới hạn 20 bản nháp trên trình duyệt, trường số người chơi tối đa cho máy chủ riêng và logo giao diện.
- Chưa có: backend trên Vivo, tài khoản thật, cơ sở dữ liệu dùng chung, khởi chạy Minecraft, cấp IP/cổng, kết nối Java–Bedrock, Modpack thật, ban/kick thật và giám sát thiết bị.
- Mọi trạng thái phải được ghi đúng; bản nháp không được gọi là máy chủ đang chạy.
