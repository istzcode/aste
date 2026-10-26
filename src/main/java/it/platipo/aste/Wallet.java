package it.platipo.aste;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.*;

/**
 * Simulazione transazionale sull'inventario di un giocatore (inventario + Shulker Box).
 * Tutte le operazioni lavorano su COPIE; solo commit() scrive sull'inventario reale.
 * Se qualcosa fallisce, basta non chiamare commit(): nessun effetto collaterale.
 */
public final class Wallet {
    private final CurrencyManager cur;
    private final Player player;
    private final ItemStack[] contents;
    private final Map<Integer, ItemStack[]> boxes = new LinkedHashMap<>();
    private final Set<Integer> dirty = new HashSet<>();

    public Wallet(Player player, CurrencyManager cur, boolean useBoxes) {
        this.player = player;
        this.cur = cur;
        ItemStack[] src = player.getInventory().getStorageContents();
        contents = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            contents[i] = src[i] == null ? null : src[i].clone();
            if (useBoxes && contents[i] != null && Tag.SHULKER_BOXES.isTagged(contents[i].getType())) {
                ItemStack[] arr = readBox(contents[i]);
                if (arr != null) boxes.put(i, arr);
            }
        }
    }

    private static ItemStack[] readBox(ItemStack it) {
        if (!(it.getItemMeta() instanceof BlockStateMeta bsm)) return null;
        if (!(bsm.getBlockState() instanceof ShulkerBox sb)) return null;
        ItemStack[] src = sb.getInventory().getContents();
        ItemStack[] out = new ItemStack[27];
        for (int i = 0; i < Math.min(src.length, 27); i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }

    public long balance() {
        long total = 0;
        for (ItemStack s : contents) total += cur.valueOf(s);
        for (ItemStack[] arr : boxes.values()) for (ItemStack s : arr) total += cur.valueOf(s);
        return total;
    }

    /** Preleva almeno 'price' (partendo dai tagli piccoli). Ritorna il totale prelevato (>= price) o -1. */
    public long withdraw(long price) {
        long removed = 0;
        for (CurrencyManager.Tier t : cur.ascending()) {
            long need = price - removed;
            if (need <= 0) break;
            long want = (need + t.value() - 1) / t.value();
            long taken = takeFrom(contents, t, want, -1);
            for (Map.Entry<Integer, ItemStack[]> e : boxes.entrySet()) {
                if (taken >= want) break;
                taken += takeFrom(e.getValue(), t, want - taken, e.getKey());
            }
            removed += taken * t.value();
        }
        return removed >= price ? removed : -1;
    }

    private long takeFrom(ItemStack[] arr, CurrencyManager.Tier t, long want, int boxSlot) {
        long taken = 0;
        for (int i = 0; i < arr.length && taken < want; i++) {
            ItemStack s = arr[i];
            if (s == null || !t.matches(s)) continue;
            int n = (int) Math.min(s.getAmount(), want - taken);
            taken += n;
            if (n >= s.getAmount()) arr[i] = null;
            else s.setAmount(s.getAmount() - n);
        }
        if (taken > 0 && boxSlot >= 0) dirty.add(boxSlot);
        return taken;
    }

    /** Aggiunge un item SOLO all'inventario (mai nelle shulker). Ritorna l'avanzo (null se tutto inserito). */
    public ItemStack addItem(ItemStack item) {
        return addTo(contents, item.clone());
    }

    /**
     * Deposita 'amount' monete col minor numero di oggetti (inventario, poi shulker).
     * Ritorna il valore NON depositato (0 = tutto depositato).
     */
    public long deposit(long amount) {
        long remaining = amount;
        for (CurrencyManager.Tier t : cur.descending()) {
            long count = remaining / t.value();
            int max = Math.max(1, t.item().getMaxStackSize());
            while (count > 0) {
                int n = (int) Math.min(count, max);
                ItemStack st = t.item().clone();
                st.setAmount(n);
                int left = place(st);
                long placed = n - left;
                remaining -= placed * t.value();
                count -= placed;
                if (left > 0) break; // niente più spazio per questo taglio
            }
        }
        return remaining;
    }

    private int place(ItemStack st) {
        ItemStack left = addTo(contents, st);
        if (left != null) {
            for (Map.Entry<Integer, ItemStack[]> e : boxes.entrySet()) {
                int before = left.getAmount();
                left = addTo(e.getValue(), left);
                if (left == null || left.getAmount() != before) dirty.add(e.getKey());
                if (left == null) return 0;
            }
        }
        return left == null ? 0 : left.getAmount();
    }

    private static ItemStack addTo(ItemStack[] arr, ItemStack it) {
        int left = it.getAmount();
        int max = Math.max(1, it.getMaxStackSize());
        for (int i = 0; i < arr.length && left > 0; i++) {
            ItemStack s = arr[i];
            if (s != null && s.isSimilar(it) && s.getAmount() < max) {
                int add = Math.min(max - s.getAmount(), left);
                s.setAmount(s.getAmount() + add);
                left -= add;
            }
        }
        for (int i = 0; i < arr.length && left > 0; i++) {
            if (arr[i] == null) {
                int add = Math.min(max, left);
                ItemStack c = it.clone();
                c.setAmount(add);
                arr[i] = c;
                left -= add;
            }
        }
        if (left <= 0) return null;
        ItemStack r = it.clone();
        r.setAmount(left);
        return r;
    }

    /** Applica la simulazione all'inventario reale. */
    public void commit() {
        for (Map.Entry<Integer, ItemStack[]> e : boxes.entrySet()) {
            if (!dirty.contains(e.getKey())) continue;
            ItemStack it = contents[e.getKey()];
            BlockStateMeta bsm = (BlockStateMeta) it.getItemMeta();
            ShulkerBox sb = (ShulkerBox) bsm.getBlockState();
            sb.getInventory().setContents(e.getValue());
            bsm.setBlockState(sb);
            it.setItemMeta(bsm);
        }
        player.getInventory().setStorageContents(contents);
        player.updateInventory();
    }
}
