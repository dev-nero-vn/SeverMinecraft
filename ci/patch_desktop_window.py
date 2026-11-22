from pathlib import Path
import re
import sys

path = Path(sys.argv[1] if len(sys.argv) > 1 else "project/desktop/main.py")
source = path.read_text(encoding="utf-8")

def replace_once(old, new):
    global source
    if old not in source:
        raise SystemExit("Patch target not found: " + old[:100])
    source = source.replace(old, new, 1)

replace_once(
    '        self._qr_img = None\n        self._build_styles(); self._build_shell(); self._show_page("Trang chủ")',
    '        self._qr_img = None\n'
    '        self.mirror_window = None\n'
    '        self.mirror_view = None\n'
    '        self.mirror_phone_screen = None\n'
    '        self.mirror_placeholder = None\n'
    '        self.mirror_window_status = None\n'
    '        self.preview_img = None\n'
    '        self.wallpaper_photo = None\n'
    '        self.wallpaper_base = None\n'
    '        self._shown_frame_seq = -1\n'
    '        self._latest_frame_bytes = None\n'
    '        self.record_writer = None\n'
    '        self.is_recording = False\n'
    '        self.record_button_ref = None\n'
    '        self._sidebar_collapsed = False\n'
    '        self._last_stream_dims = None\n'
    '        self._build_styles(); self._build_shell(); self._show_page("Trang chủ")',
)
replace_once(
    '        for name,icon in [("Trang chủ","⌂"),("Phòng chiếu","▣"),("Thiết bị","▣"),("Lịch sử","◷"),("Cài đặt","⚙"),("Trợ giúp","?"),("Giới thiệu","ⓘ")]:',
    '        for name,icon in [("Trang chủ","⌂"),("Thiết bị","▣"),("Lịch sử","◷"),("Cài đặt","⚙"),("Trợ giúp","?"),("Giới thiệu","ⓘ")]:',
)
replace_once('        elif name=="Phòng chiếu": self._mirror_page()\n', '')
replace_once(
    '        tk.Label(notice,text="✓  Tirror nhận và giải mã luồng video H.264 từ điện thoại qua Wi-Fi tại mục Phòng chiếu.",font=("Segoe UI",9,"bold"),fg="#137A50",bg="#EAF8F1",anchor="w",padx=14,pady=10).pack(fill="x")',
    '        tk.Label(notice,text="✓  Khi điện thoại bắt đầu chiếu, Tirror tự mở giao diện xem màn hình với khung điện thoại và thanh công cụ.",font=("Segoe UI",9,"bold"),fg="#137A50",bg="#EAF8F1",anchor="w",padx=14,pady=10).pack(fill="x")',
)
replace_once(
    '        ttk.Button(row,text="Mở phòng chiếu",style="Secondary.TButton",command=lambda:self._show_page("Phòng chiếu")).pack(side="right",padx=14)',
    '        ttk.Button(row,text="Xem màn hình",style="Secondary.TButton",command=self._open_mirror_window).pack(side="right",padx=14)',
)

