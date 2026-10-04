package com.halloweenswords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class SwordItems {
    private final HalloweenSwords plugin;
    private final NamespacedKey swordKey;

    public SwordItems(HalloweenSwords plugin) {
        this.plugin = plugin;
        this.swordKey = new NamespacedKey(plugin, "sword_type");
    }

    private static Component line(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack create(SwordType type) {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("base-material", "NETHERITE_SWORD"));
        if (mat == null || !mat.isItem()) mat = Material.NETHERITE_SWORD;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        TextColor color = TextColor.color(type.color());
        meta.displayName(Component.text(type.displayName(), color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(line("Halloween Sword", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        addAbilityLore(lore, "Right-Click", type.primary(), color);
        lore.add(Component.empty());
        addAbilityLore(lore, "Sneak + Right-Click", type.secondary(), color);
        meta.lore(lore);

        meta.setUnbreakable(true);

        String model = plugin.getConfig().getString("models." + type.id(), type.defaultModel());
        NamespacedKey modelKey = NamespacedKey.fromString(model);
        if (modelKey != null) meta.setItemModel(modelKey);

        meta.getPersistentDataContainer().set(swordKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    private void addAbilityLore(List<Component> lore, String trigger, Ability a, TextColor color) {
        lore.add(line(trigger + ": ", NamedTextColor.GOLD)
                .append(Component.text(a.displayName(), color).decoration(TextDecoration.ITALIC, false))
                .append(line("  (" + plugin.cooldownSeconds(a) + "s)", NamedTextColor.DARK_GRAY)));
        lore.add(line("  " + a.description(), NamedTextColor.GRAY));
    }

    public SwordType identify(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(swordKey, PersistentDataType.STRING);
        return SwordType.byId(id);
    }
}
