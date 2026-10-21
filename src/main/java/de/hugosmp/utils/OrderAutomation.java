package de.hugosmp.utils;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /order <item>: öffnet das Order-Menü des Servers, wählt die Order mit dem höchsten Preis pro Item,
 * legt ALLE passenden Items aus dem Inventar ein und bestätigt - ohne dass du etwas klicken musst.
 * Was die Mod im Menü sieht, schreibt sie nach config/hugosmputils-order-debug.txt (zum Nachjustieren).
 */
public final class OrderAutomation {
    private enum Phase { IDLE, WAIT_LIST, WAIT_DELIVER, FILLING, CONFIRM }

    private static final Pattern NUM = Pattern.compile(
            "(\\d[\\d,]*(?:\\.\\d+)?)(?:\\s*(mio|mrd|kk|k|m|b))?(?!\\w)", Pattern.CASE_INSENSITIVE);
    private static final String[] PRICE_WORDS = {"price", "preis", "each", "per ", "pro ", "/item", "$", "€"};
    private static final String[] CONFIRM_WORDS = {"confirm", "bestätig", "bestaetig", "accept", "deliver", "abgeben", "liefern"};
    private static final Path DEBUG_FILE = FabricLoader.getInstance().getConfigDir().resolve("hugosmputils-order-debug.txt");

    private static Phase phase = Phase.IDLE;
    private static String query = "";
    private static int timeout, wait, listSyncId = -1, moved, lastSlot, lastCount, stuck;
    private static boolean dumpedList, dumpedDeliver;
    private static StringBuilder log = new StringBuilder();

    private OrderAutomation() {}

    public static void start(String item) {
        query = item.trim();
        phase = Phase.WAIT_LIST;
        timeout = 20 * 12;
        wait = 0;
        moved = 0;
        stuck = 0;
        lastSlot = -1;
        lastCount = -1;
        listSyncId = -1;
        dumpedList = false;
        dumpedDeliver = false;
        log = new StringBuilder("Order-Automatik für: " + query + "\n\n");
    }

    private static void end(String result, boolean error) {
        log.append("\nERGEBNIS: ").append(result).append('\n');
        try {
            Files.writeString(DEBUG_FILE, log.toString());
        } catch (Exception ignored) {
        }
        if (error) Util.msg(result + " (Details: config/hugosmputils-order-debug.txt)");
        else Util.msg(result);
        phase = Phase.IDLE;
    }

    private static void dump(String label, HandledScreen<?> hs, ScreenHandler h, int size) {
        log.append("=== ").append(label).append(" | Titel: \"").append(hs.getTitle().getString())
                .append("\" | syncId ").append(h.syncId).append(" ===\n");
        for (int i = 0; i < size; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            log.append("Slot ").append(i).append(": ").append(Registries.ITEM.getId(s.getItem()).getPath())
                    .append(" | Name: ").append(s.getName().getString());
            LoreComponent lore = s.get(DataComponentTypes.LORE);
            if (lore != null) {
                StringBuilder l = new StringBuilder();
                for (Text t : lore.lines()) l.append(t.getString()).append(" / ");
                log.append(" | Lore: ").append(l);
            }
            log.append('\n');
        }
        log.append('\n');
    }