viewer_methods = r'''
    def _make_mirror_wallpaper(self):
        from PIL import ImageDraw, ImageFilter
        w, h = 1600, 900
        img = Image.new("RGB", (w, h), "#0757B7")
        draw = ImageDraw.Draw(img, "RGBA")
        for y in range(h):
            t = y / max(1, h - 1)
            draw.line((0, y, w, y), fill=(int(8+12*t), int(72+60*t), int(170+60*t)))
        draw.ellipse((480, -360, 1750, 760), fill=(90, 170, 255, 130))
        draw.ellipse((-380, 300, 950, 1250), fill=(0, 22, 112, 115))
        return img.filter(ImageFilter.GaussianBlur(38))

    def _open_mirror_window(self):
        if self.mirror_window is not None:
            try:
                if self.mirror_window.winfo_exists():
                    self.mirror_window.deiconify(); self.mirror_window.lift(); return
            except tk.TclError: pass
        win = tk.Toplevel(self)
        self.mirror_window = win
        win.title("Tirror — Phòng chiếu không dây")
        win.geometry("1280x800"); win.minsize(820, 560)
        try: win.state("zoomed")
        except tk.TclError: pass
        win.configure(bg="#0757B7")
        try: win.iconbitmap(str(ASSETS / "tirror.ico"))
        except Exception: pass
        self.withdraw()
        self.wallpaper_base = self._make_mirror_wallpaper()
        self.wallpaper_photo = ImageTk.PhotoImage(self.wallpaper_base.resize((1280, 800), Image.Resampling.LANCZOS))
        self.mirror_wallpaper_label = tk.Label(win, image=self.wallpaper_photo, bd=0)
        self.mirror_wallpaper_label.place(x=0, y=0, relwidth=1, relheight=1)
        tk.Label(win, text="TIRROR  •  WIRELESS DISPLAY", font=("Segoe UI", 10, "bold"),
                 fg="white", bg="#164B8C", padx=16, pady=9).place(x=22, y=18)
        self.mirror_window_status = tk.Label(win,
            text=(f"●  Đang chiếu: {self.mirror.active_device}" if self.mirror.is_casting else "●  Đang chờ điện thoại kết nối"),
            font=("Segoe UI", 10, "bold"), fg="white", bg="#164B8C", padx=16, pady=9)
        self.mirror_window_status.place(relx=1, x=-22, y=18, anchor="ne")

        self.mirror_phone_shell = tk.Frame(win, bg="white", bd=0, highlightbackground="#DCE8F9", highlightthickness=3)
        self.mirror_phone_screen = tk.Frame(self.mirror_phone_shell, bg="#0B1C3C", bd=0)
        self.mirror_speaker = tk.Frame(self.mirror_phone_shell, bg="#52627A", bd=0)
        self.mirror_camera = tk.Frame(self.mirror_phone_shell, bg="#8090A8", bd=0)
        self.mirror_nav = tk.Frame(self.mirror_phone_shell, bg="white", bd=0)
        for glyph in ("‹", "○", "▢"):
            tk.Label(self.mirror_nav, text=glyph, font=("Segoe UI", 15), fg="#52627A", bg="white").pack(side="left", expand=True, fill="both")

        self.mirror_placeholder = tk.Frame(self.mirror_phone_screen, bg="#0B1C3C")
        self.mirror_placeholder.place(relx=0, rely=0, relwidth=1, relheight=1)
        status = tk.Frame(self.mirror_placeholder, bg="#0D2146", height=26)
        status.pack(fill="x"); status.pack_propagate(False)
        tk.Label(status, text="9:41", font=("Segoe UI", 8, "bold"), fg="white", bg="#0D2146").pack(side="left", padx=10)
        tk.Label(status, text="●  Wi-Fi  ▰", font=("Segoe UI", 8), fg="white", bg="#0D2146").pack(side="right", padx=10)
        tk.Label(self.mirror_placeholder, text="09:41", font=("Segoe UI", 32, "bold"), fg="white", bg="#0B1C3C").pack(anchor="w", padx=18, pady=(15, 0))
        tk.Label(self.mirror_placeholder, text="Thứ hai  •  Tirror", font=("Segoe UI", 9), fg="#BCD2F5", bg="#0B1C3C").pack(anchor="w", padx=20, pady=(0, 8))
        grid = tk.Frame(self.mirror_placeholder, bg="#0B1C3C"); grid.pack(fill="both", expand=True, padx=12, pady=2)
        apps = [("🌐","Web"),("✉","Mail"),("▦","Ảnh"),("⚙","Cài đặt"),("▶","Video"),("♫","Nhạc"),("☏","Gọi"),("◉","Camera")]
        for i, (glyph, label) in enumerate(apps):
            cell = tk.Frame(grid, bg="#0B1C3C"); cell.grid(row=i//4, column=i%4, sticky="nsew", padx=2, pady=3)
            tk.Label(cell, text=glyph, font=("Segoe UI Emoji", 17), fg="white", bg="#244A7E", width=2).pack()
            tk.Label(cell, text=label, font=("Segoe UI", 7), fg="#E6F0FF", bg="#0B1C3C").pack()
        for col in range(4): grid.grid_columnconfigure(col, weight=1)
        dock = tk.Frame(self.mirror_placeholder, bg="#243E66"); dock.pack(fill="x", padx=10, pady=(2, 10))
        for glyph in ("☏", "✉", "🌐", "◉", "▦"):
            tk.Label(dock, text=glyph, font=("Segoe UI Emoji", 15), fg="white", bg="#243E66", width=3, pady=5).pack(side="left", expand=True)
        self.mirror_view = tk.Label(self.mirror_phone_screen, bg="#05080F", bd=0, highlightthickness=0)
        self.mirror_view.place_forget()

        self.mirror_sidebar = tk.Frame(win, bg="white", highlightbackground="#D9E4F3", highlightthickness=1)
        self.mirror_sidebar_tab = tk.Button(self.mirror_sidebar, text="‹", font=("Segoe UI", 10, "bold"),
            fg="#45617F", bg="#EDF4FF", activebackground="#DDEAFF", bd=0, relief="flat",
            command=self._toggle_mirror_sidebar, cursor="hand2")
        self.mirror_sidebar_tab.pack(fill="x", padx=7, pady=(8, 7), ipady=4)
        self.mirror_sidebar_buttons = []
        self._add_mirror_tool("●", "Tài khoản", self._mirror_account_info, True)
        self._add_mirror_tool("▣", "Chụp ảnh", self._capture_mirror_screenshot)
        self._add_mirror_tool("◉", "Quay video", self._toggle_mirror_recording)
        self._add_mirror_tool("⌨", "Điều khiển PC", self._mirror_pc_control, alert=True)
        self._add_mirror_tool("⚙", "Cài đặt", self._mirror_open_settings)
        self._add_mirror_tool("?", "Trợ giúp", self._mirror_open_help)
        self._last_stream_dims = (self.mirror.output_width, self.mirror.output_height)
        self._shown_frame_seq = -1; self.preview_img = None
        win.bind("<Configure>", self._mirror_resize)
        win.protocol("WM_DELETE_WINDOW", self._close_mirror_window)
        self._layout_mirror_window()

    def _add_mirror_tool(self, glyph, label, command, active=False, alert=False):
        wrap = tk.Frame(self.mirror_sidebar, bg="white"); wrap.pack(fill="x", padx=6, pady=4)
        button = tk.Button(wrap, text=f"{glyph}\n{label}", font=("Segoe UI", 8, "bold"),
            fg=("white" if active else "#334B6C"), bg=("#1769E8" if active else "#F7FAFF"),
            activebackground=("#0D55D8" if active else "#E8F1FF"), relief="flat", bd=0,
            cursor="hand2", command=command, justify="center", height=3, wraplength=84)
        button.pack(fill="x", ipady=2)
        if alert: tk.Label(wrap, text="●", font=("Segoe UI", 9), fg="#E53935", bg="#F7FAFF").place(relx=.9, rely=.05, anchor="center")
        self.mirror_sidebar_buttons.append((button, label, glyph))
        if label == "Quay video": self.record_button_ref = button

    def _toggle_mirror_sidebar(self):
        self._sidebar_collapsed = not self._sidebar_collapsed
        self.mirror_sidebar_tab.configure(text=("›" if self._sidebar_collapsed else "‹"))
        for button, label, glyph in self.mirror_sidebar_buttons:
            button.configure(text=(glyph if self._sidebar_collapsed else f"{glyph}\n{label}"), height=(1 if self._sidebar_collapsed else 3))
        self._layout_mirror_window()

    def _mirror_resize(self, event=None):
        if event is not None and event.widget is not self.mirror_window: return
        if self.mirror_window is not None: self.mirror_window.after(100, self._layout_mirror_window)

    def _layout_mirror_window(self):
        win = self.mirror_window
        if win is None or not win.winfo_exists(): return
        width, height = max(820, win.winfo_width()), max(560, win.winfo_height())
        ow, oh = max(2, self.mirror.output_width), max(2, self.mirror.output_height)
        ratio = ow / oh
        side_w = 66 if self._sidebar_collapsed else 112
        max_w, max_h = max(240, int(width*.60)-side_w-65), max(280, int(height*.78)-105)
        if ratio >= 1:
            sw = min(max_w, max_h*ratio); sh = sw/ratio
        else:
            sh = min(max_h, max_w/ratio); sw = sh*ratio
        sw, sh = int(sw), int(sh)
        shell_w, shell_h = sw+38, sh+105
        x = max(14, int((width-shell_w-side_w-12)/2))
        y = max(65, int((height-shell_h)/2)+12)
        self.mirror_phone_shell.place(x=x, y=y, width=shell_w, height=shell_h)
        self.mirror_phone_screen.place(x=17, y=42, width=sw, height=sh)
        self.mirror_speaker.place(x=max(3, shell_w//2-28), y=17, width=42, height=5)
        self.mirror_camera.place(x=max(3, shell_w//2+25), y=15, width=7, height=7)
        self.mirror_nav.place(x=9, y=sh+47, width=shell_w-18, height=48)
        side_h = min(555, max(390, int(height*.72)))
        self.mirror_sidebar.place(x=x+shell_w+12, y=y+(shell_h-side_h)//2, width=side_w, height=side_h)

    def _mirror_toast(self, message):
        if self.mirror_window is None: return
        try:
            label = tk.Label(self.mirror_window, text=message, font=("Segoe UI", 10, "bold"),
                             fg="white", bg="#163D70", padx=18, pady=10)
            label.place(relx=.5, rely=.94, anchor="center")
            self.mirror_window.after(2600, label.destroy)
        except tk.TclError: pass

    def _mirror_account_info(self):
        self._mirror_toast("Tirror • " + (self.mirror.active_device or "Chưa có thiết bị đang chiếu"))

    def _capture_mirror_screenshot(self):
        if not self._latest_frame_bytes:
            self._mirror_toast("Chưa có hình ảnh để chụp"); return
        try:
            from datetime import datetime
            from pathlib import Path
            folder = Path.home() / "Pictures" / "Tirror"; folder.mkdir(parents=True, exist_ok=True)
            image = Image.frombytes("RGB", (self.mirror.output_width, self.mirror.output_height), self._latest_frame_bytes)
            image.save(folder / ("Tirror_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".png"))
            self._mirror_toast("Đã lưu ảnh trong Pictures\\Tirror")
        except Exception as exc: self._mirror_toast("Không chụp được ảnh: " + str(exc))

    def _toggle_mirror_recording(self):
        if self.is_recording:
            self._stop_mirror_recording(); self._mirror_toast("Đã lưu video quay màn hình"); return
        if not self._latest_frame_bytes:
            self._mirror_toast("Hãy bắt đầu chiếu trước khi quay"); return
        try:
            from datetime import datetime
            from pathlib import Path
            import imageio_ffmpeg
            folder = Path.home() / "Videos" / "Tirror"; folder.mkdir(parents=True, exist_ok=True)
            target = folder / ("Tirror_" + datetime.now().strftime("%Y%m%d_%H%M%S") + ".mp4")
            self.record_writer = imageio_ffmpeg.write_frames(str(target), (self.mirror.output_width, self.mirror.output_height),
                fps=30, codec="libx264", pix_fmt_in="rgb24", pix_fmt_out="yuv420p")
            self.record_writer.send(None); self.is_recording = True
            if self.record_button_ref: self.record_button_ref.configure(text="■\nDừng quay", fg="#D93838")
            self._mirror_toast("Đang quay màn hình…")
        except Exception as exc:
            self.record_writer = None; self.is_recording = False; self._mirror_toast("Không bật được quay: " + str(exc))

    def _stop_mirror_recording(self):
        writer, self.record_writer = self.record_writer, None; self.is_recording = False
        if writer is not None:
            try: writer.close()
            except Exception: pass
        if self.record_button_ref is not None:
            try: self.record_button_ref.configure(text="◉\nQuay video", fg="#334B6C")
            except tk.TclError: pass

    def _mirror_pc_control(self):
        self._mirror_toast("Điều khiển ngược từ PC chưa có trong bản này")

    def _mirror_open_settings(self):
        messagebox.showinfo("Cài đặt Tirror", "Chất lượng, FPS và tối ưu nhiệt được chọn trong ứng dụng Android.", parent=self.mirror_window)

    def _mirror_open_help(self):
        messagebox.showinfo("Trợ giúp Tirror", "Điện thoại và PC cần cùng Wi-Fi. Xoay điện thoại trước khi bắt đầu chiếu để khung tự chuyển dọc/ngang.", parent=self.mirror_window)

    def _close_mirror_window(self):
        win = self.mirror_window; self._stop_mirror_recording()
        self.mirror_window = None; self.mirror_view = None; self.mirror_window_status = None
        self.preview_img = None; self.wallpaper_photo = None; self.wallpaper_base = None
        self._shown_frame_seq = -1; self._latest_frame_bytes = None
        if win is not None:
            try: win.destroy()
            except tk.TclError: pass
        try: self.deiconify(); self.lift()
        except tk.TclError: pass

    def _render_mirror_frame(self):
        if not self.winfo_exists(): return
        win, view = self.mirror_window, self.mirror_view
        if win is not None and view is not None:
            try:
                if self.mirror.is_casting:
                    frame, seq = self.mirror.get_latest_frame()
                    if frame and seq != self._shown_frame_seq:
                        ow, oh = self.mirror.output_width, self.mirror.output_height
                        dims = (ow, oh)
                        if dims != self._last_stream_dims:
                            self._last_stream_dims = dims; self._layout_mirror_window()
                        self._latest_frame_bytes = frame
                        image = Image.frombytes("RGB", (ow, oh), frame)
                        image.thumbnail((max(160, self.mirror_phone_screen.winfo_width()),
                                         max(200, self.mirror_phone_screen.winfo_height())), Image.Resampling.LANCZOS)
                        self.preview_img = ImageTk.PhotoImage(image)
                        self.mirror_placeholder.place_forget()
                        view.configure(image=self.preview_img, text="")
                        view.place(x=0, y=0, relwidth=1, relheight=1)
                        self._shown_frame_seq = seq
                        if self.is_recording and self.record_writer is not None:
                            try: self.record_writer.send(frame)
                            except Exception: self._stop_mirror_recording()
                elif self._shown_frame_seq != -1:
                    self._latest_frame_bytes = None; view.place_forget()
                    self.mirror_placeholder.place(relx=0, rely=0, relwidth=1, relheight=1)
                    self.preview_img = None; self._shown_frame_seq = -1
                    if self.is_recording: self._stop_mirror_recording()
                if self.mirror_window_status is not None:
                    self.mirror_window_status.configure(text=(f"●  Đang chiếu: {self.mirror.active_device}" if self.mirror.is_casting else "●  Đang chờ điện thoại kết nối"))
            except (tk.TclError, ValueError) as exc:
                try: view.configure(image="", text=f"Lỗi hiển thị hình ảnh: {exc}")
                except tk.TclError: pass
        self.after(33, self._render_mirror_frame)
'''

