using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;

ReceiverOptions options;
try
{
    options = ReceiverOptions.Parse(args);
}
catch (ArgumentException ex)
{
    Console.Error.WriteLine(ex.Message);
    ReceiverOptions.PrintUsage();
    return 2;
}

if (options.ShowHelp)
{
    ReceiverOptions.PrintUsage();
    return 0;
}

Console.Title = $"Taiko Phone Drum Receiver - UDP {options.Port}";
WriteBanner(options);

using CancellationTokenSource shutdown = new();
Console.CancelKeyPress += (_, eventArgs) =>
{
    eventArgs.Cancel = true;
    shutdown.Cancel();
};

HashSet<char> pressedKeys = new();
int hitBadgeCount = 0;

try
{
    using UdpClient udp = new(AddressFamily.InterNetwork);
    udp.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
    udp.Client.Bind(new IPEndPoint(IPAddress.Any, options.Port));

    while (!shutdown.IsCancellationRequested)
    {
        UdpReceiveResult receiveResult;
        try
        {
            receiveResult = await udp.ReceiveAsync(shutdown.Token);
        }
        catch (OperationCanceledException)
        {
            break;
        }

        string text = Encoding.ASCII.GetString(receiveResult.Buffer).Trim();
        if (!TaikoPacket.TryParse(text, out TaikoPacket packet))
        {
            if (options.Verbose)
            {
                Console.WriteLine($"Ignored bad packet from {receiveResult.RemoteEndPoint}: {text}");
            }
            continue;
        }

        if (options.Token.Length > 0 && !string.Equals(packet.Token, options.Token, StringComparison.Ordinal))
        {
            if (options.Verbose)
            {
                Console.WriteLine($"Ignored token mismatch from {receiveResult.RemoteEndPoint}");
            }
            continue;
        }

        try
        {
            if (packet.Action == PacketAction.Down)
            {
                KeyboardInput.SendKey(packet.Key, true);
                pressedKeys.Add(packet.Key);
            }
            else if (packet.Action == PacketAction.Up)
            {
                KeyboardInput.SendKey(packet.Key, false);
                pressedKeys.Remove(packet.Key);
            }
            else if (packet.Action == PacketAction.Tap)
            {
                KeyboardInput.SendTap(packet.Key);
            }

            if (options.Verbose)
            {
                Console.WriteLine($"{packet.Key} {packet.Action} seq={packet.Sequence} from {receiveResult.RemoteEndPoint}");
            }
            else
            {
                WriteHitBadge(packet);
            }
        }
        catch (Exception ex) when (ex is InvalidOperationException or System.ComponentModel.Win32Exception)
        {
            Console.Error.WriteLine($"Input failed: {ex.Message}");
        }
    }
}
finally
{
    foreach (char key in pressedKeys.ToArray())
    {
        try
        {
            KeyboardInput.SendKey(key, false);
        }
        catch
        {
            // Best effort cleanup on shutdown.
        }
    }
}

return 0;

static void PrintLocalAddresses(int port)
{
    foreach (NetworkInterface adapter in NetworkInterface.GetAllNetworkInterfaces())
    {
        if (adapter.OperationalStatus != OperationalStatus.Up ||
            adapter.NetworkInterfaceType == NetworkInterfaceType.Loopback)
        {
            continue;
        }

        foreach (UnicastIPAddressInformation address in adapter.GetIPProperties().UnicastAddresses)
        {
            if (address.Address.AddressFamily == AddressFamily.InterNetwork)
            {
                Console.Write("  ");
                WriteColored($"{address.Address}:{port}", ConsoleColor.White, ConsoleColor.DarkBlue);
                Console.ForegroundColor = ConsoleColor.DarkGray;
                Console.WriteLine($"  {adapter.Name}");
                Console.ResetColor();
            }
        }
    }
}

