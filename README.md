# Taiko Phone Drum

Android phone drum controller for Taiko no Tatsujin on Windows. The APK shows four large touch zones and sends keyboard input to a PC receiver:

- Left blue rim: `D`
- Left red drum: `F`
- Right red drum: `J`
- Right blue rim: `K`

The app does not use official Taiko assets. It uses an original festival/drum-style red/orange and blue layout.

## Build the APK

For a double-click menu, run:

```text
Taiko-Drum-Menu.bat
```

The menu includes Wi-Fi receiver, admin receiver, USB setup, APK build, and APK install options.

## Download Release

For normal users, download these from the latest GitHub release:

- `Taiko-Phone-Drum-Windows.zip`: Windows receiver/menu package. Extract it, then double-click `Taiko-Drum-Menu.bat`.
- `Taiko-Phone-Drum-debug.apk`: Android phone app.

The Windows ZIP includes the BAT file, receiver scripts, USB helper, APK install helper, and a copy of the APK, so the BAT works after extraction.

```powershell
.\scripts\build-android.ps1
```

Output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Install to a USB-connected phone:

```powershell
.\scripts\install-apk.ps1 -BuildFirst
```

## Run the PC Receiver

The easiest way is double-clicking `Taiko-Drum-Menu.bat` and choosing `Start Wi-Fi receiver`.

The receiver script uses the .NET receiver when the .NET SDK is installed. If the SDK is missing, it automatically uses the PowerShell receiver fallback.
The receiver shows a colored setup screen, PC IP addresses, and live hit badges for `KA` and `DON`.

```powershell
.\scripts\run-receiver.ps1 --port 27183
```

The receiver prints the PC IPv4 addresses. Type one of those addresses into the Android app for Wi-Fi mode.

To publish the receiver after installing the .NET 8 SDK:

```powershell
.\scripts\build-receiver.ps1
```

To require a pairing token, start the receiver with the same token shown in the Android app:

```powershell
.\scripts\run-receiver.ps1 --port 27183 --token 123456
```

If Taiko no Tatsujin is running as administrator and ignores input, run the receiver as administrator too.
The receiver sends keyboard scan codes for the physical `D/F/J/K` keys because games often ignore simple virtual-key input.

## USB Mode

USB mode uses Android Debug Bridge port forwarding, not true USB keyboard/HID mode.

1. Enable Developer Options and USB debugging on the phone.
2. Connect the phone by USB and accept the debugging prompt.
3. Start the PC receiver.
4. Run:

```powershell
.\scripts\usb-adb-reverse.ps1
```

5. In the Android app, tap `USB`. The PC IP field becomes `127.0.0.1`.

## Protocol

UDP packets are ASCII:

```text
TKD1|token|sequence|key|action|timestamp
```

- `key` is one of `D`, `F`, `J`, `K`.
- `action` is `DOWN`, `UP`, or `TAP`.
- The receiver accepts any token unless started with `--token`.
