package it.platipo.aste;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.*;

public final class CurrencyManager {
    public static final List<String> IDS = List.of("moneta", "pila", "sacco", "liquida");

    public record Tier(String id, String name, long value, ItemStack item) {
        /** Confronto preciso con isSimilar (ignora la quantità). */
        public boolean matches(ItemStack s) {
            return s != null && !s.getType().isAir() && item.isSimilar(s);
        }
    }

    private final PlatipoAste plugin;
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private List<Tier> ascending = List.of();
    private List<Tier> descending = List.of();
    private final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.ITALY);

    public CurrencyManager(PlatipoAste plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        tiers.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("currency");
        if (root != null) {
            for (String id : IDS) {
                ConfigurationSection sec = root.getConfigurationSection(id);
                if (sec == null) continue;
                long value = Math.max(1, sec.getLong("value", 1));
                String name = sec.getString("name", id);
                ItemStack item = sec.getItemStack("item");
                if (item == null) {
                    item = GuiUtil.build(sec.getConfigurationSection("default-item"), Map.of());
                    plugin.getLogger().warning("Valuta '" + id + "': item non impostato, uso il default. Usa /ahsetcurrency " + id);
                }
                item = item.clone();
                item.setAmount(1);
                tiers.put(id, new Tier(id, name, value, item));
            }
        }
        List<Tier> asc = new ArrayList<>(tiers.values());
        asc.sort(Comparator.comparingLong(Tier::value));
        ascending = List.copyOf(asc);
        List<Tier> desc = new ArrayList<>(asc);
        Collections.reverse(desc);
        descending = List.copyOf(desc);
        if (asc.isEmpty() || asc.get(0).value() != 1) {
            plugin.getLogger().warning("Nessun taglio di valore 1: il resto potrebbe non essere rappresentabile!");
        }
    }

    public List<Tier> ascending() { return ascending; }
    public List<Tier> descending() { return descending; }
    public Tier get(String id) { return tiers.get(id); }

    /** Valore totale (valore * quantità) di uno stack, 0 se non è valuta. */
    public long valueOf(ItemStack s) {
        if (s == null) return 0;
        for (Tier t : ascending) if (t.matches(s)) return t.value() * s.getAmount();
        return 0;
    }

    public boolean isCurrency(ItemStack s) {
        if (s == null) return false;
        for (Tier t : ascending) if (t.matches(s)) return true;
        return false;
    }

    /** Salva l'item esatto (NBT/CMD/nome/lore) in config.yml. */
    public void setCurrency(String id, ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        plugin.getConfig().set("currency." + id + ".item", copy);
        plugin.saveConfig();
        plugin.reloadConfig(); // rilegge da disco: il template coincide con la forma serializzata
        load();
    }

    public String fmt(long n) { return nf.format(n); }

    /** Scomposizione ottimizzata (meno oggetti possibile) in testo, formattata da config. */
    public String breakdown(long amount) {
        String entry = plugin.getConfig().getString("format.breakdown-entry", "&e{amount}x &f{name}");
        String sep = plugin.getConfig().getString("format.breakdown-separator", ", ");
        List<String> parts = new ArrayList<>();
        long rem = amount;
        for (Tier t : descending) {
            long c = rem / t.value();
            if (c > 0) {
                parts.add(entry.replace("{amount}", fmt(c)).replace("{name}", t.name()));
                rem -= c * t.value();
            }
        }
        if (parts.isEmpty()) return plugin.getConfig().getString("format.breakdown-empty", "-");
        return String.join(sep, parts);
    }
}
