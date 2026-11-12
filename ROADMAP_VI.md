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


## 14. Bảo vệ thế giới nâng cao

### 14.1. Sao lưu theo lịch và nhiều phiên bản
- Cho phép đặt lịch sao lưu theo giờ, ngày hoặc tuần; mỗi máy chủ có lịch riêng.
- Có nút sao lưu thủ công trước khi cài Modpack, Mod, Plugin, đổi phiên bản hoặc cập nhật phần mềm.
- Lưu nhiều điểm khôi phục, hiển thị thời gian, dung lượng, loại sao lưu và trạng thái kiểm tra.
- Cho phép đặt số bản cần giữ hoặc giới hạn dung lượng; khi dọn bản cũ phải giữ lại bản mới nhất và các bản được ghim.
- Có thể sao lưu thế giới, cấu hình, danh sách người chơi, whitelist, quyền, Plugin/Mod và tệp máy chủ theo lựa chọn.
- Hỗ trợ bản sao ở vị trí lưu trữ thứ hai khi hạ tầng cho phép; không coi bản sao trên cùng bộ nhớ là bản dự phòng độc lập.
- Kiểm tra checksum và khả năng đọc của bản sao lưu; cảnh báo nếu bản sao bị hỏng hoặc thiếu tệp.
- Trước khi khôi phục, tự tạo một bản sao lưu hiện trạng; hiển thị rõ máy chủ nào, thế giới nào và thời điểm nào sẽ bị thay thế.
- Có chế độ khôi phục thử vào thư mục riêng để kiểm tra trước khi ghi đè thế giới đang dùng.

### 14.2. Chống mất dữ liệu khi tắt đột ngột
- Ưu tiên lệnh lưu thế giới và dừng máy chủ an toàn trước khi tắt tiến trình.
- Theo dõi trạng thái lưu, ghi nhật ký thao tác và phát hiện lần tắt không bình thường.
- Có cơ chế khởi động lại sau sự cố; kiểm tra thế giới và tệp dữ liệu trước khi cho người chơi vào lại.
- Với tiến trình bị dừng đột ngột hoặc mất điện, thực hiện quy trình kiểm tra/khôi phục an toàn; không hứa bảo toàn tuyệt đối dữ liệu chưa kịp ghi.
- Có cảnh báo dung lượng thấp để tránh thế giới hoặc nhật ký không thể ghi tiếp.

## 15. Tự động vận hành và hàng đợi

- Lên lịch bật, tắt, khởi động lại và bảo trì theo múi giờ Việt Nam (Asia/Ho_Chi_Minh).
- Cho phép lịch lặp theo ngày trong tuần, ngày cụ thể hoặc chỉ chạy một lần.
- Tự khởi động lại sau lỗi với số lần thử hữu hạn, khoảng chờ tăng dần và trạng thái lỗi rõ ràng; tạm ngừng tự khởi động khi lỗi lặp lại.
- Thông báo khi máy chủ chuyển Online/Offline, khởi động thất bại, hết tài nguyên, bản sao lưu lỗi hoặc bắt đầu bảo trì.
- Kênh thông báo dự kiến: thông báo trong nền tảng; email hoặc tích hợp khác chỉ bật khi người dùng tự kết nối.
- Hàng đợi khởi chạy khi thiết bị hết tài nguyên: ghi vị trí, thời điểm gửi, trạng thái chờ và lý do; tự đánh giá lại tài nguyên theo chu kỳ.
- Người dùng có thể hủy yêu cầu đang chờ; không tự xóa hoặc khởi chạy lặp một yêu cầu.
- Ưu tiên có thể cấu hình theo chủ máy chủ/gói tài nguyên, nhưng phải công khai quy tắc để tránh bất công.
- Có nút dừng lịch và chế độ bảo trì để ngăn các tác vụ tự động xung đột.

## 16. Mời bạn bè và kiểm soát quyền tham gia

- Tạo mã mời riêng theo từng máy chủ; chủ máy chủ chọn thời hạn, số lượt dùng và quyền mặc định.
- Mã mời có thể thu hồi ngay; không dùng mã mời làm mật khẩu quản trị.
- Tạo QR chỉ chứa thông tin kết nối cần thiết hoặc liên kết mời có thời hạn; không đưa token quản trị hay thông tin bí mật vào QR.
- Có danh sách bạn bè, yêu cầu kết bạn, chấp nhận/từ chối và chặn người dùng.
- Quản lý danh sách được phép tham gia (whitelist), danh sách bị chặn và lời mời đang chờ.
- Chủ máy chủ có thể cấp quyền xem, tham gia, xây dựng hoặc quản trị riêng biệt.
- Ghi nhật ký người tạo, người sử dụng, thời gian hết hạn và lần thu hồi mã mời.
- Có giới hạn tốc độ tạo mã và thử mã; ẩn dữ liệu cá nhân không cần thiết.

## 17. Kho nội dung Minecraft và kiểm tra tương thích