static void WriteBanner(ReceiverOptions options)
{
    Console.Clear();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("============================================================");
    WriteColored("                 TAIKO PHONE DRUM RECEIVER                  ", ConsoleColor.White, ConsoleColor.DarkRed);
    Console.WriteLine();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("============================================================");
    Console.ResetColor();
    Console.WriteLine();
    WriteStatusLine("Receiver", ".NET receiver", ConsoleColor.Cyan);
    WriteStatusLine("Input", "keyboard scan codes for physical D/F/J/K", ConsoleColor.Green);
    WriteStatusLine("Port", options.Port.ToString(), ConsoleColor.Yellow);
    WriteStatusLine("Token", options.Token.Length == 0 ? "off" : options.Token, options.Token.Length == 0 ? ConsoleColor.DarkGray : ConsoleColor.Magenta);
    Console.WriteLine();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("Wi-Fi setup");
    Console.ResetColor();
    Console.WriteLine("  1. Put phone and PC on the same Wi-Fi.");
    Console.WriteLine("  2. Type one PC IP below into the phone app.");
    Console.WriteLine($"  3. Keep port as {options.Port}, then tap Wi-Fi.");
    Console.WriteLine();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("PC IP addresses");
    Console.ResetColor();
    PrintLocalAddresses(options.Port);
    Console.WriteLine();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("Legend");
    Console.ResetColor();
    Console.Write("  ");
    WriteColored(" KA D/K ", ConsoleColor.White, ConsoleColor.DarkBlue);
    Console.Write("  blue rim     ");
    WriteColored(" DON F/J ", ConsoleColor.White, ConsoleColor.DarkRed);
    Console.WriteLine("  red drum");
    Console.WriteLine();
    Console.WriteLine("Focus Taiko no Tatsujin, then tap the phone drum. Press Ctrl+C to stop.");
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("If the game ignores input, run this receiver as Administrator.");
    Console.ResetColor();
    Console.WriteLine();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("Hit stream:");
    Console.ResetColor();
}

static void WriteStatusLine(string label, string value, ConsoleColor color)
{
    Console.ForegroundColor = ConsoleColor.DarkGray;
    Console.Write($"  {label,-9} ");
    Console.ForegroundColor = color;
    Console.WriteLine(value);
    Console.ResetColor();
}

void WriteHitBadge(TaikoPacket packet)
{
    if (packet.Action is not (PacketAction.Down or PacketAction.Tap))
    {
        return;
    }

    bool rim = packet.Key is 'D' or 'K';
    WriteColored(rim ? $" KA:{packet.Key} " : $" DON:{packet.Key} ", ConsoleColor.White, rim ? ConsoleColor.DarkBlue : ConsoleColor.DarkRed);
    hitBadgeCount++;
    Console.Write(hitBadgeCount % 10 == 0 ? Environment.NewLine : " ");
}

static void WriteColored(string value, ConsoleColor foreground, ConsoleColor background)
{
    ConsoleColor oldForeground = Console.ForegroundColor;
    ConsoleColor oldBackground = Console.BackgroundColor;
    Console.ForegroundColor = foreground;
    Console.BackgroundColor = background;
    Console.Write(value);
    Console.ForegroundColor = oldForeground;
    Console.BackgroundColor = oldBackground;
}

internal sealed record ReceiverOptions(int Port, string Token, bool Verbose, bool ShowHelp)
{
    public static ReceiverOptions Parse(string[] args)
    {
        int port = 27183;
        string token = "";
        bool verbose = false;
        bool showHelp = false;

        for (int i = 0; i < args.Length; i++)
        {
            string arg = args[i];
            switch (arg)
            {
                case "--help":
                case "-h":
                    showHelp = true;
                    break;
                case "--verbose":
                case "-v":
                    verbose = true;
                    break;
                case "--port":
                case "-p":
                    port = ParsePort(ReadValue(args, ref i, arg));
                    break;
                case "--token":
                case "-t":
                    token = ReadValue(args, ref i, arg).Trim();
                    if (token.Contains('|'))
                    {
                        throw new ArgumentException("Token cannot contain '|'.");
                    }
                    break;
                default:
                    throw new ArgumentException($"Unknown argument: {arg}");
            }
        }

        return new ReceiverOptions(port, token, verbose, showHelp);
    }

    public static void PrintUsage()
    {
        Console.WriteLine("Usage:");
        Console.WriteLine("  dotnet run --project receiver/TaikoDrumReceiver.csproj -- [--port 27183] [--token 123456] [--verbose]");
    }

    private static string ReadValue(string[] args, ref int index, string option)
    {
        if (index + 1 >= args.Length)
        {
            throw new ArgumentException($"{option} needs a value.");
        }
        index++;
        return args[index];
    }

    private static int ParsePort(string value)
    {
        if (!int.TryParse(value, out int port) || port < 1 || port > 65535)
        {
            throw new ArgumentException("Port must be between 1 and 65535.");
        }
        return port;
    }
}
