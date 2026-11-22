from pathlib import Path
import re
import sys

path = Path(sys.argv[1] if len(sys.argv) > 1 else "project/desktop/main.py")
source = path.read_text(encoding="utf-8")

def replace_once(old: str, new: str) -> None:
    global source
    if old not in source:
        raise SystemExit(f"Patch target not found: {old[:100]!r}")
    source = source.replace(old, new, 1)

replace_once(
    '        self._qr_img = None\n        self._build_styles(); self._build_shell(); self._show_page("Trang chủ")',
    '        self._qr_img = None\n'
    '        self.mirror_window = None\n'
    '        self.mirror_view = None\n'
    '        self.mirror_window_status = None\n'
    '        self.preview_img = None\n'
    '        self._shown_frame_seq = -1\n'
    '        self._build_styles(); self._build_shell(); self._show_page("Trang chủ")',
)
replace_once(
    '        self.top_status=tk.Label(self.top_right,text="Wi-Fi sẵn sàng",font=("Segoe UI",10,"bold"),fg=TEXT,bg="white"); self.top_status.pack(side="left",padx=8)\n',
    '        self.top_status=tk.Label(self.top_right,text="Wi-Fi sẵn sàng",font=("Segoe UI",10,"bold"),fg=TEXT,bg="white"); self.top_status.pack(side="left",padx=8)\n'
    '        self.open_mirror_button=ttk.Button(self.top_right,text="Mở cửa sổ chiếu",style="Secondary.TButton",command=self._open_mirror_window)\n'
    '        self.open_mirror_button.pack(side="right",padx=(16,0)); self.open_mirror_button.state(["disabled"])\n',
)
replace_once(
    '        for name,icon in [("Trang chủ","⌂"),("Phòng chiếu","▣"),("Thiết bị","▣"),("Lịch sử","◷"),("Cài đặt","⚙"),("Trợ giúp","?"),("Giới thiệu","ⓘ")]:',
    '        for name,icon in [("Trang chủ","⌂"),("Thiết bị","▣"),("Lịch sử","◷"),("Cài đặt","⚙"),("Trợ giúp","?"),("Giới thiệu","ⓘ")]:',
)
replace_once('        elif name=="Phòng chiếu": self._mirror_page()\n', '')
replace_once(
    '        tk.Label(notice,text="✓  Tirror nhận và giải mã luồng video H.264 từ điện thoại qua Wi-Fi tại mục Phòng chiếu.",font=("Segoe UI",9,"bold"),fg="#137A50",bg="#EAF8F1",anchor="w",padx=14,pady=10).pack(fill="x")',
    '        tk.Label(notice,text="✓  Khi điện thoại bắt đầu chiếu, màn hình sẽ tự mở trong một cửa sổ riêng trên Windows.",font=("Segoe UI",9,"bold"),fg="#137A50",bg="#EAF8F1",anchor="w",padx=14,pady=10).pack(fill="x")',
)
replace_once(
    '        ttk.Button(row,text="Mở phòng chiếu",style="Secondary.TButton",command=lambda:self._show_page("Phòng chiếu")).pack(side="right",padx=14)',
    '        ttk.Button(row,text="Xem màn hình",style="Secondary.TButton",command=self._open_mirror_window).pack(side="right",padx=14)',
)