    public static void tick(MinecraftClient mc) {
        if (phase == Phase.IDLE) return;
        if (mc.player == null || mc.interactionManager == null) {
            phase = Phase.IDLE;
            return;
        }
        if (--timeout <= 0) {
            end("Order-Automatik: Zeitüberschreitung (Menü nicht erkannt?).", true);
            return;
        }
        if (!(mc.currentScreen instanceof HandledScreen<?> hs)) return;
        ScreenHandler h = hs.getScreenHandler();
        int size = h.slots.size() - 36;
        if (size <= 0) return;
        Config c = Config.get();

        switch (phase) {
            case WAIT_LIST -> {
                String need = c.orderScreenTitle == null ? "" : c.orderScreenTitle.trim().toLowerCase(Locale.ROOT);
                if (!need.isEmpty() && !hs.getTitle().getString().toLowerCase(Locale.ROOT).contains(need)) return;
                boolean any = false;
                for (int i = 0; i < size; i++) {
                    if (!h.getSlot(i).getStack().isEmpty()) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    wait = 0;
                    return;
                }
                if (++wait < 4) return; // Inhalt erst "setzen" lassen
                if (!dumpedList) {
                    dump("Order-Liste", hs, h, size);
                    dumpedList = true;
                }
                int best = -1;
                double bestPrice = -1;
                for (int i = 0; i < size; i++) {
                    double p = price(h.getSlot(i).getStack());
                    if (p > bestPrice) {
                        bestPrice = p;
                        best = i;
                    }
                }
                if (best < 0) {
                    end("Keine Order mit erkennbarem Preis gefunden.", true);
                    return;
                }
                log.append("Gewählt: Slot ").append(best).append(" (Preis ").append(bestPrice).append(")\n\n");
                mc.interactionManager.clickSlot(h.syncId, best, 0, SlotActionType.PICKUP, mc.player);
                Util.msg("Höchste Order gewählt: " + NumberParser.pretty(BigDecimal.valueOf(bestPrice)) + " pro Item.");
                listSyncId = h.syncId;
                wait = 0;
                if (!c.orderAutoFill) {
                    end("Order gewählt.", false);
                    return;
                }
                phase = Phase.WAIT_DELIVER;
            }
            case WAIT_DELIVER -> {
                if (h.syncId == listSyncId) return; // neues Menü noch nicht offen
                if (++wait < Math.max(1, c.orderDelayTicks)) return;
                if (!dumpedDeliver) {
                    dump("Abgabe-Menü", hs, h, size);
                    dumpedDeliver = true;
                }
                phase = Phase.FILLING;
                wait = 0;
            }
            case FILLING -> {
                if (++wait % 2 != 0) return; // höchstens ein Klick alle 2 Ticks
                int slot = -1;
                for (int i = size; i < h.slots.size(); i++) {
                    ItemStack s = h.getSlot(i).getStack();
                    if (!s.isEmpty() && matches(s)) {
                        slot = i;
                        break;
                    }
                }
                if (slot >= 0) {
                    int count = h.getSlot(slot).getStack().getCount();
                    if (slot == lastSlot && count == lastCount) stuck++;
                    else stuck = 0;
                    if (stuck < 3 && moved < 80) {
                        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.QUICK_MOVE, mc.player);
                        moved++;
                        lastSlot = slot;
                        lastCount = count;
                        return;
                    }
                }
                // nichts mehr da oder nichts mehr verschiebbar
                if (moved == 0) {
                    end("Du hast kein \"" + query + "\" im Inventar.", true);
                    return;
                }
                if (!c.orderAutoConfirm) {
                    end("Items eingelegt - bitte selbst bestätigen.", false);
                    return;
                }
                phase = Phase.CONFIRM;
                wait = 0;
            }
            case CONFIRM -> {
                if (++wait < 3) return;
                int slot = findConfirm(h, size);
                if (slot < 0) {
                    if (wait < 25) return;
                    end("Bestätigen-Button nicht gefunden - bitte selbst bestätigen.", true);
                    return;
                }
                mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                end("Order abgeschlossen (" + moved + " Stack-Klicks).", false);
            }
            default -> {
            }
        }
    }

    private static double price(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        double best = -1;
        for (Text line : lore.lines()) {
            String t = line.getString();
            String l = t.toLowerCase(Locale.ROOT);
            boolean relevant = false;
            for (String w : PRICE_WORDS) {
                if (l.contains(w)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant) continue;
            Matcher m = NUM.matcher(t);
            while (m.find()) {
                try {
                    BigDecimal v = new BigDecimal(m.group(1).replace(",", "")).multiply(NumberParser.multiplier(m.group(2)));
                    best = Math.max(best, v.doubleValue());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return best;
    }

    private static boolean matches(ItemStack stack) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        int idx = q.indexOf(':');
        if (idx >= 0) q = q.substring(idx + 1);
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.equals(q.replace(' ', '_')) || stack.getName().getString().toLowerCase(Locale.ROOT).equals(q);
    }

    private static int findConfirm(ScreenHandler h, int size) {
        for (int i = 0; i < size; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.isEmpty()) continue;
            String n = s.getName().getString().toLowerCase(Locale.ROOT);
            for (String w : CONFIRM_WORDS) {
                if (n.contains(w)) return i;
            }
        }
        for (int i = 0; i < size; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (s.isOf(Items.LIME_STAINED_GLASS_PANE) || s.isOf(Items.GREEN_STAINED_GLASS_PANE)
                    || s.isOf(Items.LIME_CONCRETE) || s.isOf(Items.GREEN_CONCRETE) || s.isOf(Items.EMERALD_BLOCK)) {
                return i;
            }
        }
        return -1;
    }
}
