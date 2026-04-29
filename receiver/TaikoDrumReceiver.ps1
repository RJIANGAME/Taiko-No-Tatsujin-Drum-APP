[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ReceiverArgs
)

$ErrorActionPreference = "Stop"

$port = 27183
$token = ""
$verbose = $false
$showHelp = $false

for ($i = 0; $i -lt $ReceiverArgs.Count; $i++) {
    $arg = $ReceiverArgs[$i]
    switch ($arg) {
        { $_ -in @("--help", "-h") } {
            $showHelp = $true
            break
        }
        { $_ -in @("--verbose", "-v") } {
            $verbose = $true
            break
        }
        { $_ -in @("--port", "-p") } {
            if ($i + 1 -ge $ReceiverArgs.Count) {
                throw "$arg needs a value."
            }
            $i++
            $port = [int]$ReceiverArgs[$i]
            break
        }
        { $_ -in @("--token", "-t") } {
            if ($i + 1 -ge $ReceiverArgs.Count) {
                throw "$arg needs a value."
            }
            $i++
            $token = $ReceiverArgs[$i].Trim()
            if ($token.Contains("|")) {
                throw "Token cannot contain '|'."
            }
            break
        }
        default {
            throw "Unknown argument: $arg"
        }
    }
}

if ($showHelp) {
    Write-Host "Usage:"
    Write-Host "  .\scripts\run-receiver.ps1 --port 27183 [--token 123456] [--verbose]"
    exit 0
}

if ($port -lt 1 -or $port -gt 65535) {
    throw "Port must be between 1 and 65535."
}

Add-Type -TypeDefinition @"
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Threading;

public static class TaikoKeyboardInput
{
    private const uint INPUT_KEYBOARD = 1;
    private const uint KEYEVENTF_KEYUP = 0x0002;
    private const uint KEYEVENTF_SCANCODE = 0x0008;

    public static void SendTap(char key)
    {
        SendKey(key, true);
        Thread.Sleep(25);
        SendKey(key, false);
    }

