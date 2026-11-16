# Nero Windows Virtual

Ứng dụng Android điều khiển máy ảo Windows 11 từ xa. Windows được cài vào ổ QEMU QCOW2 nằm trên máy chủ tự lưu trữ; APK chỉ truyền hình ảnh và thao tác, không tải ổ Windows về điện thoại.

## Giới hạn hạ tầng cần biết

- Đây là mô hình tự lưu trữ: máy tính/máy chủ x86-64 của bạn làm host. Muốn truy cập từ xa, host phải bật, có Internet và có đường mạng riêng an toàn.
- GitHub lưu mã nguồn và APK; GitHub không phải máy chủ Windows chạy liên tục và không phải ổ đĩa Cloud.
- Mô hình không thuê máy chủ Cloud, nhưng vẫn sử dụng điện, Internet và tài nguyên của máy host. Không thể cam kết Windows 11 Cloud công cộng 24/7 hoàn toàn miễn phí nếu không có host.
- ISO Windows 11 phải do bạn lấy từ Microsoft và đặt trên host. Không đưa ISO, khóa sản phẩm hoặc file QCOW2 vào Git.
- ISO tải miễn phí không đồng nghĩa giấy phép kích hoạt Windows miễn phí.

## Thành phần

- android/: APK Android tiếng Việt.
- host/: API điều khiển QEMU, TPM 2.0, UEFI và VNC qua WebSocket.
- .github/workflows/nero-windows-virtual.yml: build APK và đóng gói ZIP chứa APK.

## Yêu cầu máy host

Máy host cần CPU x86-64 có ảo hóa phần cứng, RAM phù hợp (khuyến nghị từ 8 GB trở lên cho host và VM), dung lượng lưu trữ trống đủ cho ổ ảo Windows (mặc định 80 GB), cùng các chương trình:

- QEMU: qemu-system-x86_64 và qemu-img
- swtpm (TPM 2.0 giả lập cho máy ảo)
- Firmware OVMF/UEFI có Secure Boot
- Python 3.11 trở lên

Trên Linux, QEMU cần KVM (/dev/kvm). Trên Windows, QEMU cần WHPX/Windows Hypervisor Platform. Tên và đường dẫn firmware OVMF có thể khác tùy bản cài.

## Cấu hình host

1. Sao chép host/.env.example thành host/.env.
2. Điền đường dẫn ISO Windows 11 x64, QEMU, swtpm, OVMF CODE và OVMF VARS.
3. Đổi NERO_API_TOKEN thành chuỗi ngẫu nhiên dài, riêng tư.
4. Chạy run-host.ps1 trên Windows hoặc run-host.sh trên Linux.
5. Mặc định API chạy cổng 8765; chỉ cho phép truy cập từ LAN/VPN tin cậy. Không mở thẳng API/VNC ra Internet và không cấu hình chuyển tiếp cổng công khai.

NERO_VM_DIR là nơi lưu Nero-Windows11.qcow2, UEFI variables và log. File QCOW2 được tạo dạng sparse; dung lượng thực tăng theo dữ liệu ghi nhưng có thể phát triển đến giới hạn cấu hình.

## Quy trình cài Windows 11

1. Mở APK, nhập URL host và API token.
2. Nhấn Khởi động bộ cài. QEMU tạo/giữ ổ Nero-Windows11.qcow2, boot từ ISO và hiển thị Windows Setup thật qua noVNC.
3. Trong Windows Setup, chọn ổ đĩa ảo và cài Windows.
4. Khi setup hoàn tất và máy ảo yêu cầu khởi động lại, nhấn Khởi động Windows đã cài trong APK. Host khởi động lại VM từ ổ QCOW2, không gắn ISO nữa.
5. Những lần sau chọn Khởi động Windows đã cài. Dữ liệu vẫn nằm trong file QCOW2 trên host.

Không nhấn boot từ ổ đĩa trước khi quá trình cài đặt Windows hoàn tất.

## Bảo mật

- Mỗi API request cần Authorization: Bearer <NERO_API_TOKEN>.
- Trang desktop và WebSocket cũng xác thực token.
- Không dùng HTTP qua Internet công cộng. Dùng LAN hoặc VPN tin cậy; nếu cần truy cập ngoài LAN, tự cấu hình VPN/HTTPS trước khi dùng.
- Token được nhập trong APK và giữ trong bộ nhớ ứng dụng; không đưa token vào GitHub, ảnh chụp màn hình hoặc issue công khai.
- Tắt host API khi không sử dụng.

## Trạng thái

Đây là bản nền mã nguồn. Chưa có host được triển khai, chưa có kiểm thử cài Windows end-to-end trên máy vật lý, và chưa thể xác nhận Windows 11 đã khởi động thành công. Workflow build tạo ZIP chứa APK khi GitHub Actions chạy thành công.