viewer_methods = '''    def _open_mirror_window(self):
        if self.mirror_window is not None:
            try:
                if self.mirror_window.winfo_exists():
                    self.mirror_window.deiconify(); self.mirror_window.lift(); self.mirror_window.focus_force()
                    return
            except tk.TclError:
                pass
        win=tk.Toplevel(self)
        self.mirror_window=win
        win.title(f"Tirror — {self.mirror.active_device or 'Màn hình Android'}")
        win.geometry("1180x760"); win.minsize(720,460); win.configure(bg="#0B1425")
        try: win.iconbitmap(str(ASSETS / "tirror.ico"))
        except Exception: pass
        toolbar=tk.Frame(win,bg="#101D33",height=56); toolbar.pack(fill="x"); toolbar.pack_propagate(False)
        self.mirror_window_status=tk.Label(toolbar,text=(f"● Đang chiếu: {self.mirror.active_device}" if self.mirror.is_casting else "● Đang chờ điện thoại bắt đầu chiếu"),font=("Segoe UI",10,"bold"),fg=GREEN if self.mirror.is_casting else "#9DB8E8",bg="#101D33")
        self.mirror_window_status.pack(side="left",padx=18)
        ttk.Button(toolbar,text="Dừng chiếu",style="Secondary.TButton",command=lambda:self.mirror.stop_cast("Đã dừng chiếu từ cửa sổ xem màn hình.")).pack(side="right",padx=14,pady=9)
        self.mirror_view=tk.Label(win,text="Đang chờ hình ảnh từ điện thoại\\n\\nMở Tirror Android → chọn PC đã ghép nối → Bắt đầu chiếu",font=("Segoe UI",15,"bold"),fg="#DCE9FF",bg="#0B1425",justify="center")
        self.mirror_view.pack(fill="both",expand=True,padx=12,pady=12)
        self._shown_frame_seq=-1; self.preview_img=None
        win.protocol("WM_DELETE_WINDOW",self._close_mirror_window)

    def _close_mirror_window(self):
        win=self.mirror_window
        self.mirror_window=None; self.mirror_view=None; self.mirror_window_status=None
        self.preview_img=None; self._shown_frame_seq=-1
        if win is not None:
            try: win.destroy()
            except tk.TclError: pass

    def _render_mirror_frame(self):
        if not self.winfo_exists(): return
        win=self.mirror_window; view=self.mirror_view
        if win is not None and view is not None:
            try:
                if win.winfo_exists() and view.winfo_exists():
                    if self.mirror.is_casting:
                        frame,seq=self.mirror.get_latest_frame()
                        if frame and seq!=self._shown_frame_seq:
                            img=Image.frombytes("RGB",(OUTPUT_WIDTH,OUTPUT_HEIGHT),frame)
                            win.update_idletasks()
                            max_w=max(320,view.winfo_width()-24); max_h=max(240,view.winfo_height()-24)
                            img.thumbnail((max_w,max_h),Image.Resampling.LANCZOS)
                            self.preview_img=ImageTk.PhotoImage(img)
                            view.configure(image=self.preview_img,text="")
                            self._shown_frame_seq=seq
                    elif self._shown_frame_seq!=-1:
                        view.configure(image="",text="Đang chờ hình ảnh từ điện thoại\\n\\nMở Tirror Android → chọn PC đã ghép nối → Bắt đầu chiếu")
                        self.preview_img=None; self._shown_frame_seq=-1
            except (tk.TclError,ValueError) as exc:
                self.preview_img=None; self._shown_frame_seq=-1
                try: view.configure(image="",text=f"Không hiển thị được khung hình: {exc}")
                except tk.TclError: pass
        self.after(33,self._render_mirror_frame)

'''
pattern = re.compile(r'    def _mirror_page\(self\):\n.*?(?=    def _devices\(self\):)', re.S)
source, count = pattern.subn(viewer_methods, source, count=1)
if count != 1:
    raise SystemExit("Could not replace the old in-app mirroring page")

replace_once(
    '            "Sau khi xác thực, hãy mở mục Phòng chiếu. Trên điện thoại, chọn PC đã lưu và nhấn Bắt đầu chiếu để cấp quyền chia sẻ màn hình Android."',
    '            "Sau khi xác thực, trên điện thoại chọn PC đã lưu và nhấn Bắt đầu chiếu. Windows sẽ tự mở một cửa sổ riêng để hiển thị màn hình Android."',
)
replace_once(
    '                    if self.current_page != "Phòng chiếu": self._show_page("Phòng chiếu")\n'
    '                    elif hasattr(self,"mirror_status"): self.mirror_status.configure(text=f"● Đang chiếu: {data.get(\'name\',\'Android\')}",fg=GREEN)\n',
    '                    self.open_mirror_button.state(["!disabled"])\n'
    '                    if self.mirror_window is None or not self.mirror_window.winfo_exists(): self._open_mirror_window()\n'
    '                    else:\n'
    '                        self.mirror_window.deiconify(); self.mirror_window.lift()\n'
    '                        self.mirror_window.title(f"Tirror — {data.get(\'name\',\'Android\')}")\n'
    '                        if self.mirror_window_status: self.mirror_window_status.configure(text=f"● Đang chiếu: {data.get(\'name\',\'Android\')}",fg=GREEN)\n',
)
replace_once(
    '                    if hasattr(self,"mirror_status") and self.current_page=="Phòng chiếu":\n'
    '                        self.mirror_status.configure(text="● Đang chờ điện thoại bắt đầu chiếu",fg=BLUE)\n',
    '                    self.open_mirror_button.state(["disabled"])\n'
    '                    if self.mirror_window_status:\n'
    '                        self.mirror_window_status.configure(text="● Đã dừng chiếu — đang chờ điện thoại",fg="#9DB8E8")\n',
)
replace_once(
    '                    if self.current_page=="Phòng chiếu" and hasattr(self,"mirror_view"):\n'
    '                        self.mirror_view.configure(text=data,image="")\n',
    '                    if self.mirror_view is not None:\n'
    '                        self.mirror_view.configure(text=data,image="")\n',
)

path.write_text(source, encoding="utf-8")
print(f"Updated {path}")
