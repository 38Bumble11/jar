package com.aurasmp.auralink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Settings, stored in config/auralink.json (created the first time the server starts). */
public final class AuraConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Address of the bot's API, e.g. http://123.45.67.89:25581/api */
    public String apiUrl = "http://BOT-IP:PORT/api";

    /** Must be exactly the same as LINK_API_SECRET on the bot. */
    public String apiSecret = "change-me";

    /** Seconds a player has to wait between uses of /link and /redeem. */
    public int cooldownSeconds = 5;

    public boolean isConfigured() {
        return apiUrl != null && !apiUrl.contains("BOT-IP")
                && apiSecret != null && !apiSecret.isBlank() && !apiSecret.equals("change-me");
    }

    public static AuraConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("auralink.json");
        try {
            if (Files.exists(file)) {
                AuraConfig loaded = GSON.fromJson(Files.readString(file), AuraConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            }
            AuraConfig defaults = new AuraConfig();
            Files.writeString(file, GSON.toJson(defaults));
            return defaults;
        } catch (IOException | RuntimeException e) {
            AuraLink.LOGGER.error("Couldn't read {}: {}", file, e.getMessage());
            return new AuraConfig();
        }
    }
}
