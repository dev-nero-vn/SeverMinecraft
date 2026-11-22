from pathlib import Path
import sys

path = Path(sys.argv[1] if len(sys.argv) > 1 else "project/desktop/main.py")
source = path.read_text(encoding="utf-8")
old = '        self.content=tk.Frame(body,bg=BG); self.content.pack(side="left",fill="both",expand=True,padx=24,pady=22)\n'
new = '''        right_area=tk.Frame(body,bg=BG)
        right_area.pack(side="left",fill="both",expand=True)
        self.content_canvas=tk.Canvas(right_area,bg=BG,highlightthickness=0,borderwidth=0)
        self.content_scrollbar=ttk.Scrollbar(right_area,orient="vertical",command=self.content_canvas.yview)
        self.content_canvas.configure(yscrollcommand=self.content_scrollbar.set)
        self.content_scrollbar.pack(side="right",fill="y")
        self.content_canvas.pack(side="left",fill="both",expand=True)
        content_host=tk.Frame(self.content_canvas,bg=BG)
        self._content_window=self.content_canvas.create_window((0,0),window=content_host,anchor="nw")
        self.content=tk.Frame(content_host,bg=BG)
        self.content.pack(fill="both",expand=True,padx=24,pady=22)
        content_host.bind("<Configure>",lambda _e:self.content_canvas.configure(scrollregion=self.content_canvas.bbox("all")))
        self.content_canvas.bind("<Configure>",lambda e:self.content_canvas.itemconfigure(self._content_window,width=e.width))
        def _wheel_over_content(event):
            widget=self.winfo_containing(event.x_root,event.y_root)
            while widget is not None:
                if widget is self.content_canvas:
                    units=int(-event.delta/120)
                    if units==0 and event.delta: units=-1 if event.delta>0 else 1
                    self.content_canvas.yview_scroll(units,"units")
                    return "break"
                widget=getattr(widget,"master",None)
            return None
        self.bind_all("<MouseWheel>",_wheel_over_content,add="+")
        self.bind_all("<Button-4>",lambda _e:self.content_canvas.yview_scroll(-1,"units"),add="+")
        self.bind_all("<Button-5>",lambda _e:self.content_canvas.yview_scroll(1,"units"),add="+")
'''
if old not in source:
    raise SystemExit("Desktop content frame marker not found")
source = source.replace(old, new, 1)
path.write_text(source, encoding="utf-8")
print(f"Added scrollable Windows content: {path}")
