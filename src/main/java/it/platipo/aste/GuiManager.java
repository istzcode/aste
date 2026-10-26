package it.platipo.aste;

import it.platipo.aste.AuctionManager.Outcome;
import it.platipo.aste.AuctionManager.Result;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

public final class GuiManager implements Listener {
    private final PlatipoAste plugin;
    private final Map<UUID, SellSession> sessions = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public GuiManager(PlatipoAste plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ holders

    abstract static class AHHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }
    static final class MainHolder extends AHHolder {
        int page, pages;
        boolean mine;
        final Map<Integer, String> slots = new HashMap<>();
    }
    static final class SelectHolder extends AHHolder {}
    static final class PriceHolder extends AHHolder {
        final Map<Integer, String> actions = new HashMap<>();
    }
    static final class SellSession {
        ItemStack item;     // item selezionato (con la quantità da vendere)
        int slot = -1;      // slot nell'inventario del giocatore
        final Map<String, Long> counts = new HashMap<>();
    }

    // ------------------------------------------------------------ helpers

    private ConfigurationSection cfg(String path) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection(path);
        return s != null ? s : new YamlConfiguration();
    }
    private String fmt(long n) { return plugin.currency().fmt(n); }
    private void later(Runnable r) { Bukkit.getScheduler().runTask(plugin, r); }
    private void snd(Player p, String id) { plugin.sounds().play(p, id); }
    private void msg(Player p, String key, String... kv) { plugin.messages().send(p, key, kv); }
    private static int rows(ConfigurationSection g, int def) { return Math.max(1, Math.min(6, g.getInt("rows", def))); }

    private long total(SellSession s) {
        long t = 0;
        for (CurrencyManager.Tier tier : plugin.currency().ascending()) {
            t += s.counts.getOrDefault(tier.id(), 0L) * tier.value();
        }
        return t;
    }

    private void placeButton(Inventory inv, ConfigurationSection parent, String key, Map<String, String> ph) {
        ConfigurationSection sec = parent == null ? null : parent.getConfigurationSection(key);
        if (sec == null) return;
        int slot = GuiUtil.slot(sec, "slot", inv.getSize());
        if (slot >= 0) inv.setItem(slot, GuiUtil.build(sec, ph));
    }

    private int btnSlot(ConfigurationSection g, String key, int size) {
        return GuiUtil.slot(g, "buttons." + key + ".slot", size);
    }

    // ------------------------------------------------------------ MENU PRINCIPALE

    public void openMain(Player p, int page, boolean mine) {
        ConfigurationSection g = cfg("gui.main");
        int size = rows(g, 6) * 9;
        List<Integer> slots = new ArrayList<>();
        for (int s : g.getIntegerList("auction-slots")) if (s >= 0 && s < size) slots.add(s);
        if (slots.isEmpty()) for (int i = 0; i < size; i++) slots.add(i);

        List<Auction> list = new ArrayList<>();
        for (Auction a : plugin.auctions().list()) {
            if (!mine || a.seller().equals(p.getUniqueId())) list.add(a);
        }
        int perPage = slots.size();
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(1, Math.min(page, pages));

        MainHolder h = new MainHolder();
        h.page = pg; h.pages = pages; h.mine = mine;
        long pending = plugin.auctions().earnings(p.getUniqueId());
        Map<String, String> ph = GuiUtil.ph("page", "" + pg, "pages", "" + pages, "total", "" + list.size(),
                "pending", fmt(pending), "prev", "" + (pg - 1), "next", "" + (pg + 1),
                "state", plugin.getConfig().getString(mine ? "format.state-on" : "format.state-off", mine ? "ON" : "OFF"));
        Inventory inv = Bukkit.createInventory(h, size, Text.parse(Text.replace(g.getString("title", "Aste"), ph)));
        h.inv = inv;
        GuiUtil.fill(inv, g.getConfigurationSection("filler"));

        SimpleDateFormat df = new SimpleDateFormat(plugin.getConfig().getString("format.date", "dd/MM HH:mm"));
        int from = (pg - 1) * perPage;
        for (int i = from; i < Math.min(list.size(), from + perPage); i++) {
            Auction a = list.get(i);
            boolean own = a.seller().equals(p.getUniqueId());
            ItemStack it = a.item().clone();
            Map<String, String> aph = GuiUtil.ph("seller", a.sellerName(), "price", fmt(a.price()),
                    "price_breakdown", plugin.currency().breakdown(a.price()),
                    "date", df.format(new Date(a.created())), "amount", "" + a.item().getAmount());
            GuiUtil.appendLore(it, g.getStringList(own ? "own-lore" : "auction-lore"), aph);
            int slot = slots.get(i - from);
            inv.setItem(slot, it);
            h.slots.put(slot, a.id());
        }

        ConfigurationSection b = g.getConfigurationSection("buttons");
        boolean hide = g.getBoolean("hide-unavailable-nav", true);
        if (pg > 1 || !hide) placeButton(inv, b, "previous", ph);
        if (pg < pages || !hide) placeButton(inv, b, "next", ph);
        placeButton(inv, b, "sell", ph);
        placeButton(inv, b, "my-items", ph);
        placeButton(inv, b, "claim", ph);
        p.openInventory(inv);
    }

    private void handleMain(Player p, MainHolder h, InventoryClickEvent e) {
        ConfigurationSection g = cfg("gui.main");
        int size = h.inv.getSize();
        int s = e.getSlot();
        if (s == btnSlot(g, "previous", size)) {
            if (h.page > 1) { snd(p, "page"); later(() -> openMain(p, h.page - 1, h.mine)); }
            return;
        }
        if (s == btnSlot(g, "next", size)) {
            if (h.page < h.pages) { snd(p, "page"); later(() -> openMain(p, h.page + 1, h.mine)); }
            return;
        }
        if (s == btnSlot(g, "my-items", size)) {
            snd(p, "click");
            later(() -> openMain(p, 1, !h.mine));
            return;
        }
        if (s == btnSlot(g, "sell", size)) {
            if (!p.hasPermission("platipoaste.sell")) { msg(p, "sell-no-permission"); snd(p, "error"); return; }
            snd(p, "click");
            later(() -> openSelect(p));
            return;
        }
        if (s == btnSlot(g, "claim", size)) {
            claim(p, h);
            return;
        }
        String id = h.slots.get(s);
        if (id == null) return;
        Auction a = plugin.auctions().get(id);
        if (a != null && a.seller().equals(p.getUniqueId())) cancel(p, h, id);
        else buy(p, h, id);
    }

    private void buy(Player p, MainHolder h, String id) {
        Outcome o = plugin.auctions().purchase(p, id);
        Auction a = o.auction();
        switch (o.result()) {
            case OK -> {
                msg(p, "purchase-success", "item", GuiUtil.itemName(a.item()), "amount", "" + a.item().getAmount(),
                        "price", fmt(a.price()));
                snd(p, "success");
                Player seller = Bukkit.getPlayer(a.seller());
                if (seller != null) {
                    plugin.messages().send(seller, "seller-notify", "buyer", p.getName(),
                            "item", GuiUtil.itemName(a.item()), "price", fmt(a.price()));
                }
            }
            case NO_FUNDS -> { msg(p, "purchase-no-funds", "price", fmt(a.price()), "balance", fmt(o.extra())); snd(p, "error"); }
            case GONE -> { msg(p, "purchase-gone"); snd(p, "error"); }
            case BUSY -> { msg(p, "purchase-busy"); snd(p, "error"); }
            case NO_SPACE -> { msg(p, "purchase-no-space"); snd(p, "error"); }
            default -> { msg(p, "purchase-error"); snd(p, "error"); }
        }
        later(() -> openMain(p, h.page, h.mine));
    }

    private void cancel(Player p, MainHolder h, String id) {
        Outcome o = plugin.auctions().cancel(p, id);
        switch (o.result()) {
            case OK -> { msg(p, "cancel-success", "item", GuiUtil.itemName(o.auction().item())); snd(p, "success"); }
            case NO_SPACE -> { msg(p, "cancel-no-space"); snd(p, "error"); }
            case NOT_OWNER -> { msg(p, "cancel-not-owner"); snd(p, "error"); }
            case GONE -> { msg(p, "purchase-gone"); snd(p, "error"); }
            case BUSY -> { msg(p, "purchase-busy"); snd(p, "error"); }
            default -> { msg(p, "purchase-error"); snd(p, "error"); }
        }
        later(() -> openMain(p, h.page, h.mine));
    }

    private void claim(Player p, MainHolder h) {
        Outcome o = plugin.auctions().claim(p);
        switch (o.result()) {
            case OK -> {
                msg(p, "claim-success", "amount", fmt(o.extra()), "breakdown", plugin.currency().breakdown(o.extra()));
                long left = plugin.auctions().earnings(p.getUniqueId());
                if (left > 0) msg(p, "claim-partial", "remaining", fmt(left));
                snd(p, "success");
            }
            case NONE -> { msg(p, "claim-none"); snd(p, "error"); }
            case NO_SPACE -> { msg(p, "claim-no-space"); snd(p, "error"); }
            default -> { msg(p, "purchase-error"); snd(p, "error"); }
        }
        later(() -> openMain(p, h.page, h.mine));
    }

    // ------------------------------------------------------------ STEP 2: SELEZIONE ITEM

    public void openSelect(Player p) {
        sessions.computeIfAbsent(p.getUniqueId(), k -> new SellSession());
        ConfigurationSection g = cfg("gui.select");
        SelectHolder h = new SelectHolder();
        Inventory inv = Bukkit.createInventory(h, rows(g, 3) * 9, Text.parse(g.getString("title", "Seleziona")));
        h.inv = inv;
        renderSelect(h, p);
        p.openInventory(inv);
    }

    private void renderSelect(SelectHolder h, Player p) {
        SellSession s = sessions.get(p.getUniqueId());
        ConfigurationSection g = cfg("gui.select");
        Inventory inv = h.inv;
        inv.clear();
        GuiUtil.fill(inv, g.getConfigurationSection("filler"));
        ConfigurationSection pv = g.getConfigurationSection("preview");
        int ps = GuiUtil.slot(pv, "slot", inv.getSize());
        if (ps >= 0) {
            if (s == null || s.item == null) {
                inv.setItem(ps, GuiUtil.build(pv.getConfigurationSection("empty"), Map.of()));
            } else {
                ItemStack it = s.item.clone();
                GuiUtil.appendLore(it, pv.getStringList("selected-lore"),
                        GuiUtil.ph("amount", "" + s.item.getAmount(), "item", GuiUtil.itemName(s.item)));
                inv.setItem(ps, it);
            }
        }
        ConfigurationSection b = g.getConfigurationSection("buttons");
        placeButton(inv, b, "confirm", Map.of());
        placeButton(inv, b, "back", Map.of());
    }

    private String sellableError(ItemStack it) {
        if (it == null || it.getType().isAir()) return "sell-invalid-item";
        for (String m : plugin.getConfig().getStringList("settings.blacklist-materials")) {
            Material mat = Material.matchMaterial(m);
            if (mat != null && mat == it.getType()) return "sell-blacklisted";
        }
        if (!plugin.getConfig().getBoolean("settings.allow-selling-currency", false)
                && plugin.currency().isCurrency(it)) return "sell-currency-forbidden";
        return null;
    }

    private void handleSelect(Player p, SelectHolder h, InventoryClickEvent e) {
        ConfigurationSection g = cfg("gui.select");
        Inventory top = h.inv;
        if (e.getClickedInventory() == top) {
            int s = e.getSlot();
            if (s == btnSlot(g, "confirm", top.getSize())) {
                SellSession ss = sessions.get(p.getUniqueId());
                if (ss == null || ss.item == null) { msg(p, "sell-select-first"); snd(p, "error"); return; }
                snd(p, "click");
                later(() -> openPrice(p));
            } else if (s == btnSlot(g, "back", top.getSize())) {
                snd(p, "click");
                sessions.remove(p.getUniqueId());
                later(() -> openMain(p, 1, false));
            }
            return;
        }
        // click sull'inventario del giocatore: selezione item
        if (e.getClickedInventory() != p.getInventory()) return;
        int slot = e.getSlot();
        if (slot < 0 || slot > 35) return;
        ItemStack it = p.getInventory().getItem(slot);
        String err = sellableError(it);
        if (err != null) { msg(p, err); snd(p, "error"); return; }
        SellSession ss = sessions.computeIfAbsent(p.getUniqueId(), k -> new SellSession());
        ItemStack sel = it.clone();
        if (e.getClick().isRightClick()) sel.setAmount(1);
        ss.item = sel;
        ss.slot = slot;
        snd(p, "click");
        msg(p, "sell-selected", "item", GuiUtil.itemName(sel), "amount", "" + sel.getAmount());
        renderSelect(h, p);
    }

    // ------------------------------------------------------------ STEP 3: PREZZO

    public void openPrice(Player p) {
        SellSession s = sessions.get(p.getUniqueId());
        if (s == null || s.item == null) { openSelect(p); return; }
        ConfigurationSection g = cfg("gui.price");
        PriceHolder h = new PriceHolder();
        Inventory inv = Bukkit.createInventory(h, rows(g, 5) * 9, Text.parse(g.getString("title", "Prezzo")));
        h.inv = inv;
        renderPrice(h, p);
        p.openInventory(inv);
    }

    private void renderPrice(PriceHolder h, Player p) {
        SellSession s = sessions.get(p.getUniqueId());
        if (s == null) return;
        ConfigurationSection g = cfg("gui.price");
        Inventory inv = h.inv;
        h.actions.clear();
        inv.clear();
        GuiUtil.fill(inv, g.getConfigurationSection("filler"));

        long total = total(s);
        long step = Math.max(1, g.getLong("shift-step", 10));
        Map<String, String> base = GuiUtil.ph("price", fmt(total), "breakdown", plugin.currency().breakdown(total),
                "min", fmt(plugin.getConfig().getLong("settings.min-price", 1)),
                "max", fmt(plugin.getConfig().getLong("settings.max-price", 100000000)));

        ConfigurationSection pv = g.getConfigurationSection("preview");
        int ps = GuiUtil.slot(pv, "slot", inv.getSize());
        if (ps >= 0) {
            ItemStack it = s.item.clone();
            GuiUtil.appendLore(it, pv.getStringList("lore"), base);
            inv.setItem(ps, it);
        }

        for (CurrencyManager.Tier t : plugin.currency().ascending()) {
            ConfigurationSection ts = g.getConfigurationSection("tiers." + t.id());
            if (ts == null) continue;
            long count = s.counts.getOrDefault(t.id(), 0L);
            Map<String, String> tph = GuiUtil.ph("currency", t.name(), "value", fmt(t.value()), "count", fmt(count),
                    "subtotal", fmt(count * t.value()), "step", "1", "shift_step", "" + step);
            int add = GuiUtil.slot(ts, "add-slot", inv.getSize());
            int disp = GuiUtil.slot(ts, "display-slot", inv.getSize());
            int rem = GuiUtil.slot(ts, "remove-slot", inv.getSize());
            if (add >= 0) {
                inv.setItem(add, GuiUtil.build(g.getConfigurationSection("add-button"), tph));
                h.actions.put(add, "add:" + t.id());
            }
            if (rem >= 0) {
                inv.setItem(rem, GuiUtil.build(g.getConfigurationSection("remove-button"), tph));
                h.actions.put(rem, "rem:" + t.id());
            }
            if (disp >= 0) {
                ItemStack d = t.item().clone();
                d.setAmount((int) Math.max(1, Math.min(64, count)));
                GuiUtil.appendLore(d, g.getStringList("display-lore"), tph);
                inv.setItem(disp, d);
            }
        }

        ConfigurationSection b = g.getConfigurationSection("buttons");
        for (String key : new String[]{"confirm", "back", "reset", "cancel"}) {
            placeButton(inv, b, key, base);
            int sl = btnSlot(g, key, inv.getSize());
            if (sl >= 0) h.actions.put(sl, key);
        }
    }

    private void handlePrice(Player p, PriceHolder h, InventoryClickEvent e) {
        String act = h.actions.get(e.getSlot());
        if (act == null) return;
        SellSession s = sessions.get(p.getUniqueId());
        if (s == null || s.item == null) { later(() -> openSelect(p)); return; }

        if (act.startsWith("add:") || act.startsWith("rem:")) {
            String id = act.substring(4);
            long step = e.isShiftClick() ? Math.max(1, cfg("gui.price").getLong("shift-step", 10)) : 1;
            long max = plugin.getConfig().getLong("settings.max-per-tier", 999);
            long cur = s.counts.getOrDefault(id, 0L);
            long nv = act.startsWith("add:") ? cur + step : cur - step;
            if (nv > max) {
                nv = max;
                msg(p, "tier-max", "max", fmt(max));
                snd(p, "error");
            } else {
                snd(p, "click");
            }
            s.counts.put(id, Math.max(0, nv));
            renderPrice(h, p);
            return;
        }
        switch (act) {
            case "reset" -> { s.counts.clear(); snd(p, "click"); renderPrice(h, p); }
            case "back" -> { snd(p, "click"); later(() -> openSelect(p)); }
            case "cancel" -> { snd(p, "click"); sessions.remove(p.getUniqueId()); later(() -> openMain(p, 1, false)); }
            case "confirm" -> confirmSale(p, s);
            default -> {}
        }
    }

    // ------------------------------------------------------------ STEP 4: PUBBLICAZIONE

    private void confirmSale(Player p, SellSession s) {
        long price = total(s);
        long min = plugin.getConfig().getLong("settings.min-price", 1);
        long max = plugin.getConfig().getLong("settings.max-price", 100000000);
        if (price < min) { msg(p, "sell-price-low", "min", fmt(min)); snd(p, "error"); return; }
        if (price > max) { msg(p, "sell-price-high", "max", fmt(max)); snd(p, "error"); return; }
        if (!p.hasPermission("platipoaste.sell")) { msg(p, "sell-no-permission"); snd(p, "error"); return; }

        // ri-validazione dell'item nello slot (anti-exploit: l'inventario può essere cambiato)
        ItemStack cur = s.slot >= 0 && s.slot <= 35 ? p.getInventory().getItem(s.slot) : null;
        String err = sellableError(s.item);
        if (err != null) { msg(p, err); snd(p, "error"); return; }
        if (cur == null || !cur.isSimilar(s.item) || cur.getAmount() < s.item.getAmount()) {
            msg(p, "sell-changed");
            snd(p, "error");
            s.item = null;
            later(() -> openSelect(p));
            return;
        }

        // 1) preleva dall'inventario  2) pubblica  3) se fallisce ripristina
        ItemStack original = cur.clone();
        int rest = cur.getAmount() - s.item.getAmount();
        if (rest <= 0) {
            p.getInventory().setItem(s.slot, null);
        } else {
            ItemStack left = original.clone();
            left.setAmount(rest);
            p.getInventory().setItem(s.slot, left);
        }

        Result r = plugin.auctions().addListing(p, s.item, price);
        if (r != Result.OK) {
            p.getInventory().setItem(s.slot, original);
            if (r == Result.LIMIT) msg(p, "sell-limit", "max", "" + plugin.getConfig().getInt("settings.max-listings-per-player", 10));
            else msg(p, "sell-error");
            snd(p, "error");
            p.updateInventory();
            return;
        }
        p.updateInventory();
        msg(p, "sell-success", "item", GuiUtil.itemName(s.item), "amount", "" + s.item.getAmount(), "price", fmt(price));
        snd(p, "success");
        sessions.remove(p.getUniqueId());
        later(() -> openMain(p, 1, false));
    }

    // ------------------------------------------------------------ EVENTI

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof AHHolder holder)) return;
        e.setCancelled(true); // nessun item può mai essere spostato nelle GUI
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ClickType ct = e.getClick();
        if (ct == ClickType.DOUBLE_CLICK || ct == ClickType.NUMBER_KEY || ct == ClickType.DROP
                || ct == ClickType.CONTROL_DROP || ct == ClickType.SWAP_OFFHAND || ct == ClickType.MIDDLE) return;
        if (e.getClickedInventory() == null) return;

        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("settings.click-cooldown-ms", 150);
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < cd) return;
        lastClick.put(p.getUniqueId(), now);

        if (holder instanceof MainHolder mh) {
            if (e.getClickedInventory() == top) handleMain(p, mh, e);
        } else if (holder instanceof SelectHolder sh) {
            handleSelect(p, sh, e);
        } else if (holder instanceof PriceHolder ph) {
            if (e.getClickedInventory() == top) handlePrice(p, ph, e);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof AHHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sessions.remove(e.getPlayer().getUniqueId());
        lastClick.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            long pending = plugin.auctions().earnings(p.getUniqueId());
            if (pending > 0 && p.isOnline()) msg(p, "earnings-pending", "amount", fmt(pending));
        }, 40L);
    }
}
