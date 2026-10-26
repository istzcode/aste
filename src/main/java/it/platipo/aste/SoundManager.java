package it.platipo.aste;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class SoundManager {
    private final PlatipoAste plugin;

    public SoundManager(PlatipoAste plugin) {
        this.plugin = plugin;
    }

    /** id: click | page | success | error (o qualsiasi sezione sotto "sounds") */
    public void play(Player p, String id) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("sounds." + id);
        if (s == null || !s.getBoolean("enabled", true)) return;
        String name = s.getString("sound", "");
        if (name.isBlank()) return;
        try {
            p.playSound(Sound.sound(Key.key(name.toLowerCase()), Sound.Source.MASTER,
                    (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0)));
        } catch (Exception ignored) {
            // chiave non valida in config: ignora
        }
    }
}
