internal enum PacketAction
{
    Down,
    Up,
    Tap
}

internal readonly record struct TaikoPacket(
    string Token,
    int Sequence,
    char Key,
    PacketAction Action,
    long Timestamp)
{
    private static readonly HashSet<char> ValidKeys = new() { 'D', 'F', 'J', 'K' };

    public static bool TryParse(string text, out TaikoPacket packet)
    {
        packet = default;

        string[] parts = text.Split('|');
        if (parts.Length != 6 || !string.Equals(parts[0], "TKD1", StringComparison.Ordinal))
        {
            return false;
        }

        if (!int.TryParse(parts[2], out int sequence) || sequence < 0)
        {
            return false;
        }

        if (parts[3].Length != 1)
        {
            return false;
        }

        char key = char.ToUpperInvariant(parts[3][0]);
        if (!ValidKeys.Contains(key))
        {
            return false;
        }

        PacketAction action;
        if (string.Equals(parts[4], "DOWN", StringComparison.OrdinalIgnoreCase))
        {
            action = PacketAction.Down;
        }
        else if (string.Equals(parts[4], "UP", StringComparison.OrdinalIgnoreCase))
        {
            action = PacketAction.Up;
        }
        else if (string.Equals(parts[4], "TAP", StringComparison.OrdinalIgnoreCase))
        {
            action = PacketAction.Tap;
        }
        else
        {
            return false;
        }

        if (!long.TryParse(parts[5], out long timestamp))
        {
            return false;
        }

        packet = new TaikoPacket(parts[1], sequence, key, action, timestamp);
        return true;
    }
}
