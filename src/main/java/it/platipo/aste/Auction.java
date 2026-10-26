package it.platipo.aste;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record Auction(String id, UUID seller, String sellerName, long price, long created, ItemStack item) {}
