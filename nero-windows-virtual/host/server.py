from __future__ import annotations

import asyncio
import hmac
import os
import shutil
import subprocess
import threading
from pathlib import Path
from typing import Literal

from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse

load_dotenv()

app = FastAPI(title="Nero Windows Virtual Host", version="0.1.0")
_vm_process: subprocess.Popen | None = None
_tpm_process: subprocess.Popen | None = None
_vm_mode: str | None = None
_vm_lock = threading.Lock()


def setting(name: str, default: str = "") -> str:
    return os.getenv(name, default).strip()


def expected_token() -> str:
    token = setting("NERO_API_TOKEN")
    if len(token) < 24 or token.startswith("REPLACE_"):
        raise HTTPException(status_code=503, detail="Host chưa được cấu hình NERO_API_TOKEN đủ mạnh.")
    return token


def check_token(value: str | None) -> None:
    expected = expected_token()
    if not value or not hmac.compare_digest(value, expected):
        raise HTTPException(status_code=401, detail="Token không hợp lệ.")


def require_bearer(authorization: str | None) -> None:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Thiếu Bearer token.")
    check_token(authorization[7:])


def resolve_executable(env_name: str, fallback: str) -> str:
    value = setting(env_name, fallback)
    found = shutil.which(value) if not Path(value).is_file() else value
    if not found:
        raise HTTPException(status_code=500, detail=f"Không tìm thấy chương trình {env_name}: {value}")
    return str(found)


def vm_directory() -> Path:
    path = Path(setting("NERO_VM_DIR", "./NeroWindowsCloud")).expanduser().resolve()
    path.mkdir(parents=True, exist_ok=True)
    return path


def stop_process(process: subprocess.Popen | None) -> None:
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def stop_vm_unlocked() -> None:
    global _vm_process, _tpm_process, _vm_mode
    stop_process(_vm_process)
    stop_process(_tpm_process)
    _vm_process = None
    _tpm_process = None
    _vm_mode = None


def start_vm(mode: Literal["install", "disk"]) -> dict:
    global _vm_process, _tpm_process, _vm_mode
    with _vm_lock:
        if _vm_process is not None and _vm_process.poll() is None:
            if _vm_mode == mode:
                return {"ok": True, "state": "already_running", "mode": mode}
            stop_vm_unlocked()

        qemu = resolve_executable("NERO_QEMU_BIN", "qemu-system-x86_64")
        qemu_img = resolve_executable("NERO_QEMU_IMG_BIN", "qemu-img")
        swtpm = resolve_executable("NERO_SWTPM_BIN", "swtpm")
        iso_value = setting("NERO_ISO_PATH")
        iso = Path(iso_value).expanduser() if iso_value else None
        vm_dir = vm_directory()
        disk = vm_dir / "Nero-Windows11.qcow2"
        vars_source = Path(setting("NERO_OVMF_VARS")).expanduser()
        ovmf_code = Path(setting("NERO_OVMF_CODE")).expanduser()

        if not ovmf_code.is_file():
            raise HTTPException(status_code=500, detail="Không tìm thấy firmware OVMF CODE. Kiểm tra NERO_OVMF_CODE.")
        if not vars_source.is_file():
            raise HTTPException(status_code=500, detail="Không tìm thấy firmware OVMF VARS. Kiểm tra NERO_OVMF_VARS.")
        if mode == "install" and (iso is None or not iso.is_file()):
            raise HTTPException(status_code=400, detail="Không tìm thấy ISO. Đặt NERO_ISO_PATH trỏ tới ISO Windows 11 trên host.")
        if mode == "disk" and not disk.is_file():
            raise HTTPException(status_code=400, detail="Chưa có ổ đĩa ảo. Hãy chạy bộ cài Windows 11 trước.")

        if not disk.exists():
            result = subprocess.run(
                [qemu_img, "create", "-f", "qcow2", str(disk), setting("NERO_DISK_SIZE", "80G")],
                capture_output=True, text=True, timeout=90
            )
            if result.returncode != 0:
                raise HTTPException(status_code=500, detail=f"Không tạo được ổ QCOW2: {result.stderr[-1500:]}")

        vars_copy = vm_dir / "OVMF_VARS.fd"
        if not vars_copy.exists():
            shutil.copyfile(vars_source, vars_copy)

        tpm_state = vm_dir / "tpm-state"
        tpm_state.mkdir(exist_ok=True)
        server_port = setting("NERO_TPM_SERVER_PORT", "2321")
        control_port = setting("NERO_TPM_CONTROL_PORT", "2322")
        tpm_log = (vm_dir / "swtpm.log").open("ab")
        _tpm_process = subprocess.Popen(
            [
                swtpm, "socket",
                "--tpmstate", f"dir={tpm_state}",
                "--server", f"type=tcp,port={server_port}",
                "--ctrl", f"type=tcp,port={control_port}",
                "--flags", "not-need-init,startup-clear",
                "--tpm2",
            ],
            stdout=tpm_log, stderr=subprocess.STDOUT
        )

        accel = setting("NERO_ACCEL", "whpx" if os.name == "nt" else "kvm")
        cpus = setting("NERO_CPUS", "4")
        memory = setting("NERO_MEMORY_MB", "6144")
        vnc_port = int(setting("NERO_VNC_PORT", "5901"))
        args = [
            qemu,
            "-name", "Nero-Windows11",
            "-machine", f"q35,accel={accel}",
            "-cpu", "host",
            "-smp", cpus,
            "-m", memory,
            "-drive", f"if=pflash,format=raw,unit=0,readonly=on,file={ovmf_code}",
            "-drive", f"if=pflash,format=raw,unit=1,file={vars_copy}",
            "-chardev", f"socket,id=chrtpm,host=127.0.0.1,port={server_port}",
            "-tpmdev", "emulator,id=tpm0,chardev=chrtpm",
            "-device", "tpm-tis,tpmdev=tpm0",
            "-device", "ich9-ahci,id=sata",
            "-drive", f"if=none,id=osdisk,file={disk},format=qcow2",
            "-device", "ide-hd,drive=osdisk,bus=sata.0",
            "-device", "qemu-xhci",
            "-device", "usb-kbd",
            "-device", "usb-tablet",
            "-netdev", "user,id=net0",
            "-device", "e1000,netdev=net0",
            "-display", "none",
            "-vnc", f"127.0.0.1:{vnc_port - 5900}",
            "-no-reboot",
        ]
        if mode == "install":
            assert iso is not None
            args.extend([
                "-drive", f"if=none,id=cdrom,file={iso},media=cdrom,readonly=on",
                "-device", "ide-cd,drive=cdrom,bus=sata.1",
                "-boot", "order=d,menu=on",
            ])
        else:
            args.extend(["-boot", "order=c"])

        log = (vm_dir / "qemu.log").open("ab")
        try:
            _vm_process = subprocess.Popen(args, stdout=log, stderr=subprocess.STDOUT)
        except Exception:
            stop_process(_tpm_process)
            _tpm_process = None
            raise
        _vm_mode = mode
        return {"ok": True, "state": "starting", "mode": mode, "disk": str(disk)}


