package com.aurasmp.auralink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * AuraLink - server-side mod with two commands:
 *
 *   /link   -> asks the Discord bot for a code; the player then enters it in Discord.
 *   /redeem -> asks the bot for the player's invite-reward diamonds and gives them.
 *
 * All the real logic (codes, balances) lives in the Discord bot. This mod just
 * relays the player's request and shows the bot's reply.
 */
public class AuraLink implements ModInitializer {

    public static final String MOD_ID = "auralink";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Gson GSON = new Gson();

    private static AuraConfig config;

    /** When each player last used a command (for the cooldown). */
    private static final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        config = AuraConfig.load();
        if (!config.isConfigured()) {
            LOGGER.warn("AuraLink isn't set up yet - edit config/auralink.json "
                    + "(apiUrl + apiSecret) and restart the server.");
        }

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("link")
                    .executes(context -> handle(context.getSource(), "/link", false)));
            dispatcher.register(Commands.literal("redeem")
                    .executes(context -> handle(context.getSource(), "/redeem", true)));
        });
    }

    /**
     * Runs /link or /redeem.
     *
     * @param path       which bot endpoint to call
     * @param giveItems  true for /redeem (the reply may contain diamonds to hand out)
     */
    private static int handle(CommandSourceStack source, String path, boolean giveItems) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        if (!config.isConfigured()) {
            source.sendFailure(Component.literal("AuraLink isn't set up yet. Tell a staff member!"));
            return 0;
        }

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = lastUse.get(uuid);
        if (last != null && now - last < config.cooldownSeconds * 1000L) {
            source.sendFailure(Component.literal("Slow down! Try again in a few seconds."));
            return 0;
        }
        lastUse.put(uuid, now);

        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid.toString());
        body.addProperty("name", player.getName().getString());

        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.literal("Contacting the Aura SMP bot...")
                .withStyle(ChatFormatting.GRAY), false);

        ApiClient.post(config.apiUrl, config.apiSecret, path, GSON.toJson(body))
                .whenComplete((response, error) ->
                        // The HTTP reply arrives on another thread - hop back onto the server thread.
                        server.execute(() -> finish(server, uuid, response, error, giveItems)));
        return 1;
    }

    /** Handles the bot's reply (runs on the server thread). */
    private static void finish(MinecraftServer server, UUID uuid, String response, Throwable error, boolean giveItems) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);

        boolean ok = false;
        String message = "Couldn't reach the Aura SMP bot. Try again later!";
        int diamonds = 0;

        if (error != null) {
            LOGGER.warn("Bot request failed: {}", String.valueOf(error));
        } else {
            try {
                JsonObject json = GSON.fromJson(response, JsonObject.class);
                if (json != null) {
                    ok = json.has("ok") && json.get("ok").getAsBoolean();
                    if (json.has("message")) {
                        message = json.get("message").getAsString();
                    }
                    if (json.has("diamonds")) {
                        diamonds = json.get("diamonds").getAsInt();
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Bot sent a reply I couldn't read: {}", response);
            }
        }

        if (ok && giveItems && diamonds > 0) {
            if (player == null) {
                // They left while we were waiting - put the diamonds back on their balance.
                refund(uuid, diamonds);
                return;
            }
            giveDiamonds(player, diamonds);
        }

        if (player != null) {
            player.sendSystemMessage(Component.literal(message)
                    .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    /** Puts diamonds into the inventory; anything that doesn't fit is dropped at their feet. */
    private static void giveDiamonds(ServerPlayer player, int total) {
        int remaining = total;
        while (remaining > 0) {
            int amount = Math.min(remaining, 64);
            ItemStack stack = new ItemStack(Items.DIAMOND, amount);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= amount;
        }
    }

    /** Tells the bot to give diamonds back (used when the player disconnected mid-redeem). */
    private static void refund(UUID uuid, int diamonds) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid.toString());
        body.addProperty("diamonds", diamonds);

        ApiClient.post(config.apiUrl, config.apiSecret, "/refund", GSON.toJson(body))
                .whenComplete((response, error) -> {
                    if (error != null) {
                        LOGGER.error("Couldn't refund {} diamonds to {}: {}", diamonds, uuid, String.valueOf(error));
                    }
                });
    }
}