    public static void SendKey(char key, bool down)
    {
        ushort scanCode = ScanCodeFor(key);
        INPUT input = new INPUT();
        input.type = INPUT_KEYBOARD;
        input.U.ki.wVk = 0;
        input.U.ki.wScan = scanCode;
        input.U.ki.dwFlags = down ? KEYEVENTF_SCANCODE : KEYEVENTF_SCANCODE | KEYEVENTF_KEYUP;
        input.U.ki.time = 0;
        input.U.ki.dwExtraInfo = UIntPtr.Zero;

        INPUT[] inputs = new INPUT[] { input };
        uint sent = SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
        if (sent != 1)
        {
            throw new Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    private static ushort ScanCodeFor(char key)
    {
        switch (Char.ToUpperInvariant(key))
        {
            case 'D': return 0x20;
            case 'F': return 0x21;
            case 'J': return 0x24;
            case 'K': return 0x25;
            default: throw new InvalidOperationException("Unsupported key: " + key);
        }
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT
    {
        public uint type;
        public InputUnion U;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MOUSEINPUT mi;

        [FieldOffset(0)]
        public KEYBDINPUT ki;

        [FieldOffset(0)]
        public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public UIntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public UIntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }
}
"@

function Get-LocalIPv4Addresses {
    [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
        Where-Object {
            $_.OperationalStatus -eq [System.Net.NetworkInformation.OperationalStatus]::Up -and
            $_.NetworkInterfaceType -ne [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback
        } |
        ForEach-Object {
            $adapter = $_
            $adapter.GetIPProperties().UnicastAddresses |
                Where-Object { $_.Address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork } |
                ForEach-Object {
                    [pscustomobject]@{
                        Address = $_.Address.ToString()
                        Adapter = $adapter.Name
                    }
                }
        }
}

function ConvertFrom-TaikoPacket {
    param([string]$Text)

    $parts = $Text.Trim().Split("|")
    if ($parts.Count -ne 6 -or $parts[0] -ne "TKD1") {
        return $null
    }

    $sequence = 0
    if (-not [int]::TryParse($parts[2], [ref]$sequence)) {
        return $null
    }

    if ($parts[3].Length -ne 1) {
        return $null
    }

    $key = [char]::ToUpperInvariant($parts[3][0])
    if ($key -notin @([char]'D', [char]'F', [char]'J', [char]'K')) {
        return $null
    }

    $action = $parts[4].ToUpperInvariant()
    if ($action -notin @("DOWN", "UP", "TAP")) {
        return $null
    }

    [pscustomobject]@{
        Token = $parts[1]
        Sequence = $sequence
        Key = $key
        Action = $action
    }
}

function Write-Banner {
    param(
        [int]$Port,
        [string]$Token
    )

    Clear-Host
    $Host.UI.RawUI.WindowTitle = "Taiko Phone Drum Receiver - UDP $Port"
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host "                 TAIKO PHONE DRUM RECEIVER                  " -ForegroundColor White -BackgroundColor DarkRed
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host ""
    Write-StatusLine "Receiver" "PowerShell fallback, no .NET SDK needed" Cyan
    Write-StatusLine "Input" "keyboard scan codes for physical D/F/J/K" Green
    Write-StatusLine "Port" $Port Yellow
    if ($Token) {
        Write-StatusLine "Token" $Token Magenta
    } else {
        Write-StatusLine "Token" "off" DarkGray
    }
    Write-Host ""
    Write-Host "Wi-Fi setup" -ForegroundColor Yellow
    Write-Host "  1. Put phone and PC on the same Wi-Fi."
    Write-Host "  2. Type one PC IP below into the phone app."
    Write-Host "  3. Keep port as $Port, then tap Wi-Fi."
    Write-Host ""
    Write-Host "PC IP addresses" -ForegroundColor Yellow
    $addresses = @(Get-LocalIPv4Addresses)
    if ($addresses.Count -eq 0) {
        Write-Host "  No active IPv4 address found." -ForegroundColor Red
    } else {
        foreach ($item in $addresses) {
            Write-Host "  " -NoNewline
            Write-Host ("{0}:{1}" -f $item.Address, $Port) -ForegroundColor White -BackgroundColor DarkBlue -NoNewline
            Write-Host ("  {0}" -f $item.Adapter) -ForegroundColor DarkGray
        }
    }
    Write-Host ""
    Write-Host "Legend" -ForegroundColor Yellow
    Write-Host "  " -NoNewline
    Write-Host " KA D/K " -ForegroundColor White -BackgroundColor DarkBlue -NoNewline
    Write-Host "  blue rim     " -NoNewline
    Write-Host " DON F/J " -ForegroundColor White -BackgroundColor DarkRed -NoNewline
    Write-Host "  red drum"
    Write-Host ""
    Write-Host "Focus Taiko no Tatsujin, then tap the phone drum. Press Ctrl+C to stop."
    Write-Host "If the game ignores input, run this receiver as Administrator." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Hit stream:" -ForegroundColor Yellow
}

function Write-StatusLine {
    param(
        [string]$Label,
        [object]$Value,
        [ConsoleColor]$Color
    )

    Write-Host ("  {0,-9} " -f $Label) -ForegroundColor DarkGray -NoNewline
    Write-Host $Value -ForegroundColor $Color
}

$script:hitBadgeCount = 0
function Write-HitBadge {
    param(
        [char]$Key,
        [string]$Action
    )

    if ($Action -ne "DOWN" -and $Action -ne "TAP") {
        return
    }

    $isRim = $Key -eq [char]'D' -or $Key -eq [char]'K'
    $name = if ($isRim) { "KA" } else { "DON" }
    $bg = if ($isRim) { "DarkBlue" } else { "DarkRed" }
    Write-Host (" {0}:{1} " -f $name, $Key) -ForegroundColor White -BackgroundColor $bg -NoNewline
    $script:hitBadgeCount++
    if (($script:hitBadgeCount % 10) -eq 0) {
        Write-Host ""
    } else {
        Write-Host " " -NoNewline
    }
}

Write-Banner -Port $port -Token $token

$udp = [System.Net.Sockets.UdpClient]::new($port)
$remote = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
$pressedKeys = [System.Collections.Generic.HashSet[char]]::new()

try {
    while ($true) {
        $bytes = $udp.Receive([ref]$remote)
        $text = [System.Text.Encoding]::ASCII.GetString($bytes)
        $packet = ConvertFrom-TaikoPacket -Text $text

        if ($null -eq $packet) {
            if ($verbose) {
                Write-Host "Ignored bad packet from $remote`: $text"
            }
            continue
        }

        if ($token -and $packet.Token -ne $token) {
            if ($verbose) {
                Write-Host "Ignored token mismatch from $remote"
            }
            continue
        }

        if ($packet.Action -eq "DOWN") {
            [TaikoKeyboardInput]::SendKey($packet.Key, $true)
            [void]$pressedKeys.Add($packet.Key)
        } elseif ($packet.Action -eq "UP") {
            [TaikoKeyboardInput]::SendKey($packet.Key, $false)
            [void]$pressedKeys.Remove($packet.Key)
        } else {
            [TaikoKeyboardInput]::SendTap($packet.Key)
        }

        Write-HitBadge -Key $packet.Key -Action $packet.Action

        if ($verbose) {
            Write-Host ("{0} {1} seq={2} from {3}" -f $packet.Key, $packet.Action, $packet.Sequence, $remote)
        }
    }
} finally {
    foreach ($key in @($pressedKeys)) {
        try {
            [TaikoKeyboardInput]::SendKey($key, $false)
        } catch {
        }
    }

    $udp.Close()
}