pattern = re.compile(r'    def _mirror_page\(self\):\n.*?(?=    def _devices\(self\):)', re.S)
source, count = pattern.subn(lambda _m: viewer_methods, source, count=1)
if count != 1:
    raise SystemExit("Could not replace original mirror page")

replace_once(
    '            "Sau khi xác thực, hãy mở mục Phòng chiếu. Trên điện thoại, chọn PC đã lưu và nhấn Bắt đầu chiếu để cấp quyền chia sẻ màn hình Android."',
    '            "Sau khi xác thực, chọn PC đã lưu trên điện thoại và nhấn Bắt đầu chiếu. Tirror sẽ tự mở giao diện chiếu toàn màn hình."',
)
replace_once(
    '                    if self.current_page != "Phòng chiếu": self._show_page("Phòng chiếu")\n'
    '                    elif hasattr(self,"mirror_status"): self.mirror_status.configure(text=f"● Đang chiếu: {data.get(\'name\',\'Android\')}",fg=GREEN)\n',
    '                    self._open_mirror_window()\n'
    '                    if self.mirror_window_status: self.mirror_window_status.configure(text=f"●  Đang chiếu: {data.get(\'name\',\'Android\')}")\n',
)
replace_once(
    '                    if hasattr(self,"mirror_status") and self.current_page=="Phòng chiếu":\n'
    '                        self.mirror_status.configure(text="● Đang chờ điện thoại bắt đầu chiếu",fg=BLUE)\n',
    '                    if self.mirror_window_status: self.mirror_window_status.configure(text="●  Đang chờ điện thoại kết nối")\n',
)
replace_once(
    '                    if self.current_page=="Phòng chiếu" and hasattr(self,"mirror_view"):\n'
    '                        self.mirror_view.configure(text=data,image="")\n',
    '                    if self.mirror_window is not None: self._mirror_toast(data)\n',
)
replace_once(
    '    def _close(self):\n        self.mirror.stop(); self.server.stop(); self.destroy()\n',
    '    def _close(self):\n        self._stop_mirror_recording()\n        if self.mirror_window is not None: self._close_mirror_window()\n        self.mirror.stop(); self.server.stop(); self.destroy()\n',
)
path.write_text(source, encoding="utf-8")
print("Patched Tirror desktop viewer:", path)

