package de.hugosmp.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.TridentItem;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

/** Sperrt Werkzeuge, kurz bevor sie kaputtgehen. */
public final class ToolProtection {
    public static KeyBinding bypassKey;
    private static long lastWarn = 0;
    private static long lastHome = 0;

    private ToolProtection() {}

    public static boolean isBlocked(ItemStack s) {
        Config c = Config.get();
        if (!c.toolProtection || s.isEmpty() || !s.isDamageable()) return false;
        if (s.contains(DataComponentTypes.EQUIPPABLE)) return false; // Rüstung / Elytra nicht sperren
        if (bypassKey != null && bypassKey.isPressed()) return false;
        return s.getMaxDamage() - s.getDamage() <= c.toolMinDurability;
    }

    public static void notifyBlocked(ItemStack s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config c = Config.get();
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        int rem = s.getMaxDamage() - s.getDamage();
        if (c.toolWarning && now - lastWarn > 1500) {
            lastWarn = now;
            mc.player.sendMessage(Text.literal("Achtung: " + s.getName().getString() + " ist fast kaputt (" + rem
                    + " Haltbarkeit übrig) - Benutzung gesperrt!").formatted(Formatting.RED), true);
        }
        if (c.toolAutoHome && now - lastHome > 15_000) {
            lastHome = now;
            String home = c.toolHomeName == null ? "" : c.toolHomeName.trim();
            Util.sendCommand(home.isEmpty() ? "home" : "home " + home);
            Util.msg("Werkzeug fast kaputt - teleportiere dich nach Hause.");
        }
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!world.isClient) return ActionResult.PASS;
            ItemStack s = player.getStackInHand(hand);
            if (isBlocked(s)) {
                notifyBlocked(s);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient) return ActionResult.PASS;
            ItemStack s = player.getStackInHand(hand);
            if (isBlocked(s)) {
                notifyBlocked(s);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient) return ActionResult.PASS;
            ItemStack s = player.getStackInHand(hand);
            Item item = s.getItem();
            boolean usesOnBlocks = item instanceof AxeItem || item instanceof ShovelItem
                    || item instanceof HoeItem || item instanceof FlintAndSteelItem;
            if (!usesOnBlocks || !isBlocked(s)) return ActionResult.PASS;
            BlockState state = world.getBlockState(hit.getBlockPos());
            // Kisten, Öfen etc. bleiben benutzbar
            if (state.createScreenHandlerFactory(world, hit.getBlockPos()) != null) return ActionResult.PASS;
            notifyBlocked(s);
            return ActionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient) return ActionResult.PASS;
            ItemStack s = player.getStackInHand(hand);
            Item item = s.getItem();
            boolean usable = item instanceof BowItem || item instanceof CrossbowItem
                    || item instanceof TridentItem || item instanceof FishingRodItem;
            if (usable && isBlocked(s)) {
                notifyBlocked(s);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Dauerhaftes Abbauen / Draufhalten der Maustaste unterbinden
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.currentScreen != null) return;
            ItemStack s = mc.player.getMainHandStack();
            if (!isBlocked(s)) return;
            KeyBinding atk = mc.options.attackKey;
            boolean attempt = atk.isPressed();
            while (atk.wasPressed()) attempt = true;
            if (attempt) {
                atk.setPressed(false);
                if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
                notifyBlocked(s);
            }
        });
    }
}
