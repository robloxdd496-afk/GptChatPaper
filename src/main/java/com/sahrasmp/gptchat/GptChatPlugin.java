package com.sahrasmp.gptchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GptChatPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final HttpClient http = HttpClient.newHttpClient();

    private String apiKey;
    private String model;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        apiKey = getConfig().getString("openai-api-key", "");
        model = getConfig().getString("model", "gpt-5.6");

        getServer().getPluginManager().registerEvents(this, this);

        Command gptCommand = getCommand("gpt");

        if (gptCommand != null) {
            gptCommand.setExecutor(this);
        } else {
            getLogger().severe("Command 'gpt' is missing from plugin.yml!");
        }

        getLogger().info("GptChat enabled.");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {

        Player player = event.getPlayer();

        if (!active.contains(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);

        String message = event.getMessage();
        String playerName = player.getName();

        sendToAI(
                player,
                playerName + ": " + message
        );
    }

    private void sendToAI(CommandSender sender, String input) {

        if (apiKey == null || apiKey.isBlank()) {

            sender.sendMessage(
                    ChatColor.RED
                            + "GPT: ضع مفتاح OpenAI في plugins/GptChat/config.yml"
            );

            return;
        }

        CompletableFuture.runAsync(() -> {

            try {

                JsonObject body = new JsonObject();

                body.addProperty("model", model);

                body.addProperty(
                        "instructions",
                        "أنت مساعد ذكاء اصطناعي داخل سيرفر Minecraft. "
                                + "أجب بالعربية بوضوح وباختصار، "
                                + "وكن مفيداً للاعبين. "
                                + "لا تستخدم Markdown معقداً."
                );

                body.addProperty("input", input);
                body.addProperty("store", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.openai.com/v1/responses"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body.toString(),
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

                HttpResponse<String> response =
                        http.send(
                                request,
                                HttpResponse.BodyHandlers.ofString(
                                        StandardCharsets.UTF_8
                                )
                        );

                if (response.statusCode() / 100 != 2) {

                    getLogger().warning(
                            "OpenAI HTTP "
                                    + response.statusCode()
                                    + ": "
                                    + response.body()
                    );

                    sendMessageSafely(
                            sender,
                            ChatColor.RED
                                    + "GPT: حدث خطأ في الاتصال بالذكاء الاصطناعي."
                    );

                    return;
                }

                String answer = extract(response.body());

                if (answer.isBlank()) {
                    answer = "لم أستطع استخراج الرد.";
                }

                final String output =
                        ChatColor.AQUA
                                + "[GPT] "
                                + ChatColor.WHITE
                                + answer;

                getServer().getScheduler().runTask(
                        this,
                        () -> sender.sendMessage(output)
                );

            } catch (Exception exception) {

                getLogger().warning(
                        "GPT error: "
                                + exception.getMessage()
                );

                sendMessageSafely(
                        sender,
                        ChatColor.RED
                                + "GPT: تعذر الاتصال بالخدمة."
                );
            }

        });
    }

    private void sendMessageSafely(
            CommandSender sender,
            String message
    ) {

        getServer().getScheduler().runTask(
                this,
                () -> sender.sendMessage(message)
        );
    }

    private String extract(String json) {

        JsonObject root =
                JsonParser.parseString(json)
                        .getAsJsonObject();

        if (root.has("output_text")
                && !root.get("output_text").isJsonNull()) {

            return root
                    .get("output_text")
                    .getAsString();
        }

        StringBuilder result =
                new StringBuilder();

        JsonArray output =
                root.getAsJsonArray("output");

        if (output != null) {

            for (JsonElement outputElement : output) {

                if (!outputElement.isJsonObject()) {
                    continue;
                }

                JsonObject outputObject =
                        outputElement.getAsJsonObject();

                JsonArray content =
                        outputObject.getAsJsonArray("content");

                if (content == null) {
                    continue;
                }

                for (JsonElement contentElement : content) {

                    if (!contentElement.isJsonObject()) {
                        continue;
                    }

                    JsonObject contentObject =
                            contentElement.getAsJsonObject();

                    if (contentObject.has("text")
                            && !contentObject.get("text").isJsonNull()) {

                        result.append(
                                contentObject
                                        .get("text")
                                        .getAsString()
                        );
                    }
                }
            }
        }

        return result.toString().trim();
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("gpt.use")) {

            sender.sendMessage(
                    ChatColor.RED
                            + "ليس لديك صلاحية."
            );

            return true;
        }

        if (args.length == 0) {

            sender.sendMessage(
                    ChatColor.YELLOW
                            + "/gpt protocol - تشغيل"
            );

            sender.sendMessage(
                    ChatColor.YELLOW
                            + "/gpt protccol - تشغيل"
            );

            sender.sendMessage(
                    ChatColor.YELLOW
                            + "/gpt stop - إيقاف"
            );

            sender.sendMessage(
                    ChatColor.YELLOW
                            + "/gpt ask <سؤال>"
            );

            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {

            case "protocol":
            case "protccol":

                if (sender instanceof Player player) {

                    active.add(
                            player.getUniqueId()
                    );

                    sender.sendMessage(
                            ChatColor.GREEN
                                    + "GPT Chat تم تشغيله."
                    );

                } else {

                    sender.sendMessage(
                            ChatColor.RED
                                    + "هذا الأمر متاح للاعبين فقط."
                    );
                }

                break;

            case "stop":

                if (sender instanceof Player player) {

                    active.remove(
                            player.getUniqueId()
                    );

                    sender.sendMessage(
                            ChatColor.YELLOW
                                    + "GPT Chat تم إيقافه."
                    );

                } else {

                    sender.sendMessage(
                            ChatColor.RED
                                    + "هذا الأمر متاح للاعبين فقط."
                    );
                }

                break;

            case "ask":

                if (args.length < 2) {

                    sender.sendMessage(
                            ChatColor.RED
                                    + "استخدم: /gpt ask <سؤال>"
                    );

                    return true;
                }

                sendToAI(
                        sender,
                        String.join(
                                " ",
                                Arrays.copyOfRange(
                                        args,
                                        1,
                                        args.length
                                )
                        )
                );

                break;

            default:

                sender.sendMessage(
                        ChatColor.RED
                                + "أمر غير معروف."
                );

                break;
        }

        return true;
    }
}
