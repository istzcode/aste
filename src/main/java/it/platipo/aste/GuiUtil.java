package it.platipo.aste;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.*;

public final class GuiUtil {
    private GuiUtil() {}

    public static Map<String, String> ph(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static ItemStack build(ConfigurationSection s, Map<String, String> ph) {
        if (s == null) return new ItemStack(Material.STONE);
        Material m = Material.matchMaterial(s.getString("material", "STONE"));
        if (m == null || m.isAir() || !m.isItem()) m = Material.STONE;
        ItemStack it = new ItemStack(m, Math.max(1, Math.min(64, s.getInt("amount", 1))));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        String name = s.getString("name");
        if (name != null) meta.displayName(Text.item(Text.replace(name, ph)));
        List<String> lore = s.getStringList("lore");
        if (!lore.isEmpty()) {
            List<Component> l = new ArrayList<>();
            for (String line : lore) l.add(Text.item(Text.replace(line, ph)));
            meta.lore(l);
        }
        if (s.contains("custom-model-data")) {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) s.getInt("custom-model-data")));
            meta.setCustomModelDataComponent(cmd);
        }
        if (s.getBoolean("glow", false)) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    public static void fill(Inventory inv, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        ItemStack f = build(filler, Map.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f.clone());
    }

    public static void appendLore(ItemStack it, List<String> lines, Map<String, String> ph) {
        if (lines == null || lines.isEmpty()) return;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String line : lines) lore.add(Text.item(Text.replace(line, ph)));
        meta.lore(lore);
        it.setItemMeta(meta);
    }

    public static String itemName(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        StringBuilder sb = new StringBuilder();
        for (String w : it.getType().name().toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static int slot(ConfigurationSection s, String path, int size) {
        if (s == null) return -1;
        int v = s.getInt(path, -1);
        return (v >= 0 && v < size) ? v : -1;
    }
}
