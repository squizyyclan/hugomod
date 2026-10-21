package de.hugosmp.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Alle Einstellungen. Wird als config/hugosmputils.json gespeichert. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hugosmputils.json");
    private static Config instance = new Config();

    // Allgemein
    public boolean expandInChat = true;
    public boolean expandInCommands = true;
    public boolean durabilityHud = true;

    // Pay
    public boolean payOverride = false; // true = auch /pay öffnet das Menü
    public boolean payConfirm = true;
    public long payConfirmAbove = 1_000_000L;
    public String payCommand = "pay {player} {amount}";
    public String lastPayPlayer = "";
    public String lastPayAmount = "";

    // Order
    public boolean orderEnabled = true;
    public String orderCommand = "order {item}";
    public String orderScreenTitle = "order";
    public boolean orderAutoFill = true;
    public boolean orderAutoConfirm = true;
    public int orderDelayTicks = 6;

    // Werkzeug-Schutz
    public boolean toolProtection = true;
    public int toolMinDurability = 10;
    public boolean toolWarning = true;
    public boolean toolAutoHome = false;
    public String toolHomeName = "home";

    public static Config get() {
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            HugoSmpUtils.LOGGER.warn("Konnte Config nicht lesen, nutze Standardwerte", e);
        }
        instance.save();
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            HugoSmpUtils.LOGGER.warn("Konnte Config nicht speichern", e);
        }
    }
}
