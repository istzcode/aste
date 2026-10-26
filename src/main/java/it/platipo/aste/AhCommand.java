package it.platipo.aste;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

public final class AhCommand implements CommandExecutor, TabCompleter {
    private final PlatipoAste plugin;

    public AhCommand(PlatipoAste plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        Messages m = plugin.messages();

        if (name.equals("ahsetcurrency")) {
            if (!sender.hasPermission("platipoaste.admin")) { m.send(sender, "no-permission"); return true; }
            if (!(sender instanceof Player p)) { m.send(sender, "players-only"); return true; }
            if (args.length != 1) { m.send(sender, "setcurrency-usage"); return true; }
            String id = args[0].toLowerCase(Locale.ROOT);
            if (!CurrencyManager.IDS.contains(id) || plugin.currency().get(id) == null) {
                m.send(sender, "setcurrency-invalid");
                return true;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { m.send(sender, "setcurrency-no-item"); return true; }
            plugin.currency().setCurrency(id, hand);
            m.send(sender, "setcurrency-success", "currency", plugin.currency().get(id).name());
            return true;
        }

        // /ah
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("platipoaste.admin")) { m.send(sender, "no-permission"); return true; }
            plugin.reloadAll();
            m.send(sender, "reload-done");
            return true;
        }
        if (!(sender instanceof Player p)) { m.send(sender, "players-only"); return true; }
        if (!p.hasPermission("platipoaste.use")) { m.send(sender, "no-permission"); return true; }
        plugin.gui().openMain(p, 1, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        if (cmd.getName().equalsIgnoreCase("ahsetcurrency") && sender.hasPermission("platipoaste.admin")) {
            return CurrencyManager.IDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (cmd.getName().equalsIgnoreCase("ah") && sender.hasPermission("platipoaste.admin") && "reload".startsWith(prefix)) {
            return List.of("reload");
        }
        return List.of();
    }
}