- Thư viện Modpack, Mod, Plugin, bản đồ/thế giới và mẫu máy chủ dựng sẵn.
- Tìm kiếm theo phiên bản Minecraft, Java/Bedrock, phần mềm máy chủ, Mod Loader, thể loại và mức tài nguyên dự kiến.
- Mỗi mục có nguồn tải, tác giả/nhà phát hành, giấy phép, phiên bản hỗ trợ, ngày cập nhật và dấu kiểm tra tệp.
- Trước khi cài, phân tích manifest, dependency, phiên bản Java, Mod Loader, thư viện cần thiết và phân loại client-only/server-side.
- Cảnh báo Mod/Plugin trùng chức năng, xung đột phiên bản, tệp không rõ nguồn hoặc gói chỉ dành cho máy người chơi.
- Cho phép xem trước danh sách thay đổi; sao lưu trước khi áp dụng; lưu lịch sử cài đặt và hỗ trợ hoàn tác.
- Hỗ trợ nhập từ tệp trên điện thoại/máy tính và URL nguồn hợp lệ; xử lý quyền truy cập, giới hạn tải xuống và điều khoản nền tảng.
- Mẫu dựng sẵn gồm Sinh tồn, SMP, Sáng tạo, SkyBlock và các chế độ phổ biến; mẫu chỉ là cấu hình khởi tạo, không tự bảo đảm máy có đủ tài nguyên.
- Kiểm tra riêng khả năng chơi chung Java–Bedrock; không coi Mod Java bất kỳ là tương thích với Bedrock.

## 18. Giới hạn và tối ưu tài nguyên

- Chủ máy chủ có thể đặt ngân sách RAM, CPU (mức ưu tiên/giới hạn khi hệ điều hành hỗ trợ), dung lượng lưu trữ và số người chơi.
- Hệ thống đặt trần an toàn cho từng máy chủ và tổng thiết bị; không cho nhập mức vượt ngân sách khả dụng.
- Theo dõi sử dụng RAM/CPU/dung lượng, mạng, nhiệt độ thiết bị (nếu cảm biến cho phép), số người chơi và tiến trình theo thời gian thực.
- Hiển thị biểu đồ lịch sử theo khoảng thời gian; phân biệt dữ liệu trực tiếp, dữ liệu cũ và dữ liệu chưa có.
- Từ chối tạo hoặc khởi chạy server mới khi chạm ngưỡng an toàn; đưa yêu cầu vào hàng đợi khi có thể.
- Cảnh báo trước khi dung lượng đầy; đề xuất dọn log/bản sao cũ theo chính sách, không tự xóa thế giới.
- Có hạn mức theo từng tài khoản và từng máy chủ khi đã có backend; việc giới hạn phải xác thực ở backend, không chỉ bằng JavaScript.
- Tách giới hạn số cấu hình khỏi số tiến trình chạy đồng thời. Mục tiêu 20 cấu hình không có nghĩa Vivo 4 GB RAM có thể chạy 20 server cùng lúc.
- Đo tải thực tế trên Vivo 1718 trước khi chốt số người chơi hoặc số server đồng thời; dừng thử nghiệm khi nhiệt độ hoặc độ ổn định vượt ngưỡng an toàn.

## 19. Cộng đồng, đánh giá và xếp hạng Việt Nam

- Hồ sơ server có đánh giá bằng sao, nhận xét và bình chọn; ghi thời điểm cập nhật và trạng thái xác minh.
- Người dùng có thể theo dõi/bỏ theo dõi server yêu thích và nhận cập nhật trạng thái nếu bật thông báo.
- Bảng xếp hạng Việt Nam có bộ lọc theo Java/Bedrock, phiên bản, chế độ, tỉnh/thành và thời gian.
- Công khai tiêu chí xếp hạng; tách lượt bình chọn, số người chơi kiểm tra được và đánh giá để không đánh đồng các số liệu.
- Chỉ tài khoản hợp lệ mới được bình chọn; giới hạn tần suất và phát hiện hành vi spam, tài khoản ảo hoặc thao túng điểm.
- Chủ server có thể phản hồi đánh giá nhưng không được tự xóa nhận xét của người chơi.
- Nút báo cáo server vi phạm: gian lận danh bạ, giả mạo, nội dung không phù hợp, lừa đảo hoặc hành vi gây hại.
- Hệ thống tiếp nhận, phân loại, lưu bằng chứng tối thiểu, trạng thái xử lý và lịch sử quyết định; có cơ chế kháng nghị.
- Công khai nhãn đã xác minh, đang xem xét hoặc bị hạn chế; không tự kết luận vi phạm chỉ dựa trên một báo cáo chưa kiểm chứng.

## 20. Các yêu cầu kỹ thuật và nghiệm thu bổ sung

