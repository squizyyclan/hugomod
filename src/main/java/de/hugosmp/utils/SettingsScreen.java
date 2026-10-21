package de.hugosmp.utils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Einstellungsmenü mit Tabs. Öffnen: Taste H oder /hugosmp. */
public class SettingsScreen extends Screen {
    private record Label(String text, int x, int y) {}

    private final Screen parent;
    private final List<Label> labels = new ArrayList<>();
    private int tab = 0;

    public SettingsScreen(Screen parent) {
        super(Text.literal("HugoSMP Utils - Einstellungen"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        labels.clear();
        Config c = Config.get();
        int cx = this.width / 2;
        int left = cx - 155, right = cx + 5, w = 150;

        String[] names = {"Allgemein", "Pay", "Order", "Werkzeug"};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(names[i]), btn -> {
                tab = t;
                clearAndInit();
            }).dimensions(cx - 156 + i * 78, 22, 76, 20).build();
            b.active = tab != i;
            addDrawableChild(b);
        }

        int y = 54;
        switch (tab) {
            case 0 -> {
                toggle(left, y, w, "Zahlen im Chat", () -> c.expandInChat, v -> c.expandInChat = v);
                toggle(right, y, w, "Zahlen in Befehlen", () -> c.expandInCommands, v -> c.expandInCommands = v);
                y += 24;
                toggle(left, y, w, "Haltbarkeits-Anzeige", () -> c.durabilityHud, v -> c.durabilityHud = v);
            }
            case 1 -> {
                toggle(left, y, w, "/pay öffnet auch Menü", () -> c.payOverride, v -> c.payOverride = v);
                toggle(right, y, w, "Hohe Beträge bestätigen", () -> c.payConfirm, v -> c.payConfirm = v);
                y += 28;
                field(left, y, w, "Bestätigen ab Betrag", NumberParser.pretty(c.payConfirmAbove).replace(".", ""), s -> {
                    OptionalLong v = NumberParser.parseAmount(s);
                    if (v.isPresent() && v.getAsLong() >= 0) c.payConfirmAbove = v.getAsLong();
                });
                field(right, y, w, "Pay-Befehl ({player} {amount})", c.payCommand, s -> c.payCommand = s);
            }
            case 2 -> {
                toggle(left, y, w, "/order-Automatik", () -> c.orderEnabled, v -> c.orderEnabled = v);
                toggle(right, y, w, "Items auto. einlegen", () -> c.orderAutoFill, v -> c.orderAutoFill = v);
                y += 24;
                toggle(left, y, w, "Auto. bestätigen", () -> c.orderAutoConfirm, v -> c.orderAutoConfirm = v);
                y += 28;
                field(left, y, w, "Order-Befehl ({item})", c.orderCommand, s -> c.orderCommand = s);
                field(right, y, w, "Menü-Titel enthält", c.orderScreenTitle, s -> c.orderScreenTitle = s);
                y += 40;
                field(left, y, w, "Wartezeit (Ticks)", String.valueOf(c.orderDelayTicks), s -> {
                    try {
                        c.orderDelayTicks = Math.max(1, Integer.parseInt(s.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
            case 3 -> {
                toggle(left, y, w, "Werkzeug-Schutz", () -> c.toolProtection, v -> c.toolProtection = v);
                toggle(right, y, w, "Warnung anzeigen", () -> c.toolWarning, v -> c.toolWarning = v);
                y += 24;
                toggle(left, y, w, "Auto-Teleport nach Home", () -> c.toolAutoHome, v -> c.toolAutoHome = v);
                y += 28;
                field(left, y, w, "Sperren ab Rest-Haltbarkeit", String.valueOf(c.toolMinDurability), s -> {
                    try {
                        c.toolMinDurability = Math.max(0, Integer.parseInt(s.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                });
                field(right, y, w, "Home-Name", c.toolHomeName, s -> c.toolHomeName = s);
            }
            default -> {
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(cx - 100, this.height - 28, 200, 20).build());
    }

    private void toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(toggleText(label, get.getAsBoolean()), btn -> {
            boolean now = !get.getAsBoolean();
            set.accept(now);
            btn.setMessage(toggleText(label, now));
            Config.get().save();
        }).dimensions(x, y, w, 20).build());
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": ").append(Text.literal(on ? "AN" : "AUS").formatted(on ? Formatting.GREEN : Formatting.RED));
    }

    private void field(int x, int y, int w, String label, String value, Consumer<String> onChange) {
        labels.add(new Label(label, x, y));
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y + 11, w, 18, Text.literal(label));
        f.setMaxLength(64);
        f.setText(value == null ? "" : value);
        f.setChangedListener(onChange);
        addDrawableChild(f);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFD700);
        for (Label l : labels) {
            ctx.drawText(this.textRenderer, l.text(), l.x(), l.y(), 0xAAAAAA, false);
        }
        if (tab == 0) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Tipp: 150k, 1.5m, 2kk, 3mio, 1b werden automatisch ausgeschrieben."),
                    this.width / 2, 108, 0x999999);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Zusatz: /calc 150k*3+2m rechnet mit Kürzeln."),
                    this.width / 2, 122, 0x999999);
        }
    }

    @Override
    public void removed() {
        Config.get().save();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