def status_payload() -> dict:
    running = _vm_process is not None and _vm_process.poll() is None
    disk = vm_directory() / "Nero-Windows11.qcow2"
    iso_value = setting("NERO_ISO_PATH")
    return {
        "ok": True,
        "running": running,
        "pid": _vm_process.pid if running else None,
        "mode": _vm_mode if running else None,
        "diskExists": disk.is_file(),
        "isoConfigured": bool(iso_value) and Path(iso_value).is_file(),
    }


@app.get("/api/status")
def api_status(authorization: str | None = Header(default=None)) -> dict:
    require_bearer(authorization)
    return status_payload()


@app.post("/api/start")
def api_start(
    mode: Literal["install", "disk"] = Query(default="install"),
    authorization: str | None = Header(default=None),
) -> dict:
    require_bearer(authorization)
    return start_vm(mode)


@app.post("/api/stop")
def api_stop(authorization: str | None = Header(default=None)) -> dict:
    require_bearer(authorization)
    with _vm_lock:
        stop_vm_unlocked()
    return {"ok": True, "state": "stopped"}


@app.get("/desktop", response_class=HTMLResponse)
def desktop(token: str = Query(...)) -> HTMLResponse:
    check_token(token)
    html = r"""<!doctype html>
<html lang="vi">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="referrer" content="no-referrer">
<title>Nero Windows Virtual</title>
<style>
html,body,#screen{width:100%;height:100%;margin:0;background:#090e18;color:#fff;font-family:Arial,sans-serif}
#screen{display:flex;align-items:center;justify-content:center}
#message{padding:20px;text-align:center;color:#cbd5e1}
</style>
</head>
<body><div id="screen"><div id="message">Đang kết nối màn hình máy ảo…</div></div>
<script type="module">
import RFB from "https://cdn.jsdelivr.net/npm/@novnc/novnc@1.5.0/core/rfb.js";
const token = new URLSearchParams(location.search).get("token") || "";
const scheme = location.protocol === "https:" ? "wss://" : "ws://";
const socketUrl = scheme + location.host + "/ws?token=" + encodeURIComponent(token);
const screen = document.getElementById("screen");
try {
  const rfb = new RFB(screen, socketUrl);
  rfb.scaleViewport = true;
  rfb.resizeSession = false;
  rfb.showDotCursor = true;
  rfb.addEventListener("disconnect", () => {
    screen.innerHTML = '<div id="message">Mất kết nối máy ảo. Hãy kiểm tra host và trạng thái VM.</div>';
  });
} catch (error) {
  document.getElementById("message").textContent = "Không khởi tạo được màn hình: " + error;
}
</script></body></html>"""
    return HTMLResponse(html)


@app.websocket("/ws")
async def vnc_websocket(websocket: WebSocket) -> None:
    try:
        check_token(websocket.query_params.get("token"))
    except HTTPException:
        await websocket.close(code=1008)
        return

    try:
        reader, writer = await asyncio.open_connection(
            "127.0.0.1", int(setting("NERO_VNC_PORT", "5901"))
        )
    except OSError:
        await websocket.close(code=1011)
        return

    await websocket.accept()

    async def client_to_vnc() -> None:
        while True:
            event = await websocket.receive()
            if event["type"] == "websocket.disconnect":
                return
            data = event.get("bytes")
            if data is None:
                text = event.get("text")
                if text is None:
                    continue
                data = text.encode("utf-8")
            writer.write(data)
            await writer.drain()

    async def vnc_to_client() -> None:
        while True:
            data = await reader.read(65536)
            if not data:
                return
            await websocket.send_bytes(data)

    tasks = [
        asyncio.create_task(client_to_vnc()),
        asyncio.create_task(vnc_to_client()),
    ]
    try:
        done, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in pending:
            task.cancel()
        for task in done:
            task.result()
    except (WebSocketDisconnect, OSError, RuntimeError):
        pass
    finally:
        for task in tasks:
            if not task.done():
                task.cancel()
        writer.close()
        try:
            await writer.wait_closed()
        except OSError:
            pass
        try:
            await websocket.close()
        except RuntimeError:
            pass
