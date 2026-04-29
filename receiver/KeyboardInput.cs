using System.ComponentModel;
using System.Runtime.InteropServices;

internal static class KeyboardInput
{
    private const uint InputKeyboard = 1;
    private const uint KeyEventUp = 0x0002;
    private const uint KeyEventScanCode = 0x0008;

    public static void SendTap(char key)
    {
        SendKey(key, true);
        Thread.Sleep(25);
        SendKey(key, false);
    }

    public static void SendKey(char key, bool down)
    {
        ushort scanCode = ScanCodeFor(key);
        Input input = new()
        {
            Type = InputKeyboard,
            Data = new InputUnion
            {
                Keyboard = new KeyboardInputData
                {
                    VirtualKey = 0,
                    ScanCode = scanCode,
                    Flags = down ? KeyEventScanCode : KeyEventScanCode | KeyEventUp,
                    Time = 0,
                    ExtraInfo = UIntPtr.Zero
                }
            }
        };

        uint sent = SendInput(1, new[] { input }, Marshal.SizeOf<Input>());
        if (sent != 1)
        {
            throw new Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    private static ushort ScanCodeFor(char key)
    {
        return char.ToUpperInvariant(key) switch
        {
            'D' => 0x20,
            'F' => 0x21,
            'J' => 0x24,
            'K' => 0x25,
            _ => throw new InvalidOperationException($"Unsupported key: {key}")
        };
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint inputCount, Input[] inputs, int size);

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint Type;
        public InputUnion Data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public MouseInputData Mouse;

        [FieldOffset(0)]
        public KeyboardInputData Keyboard;

        [FieldOffset(0)]
        public HardwareInputData Hardware;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInputData
    {
        public int X;
        public int Y;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInputData
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public UIntPtr ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HardwareInputData
    {
        public uint Message;
        public ushort LowParam;
        public ushort HighParam;
    }
}
