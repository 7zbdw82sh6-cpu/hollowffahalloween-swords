package com.halloweenswords;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HalloweenSwords extends JavaPlugin implements TabExecutor {
    private SwordItems swordItems;
    private Cooldowns cooldowns;
    private Abilities abilities;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.swordItems = new SwordItems(this);
        this.cooldowns = new Cooldowns();
        this.abilities = new Abilities(this);

        getServer().getPluginManager().registerEvents(new SwordListener(this, swordItems, cooldowns, abilities), this);

        var cmd = getCommand("hsword");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("Halloween Swords enabled.");
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.shutdown();
    }

    public int cooldownSeconds(Ability a) {
        return getConfig().getInt("cooldowns." + a.id(), a.defaultCooldown());
    }

    // ------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("halloweenswords.admin")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <give <sword|all> [player] | list | reload | resetcd [player]>", NamedTextColor.GOLD));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                sender.sendMessage(Component.text("Halloween Swords:", NamedTextColor.GOLD));
                for (SwordType t : SwordType.values()) {
                    sender.sendMessage(Component.text(" - " + t.id() + " : " + t.displayName() + "  [" +
                            t.primary().displayName() + " / " + t.secondary().displayName() + "]", NamedTextColor.YELLOW));
                }
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage(Component.text("Config reloaded. (Re-give swords to refresh lore/models.)", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " give <sword|all> [player]", NamedTextColor.RED));
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else {
                    target = sender instanceof Player p ? p : null;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    for (SwordType t : SwordType.values()) give(target, t);
                    sender.sendMessage(Component.text("Gave all swords to " + target.getName() + ".", NamedTextColor.GREEN));
                } else {
                    SwordType t = SwordType.byId(args[1]);
                    if (t == null) {
                        sender.sendMessage(Component.text("Unknown sword. Use /" + label + " list", NamedTextColor.RED));
                        return true;
                    }
                    give(target, t);
                    sender.sendMessage(Component.text("Gave " + t.displayName() + " to " + target.getName() + ".", NamedTextColor.GREEN));
                }
            }
            case "resetcd" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                    return true;
                }
                cooldowns.clear(target.getUniqueId());
                sender.sendMessage(Component.text("Cooldowns reset for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    private void give(Player target, SwordType type) {
        ItemStack item = swordItems.create(type);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), extra);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("halloweenswords.admin")) return out;
        if (args.length == 1) {
            for (String s : List.of("give", "list", "reload", "resetcd")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.add("all");
            for (SwordType t : SwordType.values()) out.add(t.id());
            out.removeIf(s -> !s.startsWith(args[1].toLowerCase(Locale.ROOT)));
        } else if ((args.length == 3 && args[0].equalsIgnoreCase("give"))
                || (args.length == 2 && args[0].equalsIgnoreCase("resetcd"))) {
            String partial = args[args.length - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
            }
        }
        return out;
    }
}
