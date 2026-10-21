package de.hugosmp.utils;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Zeigt die Rest-Haltbarkeit des Werkzeugs in der Haupthand unter dem Fadenkreuz. */
public final class DurabilityHud {
    private DurabilityHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Config.get().durabilityHud || mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
            ItemStack s = mc.player.getMainHandStack();
            if (s.isEmpty() || !s.isDamageable()) return;
            int max = s.getMaxDamage();
            int rem = max - s.getDamage();
            float pct = rem / (float) Math.max(1, max);
            int color = pct > 0.5f ? 0x55FF55 : pct > 0.2f ? 0xFFFF55 : 0xFF5555;
            String txt = rem + " / " + max + (ToolProtection.isBlocked(s) ? "  [GESPERRT]" : "");
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(txt),
                    ctx.getScaledWindowWidth() / 2, ctx.getScaledWindowHeight() / 2 + 14, color);
        });
    }
}
