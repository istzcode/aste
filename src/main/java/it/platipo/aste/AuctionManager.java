package it.platipo.aste;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestione aste + persistenza su data.yml.
 * data.yml è la fonte di verità: ogni acquisto rilegge l'inserzione dal file di dati
 * (sezione in memoria sincronizzata e salvata su disco PRIMA di toccare l'inventario).
 */
public final class AuctionManager {
    public enum Result { OK, GONE, BUSY, NO_FUNDS, NO_SPACE, OWN, NOT_OWNER, LIMIT, NONE, ERROR }

    public record Outcome(Result result, Auction auction, long extra) {
        static Outcome of(Result r) { return new Outcome(r, null, 0); }
    }

    private final PlatipoAste plugin;
    private final File file;
    private YamlConfiguration data = new YamlConfiguration();
    private final Map<String, Auction> cache = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<String> processing = ConcurrentHashMap.newKeySet();

    public AuctionManager(PlatipoAste plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ---------------------------------------------------------------- persistenza

    public void load() {
        lock.lock();
        try {
            plugin.getDataFolder().mkdirs();
            data = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            cache.clear();
            ConfigurationSection sec = data.getConfigurationSection("auctions");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    Auction a = read(id);
                    if (a != null) cache.put(id, a);
                    else plugin.getLogger().warning("Inserzione corrotta ignorata: " + id);
                }
            }
            plugin.getLogger().info("Caricate " + cache.size() + " aste.");
        } finally {
            lock.unlock();
        }
    }

    private void saveSync() throws IOException {
        File tmp = new File(plugin.getDataFolder(), "data.yml.tmp");
        data.save(tmp);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuiet() {
        lock.lock();
        try {
            saveSync();
        } catch (IOException e) {
            plugin.getLogger().severe("Impossibile salvare data.yml: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private Auction read(String id) {
        ConfigurationSection s = data.getConfigurationSection("auctions." + id);
        if (s == null) return null;
        ItemStack item = s.getItemStack("item");
        String sel = s.getString("seller");
        if (item == null || sel == null) return null;
        try {
            return new Auction(id, UUID.fromString(sel), s.getString("seller-name", "?"),
                    s.getLong("price"), s.getLong("created"), item);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void write(Auction a) {
        String p = "auctions." + a.id();
        data.set(p + ".seller", a.seller().toString());
        data.set(p + ".seller-name", a.sellerName());
        data.set(p + ".price", a.price());
        data.set(p + ".created", a.created());
        data.set(p + ".item", a.item());
    }

    // ---------------------------------------------------------------- query

    public List<Auction> list() {
        lock.lock();
        try {
            List<Auction> l = new ArrayList<>(cache.values());
            l.sort(Comparator.comparingLong(Auction::created).reversed());
            return l;
        } finally {
            lock.unlock();
        }
    }

    public Auction get(String id) {
        lock.lock();
        try {
            return cache.get(id);
        } finally {
            lock.unlock();
        }
    }

    public long earnings(UUID u) {
        lock.lock();
        try {
            return data.getLong("earnings." + u);
        } finally {
            lock.unlock();
        }
    }

    // ---------------------------------------------------------------- operazioni

    public Result addListing(Player seller, ItemStack item, long price) {
        lock.lock();
        try {
            int max = plugin.getConfig().getInt("settings.max-listings-per-player", 10);
            if (max > 0) {
                long mine = cache.values().stream().filter(a -> a.seller().equals(seller.getUniqueId())).count();
                if (mine >= max) return Result.LIMIT;
            }
            String id = UUID.randomUUID().toString();
            Auction a = new Auction(id, seller.getUniqueId(), seller.getName(), price, System.currentTimeMillis(), item.clone());
            write(a);
            try {
                saveSync();
            } catch (IOException e) {
                data.set("auctions." + id, null);
                plugin.getLogger().severe("Salvataggio fallito: " + e.getMessage());
                return Result.ERROR;
            }
            Auction stored = read(id);
            if (stored == null) return Result.ERROR;
            cache.put(id, stored);
            return Result.OK;
        } finally {
            lock.unlock();
        }
    }

    public Outcome purchase(Player buyer, String id) {
        if (!processing.add(id)) return Outcome.of(Result.BUSY);
        lock.lock();
        try {
            // 1) l'inserzione deve esistere ANCORA nel data store
            Auction a = read(id);
            if (a == null) {
                cache.remove(id);
                return Outcome.of(Result.GONE);
            }
            if (a.seller().equals(buyer.getUniqueId())) return Outcome.of(Result.OWN);

            // 2) simulazione completa SENZA toccare l'inventario reale
            Wallet w = new Wallet(buyer, plugin.currency(), plugin.getConfig().getBoolean("settings.use-shulker-boxes", true));
            long bal = w.balance();
            if (bal < a.price()) return new Outcome(Result.NO_FUNDS, a, bal);
            long removed = w.withdraw(a.price());
            if (removed < 0) return new Outcome(Result.NO_FUNDS, a, bal);
            if (w.addItem(a.item()) != null) return new Outcome(Result.NO_SPACE, a, 0);
            long change = removed - a.price();
            if (change > 0 && w.deposit(change) > 0) return new Outcome(Result.NO_SPACE, a, 0);

            // 3) persistenza: rimuovi inserzione + accredita venditore, salva su disco
            String earnPath = "earnings." + a.seller();
            long prev = data.getLong(earnPath);
            data.set("auctions." + id, null);
            data.set(earnPath, prev + a.price());
            try {
                saveSync();
            } catch (IOException e) {
                write(a);
                data.set(earnPath, prev == 0 ? null : prev);
                plugin.getLogger().severe("Salvataggio fallito durante acquisto: " + e.getMessage());
                return Outcome.of(Result.ERROR);
            }
            cache.remove(id);

            // 4) solo ora tocca l'inventario reale (stesso tick, main thread)
            w.commit();
            return new Outcome(Result.OK, a, 0);
        } finally {
            lock.unlock();
            processing.remove(id);
        }
    }

    public Outcome cancel(Player p, String id) {
        if (!processing.add(id)) return Outcome.of(Result.BUSY);
        lock.lock();
        try {
            Auction a = read(id);
            if (a == null) {
                cache.remove(id);
                return Outcome.of(Result.GONE);
            }
            if (!a.seller().equals(p.getUniqueId())) return Outcome.of(Result.NOT_OWNER);
            Wallet w = new Wallet(p, plugin.currency(), false);
            if (w.addItem(a.item()) != null) return Outcome.of(Result.NO_SPACE);
            data.set("auctions." + id, null);
            try {
                saveSync();
            } catch (IOException e) {
                write(a);
                return Outcome.of(Result.ERROR);
            }
            cache.remove(id);
            w.commit();
            return new Outcome(Result.OK, a, 0);
        } finally {
            lock.unlock();
            processing.remove(id);
        }
    }

    /** Riscuote i guadagni pendenti (anche parzialmente se manca spazio). extra = valore depositato. */
    public Outcome claim(Player p) {
        lock.lock();
        try {
            String path = "earnings." + p.getUniqueId();
            long earned = data.getLong(path);
            if (earned <= 0) return Outcome.of(Result.NONE);
            Wallet w = new Wallet(p, plugin.currency(), plugin.getConfig().getBoolean("settings.use-shulker-boxes", true));
            long undeposited = w.deposit(earned);
            long deposited = earned - undeposited;
            if (deposited <= 0) return Outcome.of(Result.NO_SPACE);
            data.set(path, undeposited > 0 ? undeposited : null);
            try {
                saveSync();
            } catch (IOException e) {
                data.set(path, earned);
                return Outcome.of(Result.ERROR);
            }
            w.commit();
            return new Outcome(Result.OK, null, deposited);
        } finally {
            lock.unlock();
        }
    }
}
