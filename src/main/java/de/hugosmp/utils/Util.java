package de.hugosmp.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Util {
    /** true, solange wir einen Befehl an den Server weiterreichen (deaktiviert dann unsere Client-Befehle). */
    public static boolean forwarding = false;
    private static Screen pending;

    private Util() {}

    /** Screen im nächsten Tick öffnen (nötig, weil der Chat sich nach einem Befehl selbst schließt). */
    public static void openLater(Screen s) {
        pending = s;
    }

    public static void tick(MinecraftClient mc) {
        if (pending != null) {
            Screen s = pending;
            pending = null;
            mc.setScreen(s);
        }
    }

    public static void msg(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.sendMessage(Text.literal("[HugoSMP] ").formatted(Formatting.GOLD)
                .append(Text.literal(text).formatted(Formatting.WHITE)), false);
    }

    /** Sendet einen Befehl (ohne /) an den Server. */
    public static void sendCommand(String cmd) {
        ClientPlayNetworkHandler h = MinecraftClient.getInstance().getNetworkHandler();
        if (h == null || cmd == null) return;
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        forwarding = true;
        try {
            h.sendChatCommand(cmd);
        } finally {
            forwarding = false;
        }
    }
}
