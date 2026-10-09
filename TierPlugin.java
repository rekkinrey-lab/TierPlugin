package com.example.tier;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TierPlugin extends JavaPlugin implements TabExecutor {

    // Lowest to highest
    private static final List<String> TIERS = List.of(
            "LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "LT2", "HT2", "LT1", "HT1");

    private static final String GROUP_PREFIX = "tier_";

    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            getLogger().severe("LuckPerms not found! Disabling TierPlugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        luckPerms = provider.getProvider();

        var cmd = getCommand("tier");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        // Make sure every tier group exists
        for (String tier : TIERS) {
            luckPerms.getGroupManager().createAndLoadGroup(groupName(tier));
        }
        getLogger().info("TierPlugin enabled with tiers: " + String.join(", ", TIERS));
    }

    private static String groupName(String tier) {
        return GROUP_PREFIX + tier.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tierplugin.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> <tier|none>");
            sender.sendMessage(ChatColor.GRAY + "Tiers: " + String.join(", ", TIERS));
            return true;
        }

        String ign = args[0];
        String input = args[1].toUpperCase(Locale.ROOT);
        boolean remove = input.equals("NONE") || input.equals("REMOVE");

        if (!remove && !TIERS.contains(input)) {
            sender.sendMessage(ChatColor.RED + "Invalid tier. Valid tiers: " + String.join(", ", TIERS));
            return true;
        }

        resolveUuid(ign).thenCompose(uuid -> {
            if (uuid == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + ign + "' was not found.");
                return CompletableFuture.completedFuture(null);
            }
            return luckPerms.getUserManager().modifyUser(uuid, user -> applyTier(user, remove ? null : input))
                    .thenAccept(v -> sender.sendMessage(remove
                            ? ChatColor.GREEN + "Removed tier from " + ign + "."
                            : ChatColor.GREEN + "Set " + ign + "'s tier to " + ChatColor.GOLD + input + ChatColor.GREEN + "."));
        }).exceptionally(ex -> {
            sender.sendMessage(ChatColor.RED + "Something went wrong, check the console.");
            getLogger().warning("Failed to set tier: " + ex);
            return null;
        });
        return true;
    }

    private CompletableFuture<UUID> resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        // LuckPerms' own lookup covers offline players it has seen before
        return luckPerms.getUserManager().lookupUniqueId(name);
    }

    private void applyTier(User user, String tier) {
        // Remove any existing tier group so a player only ever has one
        for (String t : TIERS) {
            user.data().remove(InheritanceNode.builder(groupName(t)).build());
        }
        if (tier != null) {
            Group group = luckPerms.getGroupManager().getGroup(groupName(tier));
            if (group == null) {
                group = luckPerms.getGroupManager().createAndLoadGroup(groupName(tier)).join();
            }
            user.data().add(InheritanceNode.builder(group).build());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("tierplugin.use")) return out;
        if (args.length == 1) {
            for (OfflinePlayer p : Bukkit.getOnlinePlayers()) {
                if (p.getName() != null && p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            for (String t : TIERS) {
                if (t.toLowerCase().startsWith(args[1].toLowerCase())) out.add(t);
            }
            if ("none".startsWith(args[1].toLowerCase())) out.add("none");
        }
        return out;
    }
}
