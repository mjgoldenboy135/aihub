# BlueKey

BlueKey turns an old Android phone into a Bluetooth HID (Human Interface Device) peripheral, letting it act as a keyboard, gamepad, media remote, or mouse that controls another phone or computer over Bluetooth Classic.

---

## What it does

BlueKey uses the Android `BluetoothHidDevice` API to register the device as a combo HID peripheral. Once paired with a host device (another phone, PC, tablet, or Smart TV), it sends standard HID reports for:

- **Keyboard** — Full QWERTY keyboard with Shift, Caps Lock, symbol mode, and 6-key rollover
- **Gamepad** — Dual joysticks, D-pad, face buttons (A/B/X/Y), shoulder buttons (L1/L2/R1/R2), Select/Start/Home
- **Media Remote** — Play/Pause, Next/Prev, Volume, Mute, Brightness, Stop, Back/Home/Forward
- **Mouse** — Full touchpad with left/right click, scroll mode, and adjustable sensitivity

---

## Requirements

- **Android 9.0+** (API level 28 minimum) on the peripheral device (the phone running BlueKey)
- Bluetooth Classic support on both the peripheral and the host device
- The host device must support Bluetooth HID (virtually all phones, PCs, and tablets do)

---

## How to build

1. Clone or copy this project directory
2. Open in **Android Studio Hedgehog (2023.1)** or newer
3. Let Gradle sync complete
4. Connect your old Android phone via USB with Developer Options enabled
5. Click **Run** (or press Shift+F10)

You can also build an APK via:
```
./gradlew assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## How to connect

1. Launch BlueKey on your peripheral phone
2. Grant the Bluetooth permissions when prompted
3. Tap **Connect** on the main menu
4. Tap **Make Discoverable** — the phone will be visible for 300 seconds
5. On your **host device**, open Bluetooth Settings and pair with **BlueKey HID**
6. Once paired, BlueKey will automatically connect and show "Connected" status
7. On subsequent launches, tap any bonded device in the list to reconnect

---

## 4 Modes

### Keyboard
Full QWERTY layout built programmatically. Features:
- Letters, numbers, symbols on two layout pages (tap `123` / `ABC` to switch)
- **Single tap Shift** = one-shot uppercase (auto-clears after one key)
- **Double tap Shift** = Caps Lock
- Backspace, Enter, Space, Tab
- All keys send HID keycodes on press and a release report on lift

### Gamepad
Landscape game controller layout:
- Left joystick (LX/LY axes) + D-pad (4 directional buttons)
- Right joystick (RX/RY axes)
- Face buttons: A (green), B (red), X (blue), Y (yellow)
- Shoulder buttons: L1, L2, R1, R2
- Center: Select, Home, Start
- Supports simultaneous button presses via bitmask

### Media Remote
Large tap buttons for media control:
- Transport: Previous, Play/Pause, Next, Stop
- Volume: Mute, Volume Down, Volume Up
- Display: Brightness Down, Brightness Up
- Navigation: Back (Alt+Left), Home (GUI key), Forward (Alt+Right)

### Mouse
Full-screen touchpad:
- **Slide** to move the cursor
- **Tap** the touchpad for left click
- **Left Click** / **Right Click** buttons at the bottom
- **Scroll Mode** toggle — vertical swipe scrolls instead of moving
- Adjustable sensitivity slider (0.5x to 3.0x, default 1.5x)

---

## Notes

- The `BluetoothHidDevice` API (`SUBCLASS1_COMBO`) is used for maximum compatibility
- Report IDs: 1 = Keyboard, 2 = Mouse, 3 = Consumer/Media, 4 = Gamepad
- All HID operations silently no-op if not connected, preventing crashes
- Screen stays on while the app is open (`keepScreenOn`)
