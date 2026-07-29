#!/usr/bin/env python3
"""
BlueKey PC Companion Server
Receives input commands from the BlueKey Android app over WiFi
and injects them as real keyboard/mouse events on this PC.

Requirements:
    pip install pynput

Usage:
    python pc_server.py              # listen on 0.0.0.0:8080
    python pc_server.py --port 9090  # custom port
    python pc_server.py --host 0.0.0.0 --port 8080

In the BlueKey app:
    Connect → WiFi → enter this PC's local IP (e.g. 192.168.1.5)
"""

import argparse
import json
import socket
import threading

try:
    from pynput.keyboard import Key, Controller as KbController
    from pynput.mouse import Button, Controller as MouseController
except ImportError:
    print("[!] pynput not found. Install it with:  pip install pynput")
    raise SystemExit(1)

# ── HID keycode → pynput key map ────────────────────────────────────────────

KEYCODE_MAP = {
    # Letters a–z  (HID 0x04–0x1D)
    **{0x04 + i: chr(ord('a') + i) for i in range(26)},
    # Digits 1–9, 0
    0x1E: '1', 0x1F: '2', 0x20: '3', 0x21: '4', 0x22: '5',
    0x23: '6', 0x24: '7', 0x25: '8', 0x26: '9', 0x27: '0',
    # Special
    0x28: Key.enter,
    0x29: Key.esc,
    0x2A: Key.backspace,
    0x2B: Key.tab,
    0x2C: Key.space,
    0x2D: '-',  0x2E: '=',  0x2F: '[',  0x30: ']',  0x31: '\\',
    0x33: ';',  0x34: "'",  0x35: '`',  0x36: ',',  0x37: '.',  0x38: '/',
    0x39: Key.caps_lock,
    0x4C: Key.delete,
    0x4F: Key.right,
    0x50: Key.left,
    0x51: Key.down,
    0x52: Key.up,
    0x3A: Key.f1,  0x3B: Key.f2,  0x3C: Key.f3,  0x3D: Key.f4,
    0x3E: Key.f5,  0x3F: Key.f6,  0x40: Key.f7,  0x41: Key.f8,
    0x42: Key.f9,  0x43: Key.f10, 0x44: Key.f11, 0x45: Key.f12,
}

MODIFIER_MAP = {
    0x01: Key.ctrl_l,
    0x02: Key.shift_l,
    0x04: Key.alt_l,
    0x08: Key.cmd,
    0x10: Key.ctrl_r,
    0x20: Key.shift_r,
    0x40: Key.alt_r,
    0x80: Key.cmd_r,
}

CONSUMER_MAP = {
    0xCD: Key.media_play_pause,
    0xB5: Key.media_next,
    0xB6: Key.media_previous,
    0xE2: Key.media_volume_mute,
    0xE9: Key.media_volume_up,
    0xEA: Key.media_volume_down,
}

# ── Controllers ──────────────────────────────────────────────────────────────

kb = KbController()
mouse = MouseController()
_pressed_mods: set = set()
_pressed_keys: set = set()


def handle_keyboard(data: dict) -> None:
    modifier = data.get('modifier', 0)
    keys = [k for k in data.get('keys', []) if k != 0]

    # Sync modifier keys
    new_mods = {key for bit, key in MODIFIER_MAP.items() if modifier & bit}
    for m in _pressed_mods - new_mods:
        kb.release(m)
    for m in new_mods - _pressed_mods:
        kb.press(m)
    _pressed_mods.clear()
    _pressed_mods.update(new_mods)

    # Sync regular keys
    mapped = {KEYCODE_MAP[k] for k in keys if k in KEYCODE_MAP}
    for k in _pressed_keys - mapped:
        try:
            kb.release(k)
        except Exception:
            pass
    for k in mapped - _pressed_keys:
        try:
            kb.press(k)
        except Exception:
            pass
    _pressed_keys.clear()
    _pressed_keys.update(mapped)