# Make the video decode output follow the incoming screen orientation and hide
# the child FFmpeg process console on Windows.
mirror_path = path.with_name("mirroring.py")
mirror = mirror_path.read_text(encoding="utf-8")
if "import os\n" not in mirror:
    mirror = mirror.replace("import json\n", "import json\nimport os\n", 1)
needle = '        self._active_device = ""\n        self._active = threading.Event()\n'
if needle not in mirror: raise SystemExit("Missing MirrorReceiver init marker")
mirror = mirror.replace(needle, needle + '        self.output_width = OUTPUT_WIDTH\n        self.output_height = OUTPUT_HEIGHT\n', 1)
needle = '            ffmpeg = self._ffmpeg_path()\n            command = [\n'
if needle not in mirror: raise SystemExit("Missing FFmpeg command marker")
setup = '''            max_edge = max(OUTPUT_WIDTH, OUTPUT_HEIGHT)
            scale = min(1.0, max_edge / float(max(width, height)))
            output_width = max(2, int(width * scale) // 2 * 2)
            output_height = max(2, int(height * scale) // 2 * 2)
            with self._frame_lock:
                self.output_width, self.output_height = output_width, output_height
                self._latest_frame = None
                self._frame_seq += 1
            ffmpeg = self._ffmpeg_path()
            command = [
'''
mirror = mirror.replace(needle, setup, 1)
mirror = mirror.replace(
    '                "-vf", f"scale={OUTPUT_WIDTH}:{OUTPUT_HEIGHT}:force_original_aspect_ratio=decrease,pad={OUTPUT_WIDTH}:{OUTPUT_HEIGHT}:(ow-iw)/2:(oh-ih)/2",\n',
    '                "-vf", f"scale={output_width}:{output_height}:force_original_aspect_ratio=decrease,pad={output_width}:{output_height}:(ow-iw)/2:(oh-ih)/2",\n',
    1,
)
mirror = mirror.replace(
    '            decoder = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, bufsize=0)\n',
    '''            popen_options = {"stdin": subprocess.PIPE, "stdout": subprocess.PIPE, "stderr": subprocess.DEVNULL, "bufsize": 0}
            if os.name == "nt":
                startup = subprocess.STARTUPINFO()
                startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                startup.wShowWindow = subprocess.SW_HIDE
                popen_options["startupinfo"] = startup
                popen_options["creationflags"] = subprocess.CREATE_NO_WINDOW
            decoder = subprocess.Popen(command, **popen_options)
''',
    1,
)
mirror = mirror.replace('            frame_size = OUTPUT_WIDTH * OUTPUT_HEIGHT * 3\n', '            frame_size = self.output_width * self.output_height * 3\n', 1)
mirror_path.write_text(mirror, encoding="utf-8")
print("Patched orientation scaling and hidden FFmpeg console:", mirror_path)
