package com.sahrasmp.gptchat;

import com.google.gson.*;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class GptChatPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final HttpClient http = HttpClient.newHttpClient();
    private String apiKey;
    private String model;

    @Override public void onEnable() {
        saveDefaultConfig();
        apiKey = getConfig().getString("openai-api-key", "");
        model = getConfig().getString("model", "gpt-5.6");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("gpt")).setExecutor(this);
        getLogger().info("GptChat enabled.");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!active.contains(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        String msg = e.getMessage();
        String player = e.getPlayer().getName();
        sendToAI(e.getPlayer(), player + ": " + msg);
    }

    private void sendToAI(CommandSender sender, String input) {
        if (apiKey.isBlank()) {
            sender.sendMessage(ChatColor.RED + "GPT: ضع مفتاح OpenAI في plugins/GptChat/config.yml");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.addProperty("instructions",
                    "أنت مساعد ذكاء اصطناعي داخل سيرفر Minecraft. أجب بالعربية بوضوح وباختصار، وكن مفيداً للاعبين. لا تستخدم Markdown معقداً.");
                body.addProperty("input", input);
                body.addProperty("store", false);

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/responses"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (res.statusCode() / 100 != 2) {
                    getLogger().warning("OpenAI HTTP " + res.statusCode() + ": " + res.body());
                    sender.sendMessage(ChatColor.RED + "GPT: حدث خطأ في الاتصال بالذكاء الاصطناعي.");
                    return;
                }
                String answer = extract(res.body());
                if (answer.isBlank()) answer = "لم أستطع استخراج الرد.";
                final String out = ChatColor.AQUA + "[GPT] " + ChatColor.WHITE + answer;
                getServer().getScheduler().runTask(this, () -> {
                    if (sender instanceof Player p) p.sendMessage(out);
                    else sender.sendMessage(out);
                });
            } catch (Exception ex) {
                getLogger().warning("GPT error: " + ex.getMessage());
                sender.sendMessage(ChatColor.RED + "GPT: تعذر الاتصال بالخدمة.");
            }
        });
    }

    private String extract(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("output_text") && !root.get("output_text").isJsonNull())
            return root.get("output_text").getAsString();
        StringBuilder s = new StringBuilder();
        JsonArray output = root.getAsJsonArray("output");
        if (output != null) for (JsonElement oe : output) {
            JsonObject o = oe.getAsJsonObject();
            JsonArray content = o.getAsJsonArray("content");
            if (content != null) for (JsonElement ce : content) {
                JsonObject c = ce.getAsJsonObject();
                if (c.has("text")) s.append(c.get("text").getAsString());
            }
        }
        return s.toString().trim();
    }

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("gpt.use")) {
            s.sendMessage(ChatColor.RED + "ليس لديك صلاحية.");
            return true;
        }
        if (a.length == 0) {
            s.sendMessage(ChatColor.YELLOW + "/gpt protocol - تشغيل");
            s.sendMessage(ChatColor.YELLOW + "/gpt protccol - تشغيل");
            s.sendMessage(ChatColor.YELLOW + "/gpt stop - إيقاف");
            s.sendMessage(ChatColor.YELLOW + "/gpt ask <سؤال>");
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "protocol", "protccol" -> {
                if (s instanceof Player p) active.add(p.getUniqueId());
                s.sendMessage(ChatColor.GREEN + "GPT Chat تم تشغيله.");
            }
            case "stop" -> {
                if (s instanceof Player p) active.remove(p.getUniqueId());
                s.sendMessage(ChatColor.YELLOW + "GPT Chat تم إيقافه.");
            }
            case "ask" -> {
                if (a.length < 2) { s.sendMessage(ChatColor.RED + "استخدم: /gpt ask <سؤال>"); return true; }
                sendToAI(s, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            default -> s.sendMessage(ChatColor.RED + "أمر غير معروف.");
        }
        return true;
    }
}
