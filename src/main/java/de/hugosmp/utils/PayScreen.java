package de.hugosmp.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.OptionalLong;

/** Pay-Menü: Spieler aus der Liste wählen, Betrag eingeben (Kürzel erlaubt), bezahlen. */
public class PayScreen extends Screen {
    private static final int W = 260, ROW_H = 16, ROWS = 8;

    private TextFieldWidget search, amount;
    private ButtonWidget payBtn;
    private final List<String> all = new ArrayList<>();
    private final Map<String, PlayerListEntry> entries = new HashMap<>();
    private List<String> filtered = new ArrayList<>();
    private int scroll, listX, listY, top, ay;
    private String selected;
    private String searchText = "";
    private String amountText;
    private boolean armed;
    private Text status = Text.empty();

    public PayScreen() {
        super(Text.literal("Bezahlen"));
        Config c = Config.get();
        amountText = c.lastPayAmount == null ? "" : c.lastPayAmount;
        selected = (c.lastPayPlayer == null || c.lastPayPlayer.isBlank()) ? null : c.lastPayPlayer;
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        all.clear();
        entries.clear();
        if (mc.getNetworkHandler() != null) {
            String me = mc.player != null ? mc.player.getName().getString() : "";
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().getName();
                if (n != null && !n.equalsIgnoreCase(me)) {
                    all.add(n);
                    entries.put(n, e);
                }
            }
        }
        all.sort(String.CASE_INSENSITIVE_ORDER);
        if (selected != null && all.stream().noneMatch(n -> n.equalsIgnoreCase(selected))) selected = null;

        top = Math.max(6, this.height / 2 - 118);
        int x = (this.width - W) / 2;
        listX = x;
        listY = top + 38;
        ay = listY + ROWS * ROW_H + 18;

        search = new TextFieldWidget(this.textRenderer, x, top + 16, W, 18, Text.literal("Suche"));
        search.setPlaceholder(Text.literal("Spieler suchen ..."));
        search.setText(searchText);
        search.setChangedListener(s -> {
            searchText = s;
            refilter();
        });
        addDrawableChild(search);

        amount = new TextFieldWidget(this.textRenderer, x, ay, W, 18, Text.literal("Betrag"));
        amount.setPlaceholder(Text.literal("Betrag, z. B. 150k oder 1.5m"));
        amount.setMaxLength(32);
        amount.setText(amountText);
        amount.setChangedListener(s -> {
            amountText = s;
            disarm();
        });
        addDrawableChild(amount);

        String[] quick = {"1k", "10k", "100k", "1m"};
        int qw = (W - 12) / 4;
        for (int i = 0; i < quick.length; i++) {
            final String q = quick[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(q), b -> amount.setText(NumberParser.expand(q)))
                    .dimensions(x + i * (qw + 4), ay + 22, qw, 18).build());
        }

        payBtn = ButtonWidget.builder(Text.literal("Bezahlen"), b -> submit())
                .dimensions(x, ay + 46, W / 2 - 2, 20).build();
        addDrawableChild(payBtn);
        addDrawableChild(ButtonWidget.builder(Text.literal("Abbrechen"), b -> close())
                .dimensions(x + W / 2 + 2, ay + 46, W / 2 - 2, 20).build());

        refilter();
        setInitialFocus(search);
    }

    private void refilter() {
        String q = searchText.toLowerCase(Locale.ROOT).trim();
        filtered = new ArrayList<>();
        for (String n : all) {
            if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) filtered.add(n);
        }
        scroll = Math.min(scroll, Math.max(0, filtered.size() - ROWS));
    }

    private void disarm() {
        armed = false;
        if (payBtn != null) payBtn.setMessage(Text.literal("Bezahlen"));
    }

    private void submit() {
        if (selected == null) {
            status = Text.literal("Bitte einen Spieler auswählen.");
            return;
        }
        OptionalLong v = NumberParser.parseAmount(amountText);
        if (v.isEmpty() || v.getAsLong() <= 0) {
            status = Text.literal("Ungültiger Betrag.");
            return;
        }
        Config c = Config.get();
        if (c.payConfirm && v.getAsLong() >= c.payConfirmAbove && !armed) {
            armed = true;
            status = Text.literal("Hoher Betrag! Nochmal klicken zum Bestätigen.");
            payBtn.setMessage(Text.literal("Sicher? " + NumberParser.pretty(v.getAsLong())).formatted(Formatting.RED));
            return;
        }
        String cmd = c.payCommand.replace("{player}", selected).replace("{amount}", Long.toString(v.getAsLong()));
        c.lastPayPlayer = selected;
        c.lastPayAmount = amountText;
        c.save();
        close();
        Util.sendCommand(cmd);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x = listX;

        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("HugoSMP - Bezahlen").formatted(Formatting.GOLD),
                this.width / 2, top + 4, 0xFFFFFF);

        // Spielerliste
        ctx.fill(x, listY, x + W, listY + ROWS * ROW_H, 0x90000000);
        if (filtered.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Keine Spieler gefunden"),
                    x + W / 2, listY + ROWS * ROW_H / 2 - 4, 0x999999);
        }
        for (int i = 0; i < ROWS; i++) {
            int idx = scroll + i;
            if (idx >= filtered.size()) break;
            int y = listY + i * ROW_H;
            String name = filtered.get(idx);
            boolean hover = mouseX >= x && mouseX < x + W && mouseY >= y && mouseY < y + ROW_H;
            if (name.equals(selected)) ctx.fill(x, y, x + W, y + ROW_H, 0xAA3A7BD5);
            else if (hover) ctx.fill(x, y, x + W, y + ROW_H, 0x40FFFFFF);
            PlayerListEntry pe = entries.get(name);
            if (pe != null) PlayerSkinDrawer.draw(ctx, pe.getSkinTextures(), x + 3, y + 2, 12);
            ctx.drawText(this.textRenderer, name, x + 20, y + 4, 0xFFFFFF, false);
        }
        if (filtered.size() > ROWS) {
            int barH = Math.max(10, ROWS * ROW_H * ROWS / filtered.size());
            int maxScroll = filtered.size() - ROWS;
            int barY = listY + (ROWS * ROW_H - barH) * scroll / Math.max(1, maxScroll);
            ctx.fill(x + W - 3, barY, x + W - 1, barY + barH, 0xFFAAAAAA);
        }
        ctx.drawBorder(x, listY, W, ROWS * ROW_H, 0xFF555555);

        // Empfänger + Vorschau
        String rec = selected == null ? "Empfänger: -" : "Empfänger: " + selected;
        ctx.drawText(this.textRenderer, rec, x, ay - 11, selected == null ? 0xAAAAAA : 0x55FF55, false);
        OptionalLong v = NumberParser.parseAmount(amountText);
        if (v.isPresent()) {
            String pv = "= " + NumberParser.pretty(v.getAsLong());
            ctx.drawText(this.textRenderer, pv, x + W - this.textRenderer.getWidth(pv), ay - 11, 0xFFD700, false);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer, status, this.width / 2, ay + 72, 0xFF5555);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= listX && mx < listX + W && my >= listY && my < listY + ROWS * ROW_H) {
            int idx = scroll + (int) ((my - listY) / ROW_H);
            if (idx >= 0 && idx < filtered.size()) {
                selected = filtered.get(idx);
                status = Text.empty();
                disarm();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (filtered.size() > ROWS) {
            scroll = Math.max(0, Math.min(filtered.size() - ROWS, scroll - (int) Math.signum(vertical)));
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
