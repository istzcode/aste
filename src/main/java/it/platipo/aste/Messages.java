package it.platipo.aste;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Messages {
    private final PlatipoAste plugin;
    private FileConfiguration cfg;

    public Messages(PlatipoAste plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        cfg = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public String raw(String key, String... kv) {
        String v = cfg.getString(key);
        if (v == null) return "&cMessaggio mancante: " + key;
        v = v.replace("{prefix}", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            v = v.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        return v;
    }

    public void send(CommandSender to, String key, String... kv) {
        String raw = raw(key, kv);
        if (raw.isBlank()) return;
        to.sendMessage(Text.parse(raw));
    }
}