1. Lịch sao lưu chạy đúng giờ Việt Nam; lưu được nhiều bản và tự kiểm tra tính toàn vẹn.
2. Khôi phục thử vào vị trí riêng thành công; khôi phục thật luôn tạo bản dự phòng trước khi ghi đè.
3. Mô phỏng tiến trình dừng đột ngột và xác minh quy trình kiểm tra dữ liệu sau khi khởi động.
4. Tự khởi động lại có giới hạn thử; không lặp vô hạn khi cấu hình sai.
5. Lịch bật/tắt/bảo trì không chạy chồng nhau và có thể tạm dừng.
6. Mã mời có thể hết hạn, giới hạn lượt và thu hồi; QR không chứa bí mật quản trị.
7. Danh sách bạn bè, whitelist và quyền tham gia được backend xác thực.
8. Bộ kiểm tra Modpack phát hiện thiếu dependency hoặc sai loader trong bộ mẫu thử.
9. Giới hạn tài nguyên được áp dụng thật ở backend; không thể vượt chỉ bằng sửa dữ liệu trình duyệt.
10. Khi thiết bị đầy tải, yêu cầu khởi chạy bị từ chối hoặc xếp hàng theo quy tắc công khai.
11. Bảng xếp hạng không cộng điểm lặp vô hạn; báo cáo vi phạm có trạng thái xử lý và nhật ký.
12. Mọi chỉ số Online, CPU, RAM, người chơi và nhiệt độ đều phải đến từ nguồn thực, có thời điểm cập nhật.



## 21. Đồng bộ phiên bản Minecraft mới

- Có trang **Phiên bản Minecraft** riêng, hiển thị Java Release, Java Snapshot, Bedrock Dedicated Server Release và Preview.
- Workflow `.github/workflows/minecraft-version-sync.yml` kiểm tra nguồn phiên bản mỗi 5 phút và hỗ trợ chạy thủ công.
- Java lấy danh mục từ version manifest chính thức của Mojang: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`.
- Bedrock lấy các đường dẫn tải từ dịch vụ liên kết tải xuống mà trang Minecraft chính thức sử dụng: `https://net-secondary.web.minecraft-services.net/api/v1.0/download/links`; trang tải chính thức: `https://www.minecraft.net/en-us/download/server/bedrock`.
- Khi phát hiện thay đổi, workflow cập nhật `minecraft-versions.json`, commit dữ liệu mới và yêu cầu xuất bản lại GitHub Pages.
- Danh mục phân biệt Release và Snapshot/Preview; bản thử nghiệm không được đánh dấu là bản ổn định.
- Selector tạo máy chủ đọc từ danh mục đã đồng bộ, không giữ danh sách phiên bản cũ viết cứng trong JavaScript.
- Kiểm tra đúng loại Edition: Java phải dùng phần mềm máy chủ Java; Bedrock phải dùng Bedrock Dedicated Server.
- Phiên bản Minecraft mới không tự động có nghĩa Paper/Purpur/Spigot/Forge/Fabric/NeoForge/Quilt hoặc Mod/Plugin đã tương thích. Cần xác minh riêng trước khi cho phép nâng cấp.
- Không tự thay thế server đang chạy hoặc tự mở thế giới bằng bản mới. Trình cập nhật tương lai phải dừng server an toàn, sao lưu đủ dữ liệu, kiểm tra tương thích, nâng cấp ở thư mục mới và xác minh kết quả trước khi chuyển sang bản mới.
- Lịch kiểm tra 5 phút là mục tiêu gần thời gian thực, không bảo đảm cập nhật tức thì tuyệt đối; GitHub Actions hoặc nguồn upstream có thể chậm/gián đoạn. Khi nguồn lỗi, giữ nguyên danh mục gần nhất và báo workflow thất bại.
- Bedrock Dedicated Server chính thức có gói Windows/Linux; tài liệu hỗ trợ Minecraft nêu không có gói ARM chính thức. Không được coi gói Linux x86-64 chạy trực tiếp trên Vivo Android ARM.

### Tiêu chí nghiệm thu đồng bộ phiên bản

1. Workflow lấy được manifest Java và dữ liệu tải Bedrock từ nguồn được chỉ định.
2. ID snapshot/legacy có ký tự đặc biệt được giữ nguyên như dữ liệu nguồn, không bị làm mất hoặc đổi mã.
3. URL tải phải là HTTPS và thuộc host chính thức được cho phép; Java server metadata phải có SHA-1 hợp lệ.
4. Danh mục chỉ được ghi khi cả dữ liệu bắt buộc đều hợp lệ; nếu nguồn lỗi thì không ghi đè danh mục cũ.
5. Khi Java/Bedrock có phiên bản mới, file JSON được cập nhật và trang GitHub Pages được triển khai lại khi Pages đã bật.
6. Biểu mẫu không cho ghép nhầm phiên bản Java với Bedrock Dedicated Server hoặc ngược lại.
7. Không tự nâng cấp thế giới đang chạy; mọi nâng cấp phải có sao lưu và bước xác nhận riêng.
