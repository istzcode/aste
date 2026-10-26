package it.platipo.aste;

import org.bukkit.plugin.java.JavaPlugin;

public final class PlatipoAste extends JavaPlugin {
    private Messages messages;
    private SoundManager sounds;
    private CurrencyManager currency;
    private AuctionManager auctions;
    private GuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        sounds = new SoundManager(this);
        currency = new CurrencyManager(this);
        auctions = new AuctionManager(this);
        auctions.load();
        gui = new GuiManager(this);
        getServer().getPluginManager().registerEvents(gui, this);

        AhCommand cmd = new AhCommand(this);
        for (String c : new String[]{"ah", "ahsetcurrency"}) {
            getCommand(c).setExecutor(cmd);
            getCommand(c).setTabCompleter(cmd);
        }
    }

    @Override
    public void onDisable() {
        if (auctions != null) auctions.saveQuiet();
    }

    public void reloadAll() {
        reloadConfig();
        messages.load();
        currency.load();
    }

    public Messages messages() { return messages; }
    public SoundManager sounds() { return sounds; }
    public CurrencyManager currency() { return currency; }
    public AuctionManager auctions() { return auctions; }
    public GuiManager gui() { return gui; }
}