def handle_mouse(data: dict) -> None:
    buttons = data.get('buttons', 0)
    dx = data.get('dx', 0)
    dy = data.get('dy', 0)
    wheel = data.get('wheel', 0)

    if dx != 0 or dy != 0:
        mouse.move(dx, dy)
    if wheel != 0:
        mouse.scroll(0, -wheel)

    _sync_mouse_button(buttons & 0x01, Button.left)
    _sync_mouse_button(buttons & 0x02, Button.right)
    _sync_mouse_button(buttons & 0x04, Button.middle)


_mouse_buttons_pressed: dict = {}


def _sync_mouse_button(pressed: int, button: Button) -> None:
    was = _mouse_buttons_pressed.get(button, False)
    if pressed and not was:
        mouse.press(button)
        _mouse_buttons_pressed[button] = True
    elif not pressed and was:
        mouse.release(button)
        _mouse_buttons_pressed[button] = False


def handle_consumer(data: dict) -> None:
    usage = data.get('usage', 0)
    if usage == 0:
        return
    key = CONSUMER_MAP.get(usage)
    if key:
        kb.press(key)
        kb.release(key)
    else:
        print(f"  [?] Unknown consumer usage: 0x{usage:X}")


def handle_gamepad(data: dict) -> None:
    # Gamepad → keyboard arrow key mapping for basic game support
    buttons = data.get('buttons', 0)
    lx = data.get('lx', 0)
    ly = data.get('ly', 0)
    dpad_map = {
        8:  Key.up,
        9:  Key.down,
        10: Key.left,
        11: Key.right,
    }
    for bit, key in dpad_map.items():
        if buttons & (1 << (bit - 1)):
            kb.press(key)
        else:
            try:
                kb.release(key)
            except Exception:
                pass
    # Left stick as arrow keys
    if ly < -40:
        kb.press(Key.up)
    elif ly > 40:
        kb.press(Key.down)
    if lx < -40:
        kb.press(Key.left)
    elif lx > 40:
        kb.press(Key.right)


# ── Client handler ───────────────────────────────────────────────────────────

def handle_client(conn: socket.socket, addr: tuple) -> None:
    print(f"[+] BlueKey connected from {addr[0]}:{addr[1]}")
    buf = ""
    try:
        while True:
            chunk = conn.recv(4096)
            if not chunk:
                break
            buf += chunk.decode('utf-8', errors='ignore')
            while '\n' in buf:
                line, buf = buf.split('\n', 1)
                line = line.strip()
                if not line:
                    continue
                try:
                    msg = json.loads(line)
                    t = msg.get('type', '')
                    if   t == 'keyboard': handle_keyboard(msg)
                    elif t == 'mouse':    handle_mouse(msg)
                    elif t == 'consumer': handle_consumer(msg)
                    elif t == 'gamepad':  handle_gamepad(msg)
                except json.JSONDecodeError:
                    pass
    except Exception as e:
        print(f"[-] Error: {e}")
    finally:
        conn.close()
        print(f"[-] BlueKey disconnected from {addr[0]}")


# ── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description='BlueKey PC Companion Server')
    parser.add_argument('--host', default='0.0.0.0', help='Bind address (default: 0.0.0.0)')
    parser.add_argument('--port', type=int, default=8080, help='Port (default: 8080)')
    args = parser.parse_args()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((args.host, args.port))
    server.listen(1)

    # Find the real local IP to display
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        local_ip = s.getsockname()[0]
        s.close()
    except Exception:
        local_ip = '127.0.0.1'

    print("=" * 50)
    print("  BlueKey PC Companion Server")
    print("=" * 50)
    print(f"  Listening on port : {args.port}")
    print(f"  Your local IP     : {local_ip}")
    print()
    print("  In the BlueKey app:")
    print("    Connect → WiFi → enter the IP above")
    print()
    print("  Press Ctrl+C to stop")
    print("=" * 50)

    try:
        while True:
            conn, addr = server.accept()
            t = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
            t.start()
    except KeyboardInterrupt:
        print("\n[*] Server stopped.")
    finally:
        server.close()


if __name__ == '__main__':
    main()
