package de.hugosmp.utils;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class HugoSmpUtils implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("hugosmputils");

    public static KeyBinding settingsKey, payKey, bypassKey;
    private static int rememberTimer = 150;

    private static void rememberOnline(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        boolean changed = false;
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().getName();
            if (n != null && Config.get().remember(n)) changed = true;
        }
        if (changed) Config.get().save();
    }

    @Override
    public void onInitializeClient() {
        Config.load();

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hugosmputils.settings",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.hugosmputils"));
        payKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hugosmputils.pay",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.hugosmputils"));
        bypassKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hugosmputils.bypass",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.hugosmputils"));
        ToolProtection.bypassKey = bypassKey;

        // Zahlen-Kürzel: 150k -> 150000 (Chat + Befehle)
        ClientSendMessageEvents.MODIFY_CHAT.register(msg -> Config.get().expandInChat ? NumberParser.expand(msg) : msg);
        ClientSendMessageEvents.MODIFY_COMMAND.register(cmd -> Config.get().expandInCommands ? NumberParser.expand(cmd) : cmd);

        ToolProtection.register();
        DurabilityHud.register();
        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Util.tick(mc);
            if (++rememberTimer >= 200) {
                rememberTimer = 0;
                rememberOnline(mc);
            }
            OrderAutomation.tick(mc);
            while (settingsKey.wasPressed()) mc.setScreen(new SettingsScreen(null));
            while (payKey.wasPressed()) {
                if (mc.player != null) mc.setScreen(new PayScreen());
            }
        });

        LOGGER.info("HugoSMP Utils geladen.");
    }

    private static String expandIfEnabled(String s) {
        return Config.get().expandInCommands ? NumberParser.expand(s) : s;
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // /hugomod -> Pay-Menü, /hugomod settings -> Einstellungen
            dispatcher.register(literal("hugomod")
                    .executes(ctx -> {
                        Util.openLater(new PayScreen());
                        return 1;
                    })
                    .then(literal("settings").executes(ctx -> {
                        Util.openLater(new SettingsScreen(null));
                        return 1;
                    })));

            // /pay: nur abgefangen, wenn in den Einstellungen aktiviert; sonst normal an den Server
            dispatcher.register(literal("pay").requires(s -> !Util.forwarding && Config.get().payOverride)
                    .executes(ctx -> {
                        Util.openLater(new PayScreen());
                        return 1;
                    })
                    .then(argument("args", StringArgumentType.greedyString()).executes(ctx -> {
                        Util.sendCommand("pay " + expandIfEnabled(StringArgumentType.getString(ctx, "args")));
                        return 1;
                    })));

            // /order <item>
            dispatcher.register(literal("order").requires(s -> !Util.forwarding)
                    .executes(ctx -> {
                        Util.sendCommand("order");
                        return 1;
                    })
                    .then(argument("item", StringArgumentType.greedyString())
                            .suggests((ctx, b) -> CommandSource.suggestMatching(
                                    Registries.ITEM.getIds().stream().map(Identifier::getPath), b))
                            .executes(ctx -> {
                                runOrder(StringArgumentType.getString(ctx, "item"));
                                return 1;
                            })));

            // /hugosmp -> Einstellungen
            dispatcher.register(literal("hugosmp").executes(ctx -> {
                Util.openLater(new SettingsScreen(null));
                return 1;
            }));

            // /calc 150k*3+2m
            dispatcher.register(literal("calc").then(argument("ausdruck", StringArgumentType.greedyString()).executes(ctx -> {
                String expr = StringArgumentType.getString(ctx, "ausdruck");
                try {
                    BigDecimal r = NumberParser.eval(expr);
                    ctx.getSource().sendFeedback(Text.literal(expr + " = " + NumberParser.pretty(r)
                            + " (" + r.stripTrailingZeros().toPlainString() + ")"));
                } catch (Exception e) {
                    ctx.getSource().sendError(Text.literal("Ungültiger Ausdruck: " + e.getMessage()));
                }
                return 1;
            })));
        });
    }

    private static void runOrder(String item) {
        Config c = Config.get();
        if (!c.orderEnabled) {
            Util.sendCommand("order " + item);
            return;
        }
        Util.sendCommand(c.orderCommand.replace("{item}", item));
        OrderAutomation.start(item);
    }
}
