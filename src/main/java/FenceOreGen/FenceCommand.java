package FenceOreGen;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

@SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "ResultOfMethodCallIgnored"})
public class FenceCommand implements CommandExecutor {
    private final Map<Integer, Integer> upgradeCosts = new HashMap<>();
    private final FenceOreGen plugin;
    private final MessageManager messages;

    public FenceCommand(FenceOreGen plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "errors.player-only");
            return true;
        }

        if (checkPermission(player, "fenceoregen.use")) {
            return true;
        }

        if (args.length == 0) {
            sendHelpMenu(player);
            return true;
        }

        try {
            return handleSubCommands(player, args);
        } catch (Exception e) {
            handleCommandError(player, e);
            return true;
        }
    }

    private boolean handleSubCommands(Player player, String[] args) {
        return switch (args[0].toLowerCase()) {
            case "setlevel" -> handleSetLevel(player, args);
            case "upgrade" -> handleUpgrade(player);
            case "level" -> handleCheckLevel(player, args);
            case "reload" -> handleReload(player);
            default -> {
                sendHelpMenu(player);
                yield true;
            }
        };
    }

    private boolean handleReload(Player player) {
        if (!player.isOp() && !player.hasPermission("fenceoregen.admin")) {
            sendMessage(player, "errors.no-permission");
            return true;
        }

        try {
            // Reload config chính
            plugin.reloadConfig();

            // Reload messages
            plugin.getGeneratorManager().reload();

            // Reload generator manager
            plugin.reloadConfig();

            // Reload player data
            plugin.getPlayerData(); // Đảm bảo init nếu chưa có
            plugin.loadPlayerLevels();

            sendMessage(player, "success.reloaded");
        } catch (Exception e) {
            sendMessage(player, "errors.reload-failed");
            plugin.getLogger().log(Level.SEVERE, "Lỗi khi reload plugin", e);
        }
        return true;
    }


    private boolean handleSetLevel(Player player, String[] args) {
        if (checkPermission(player, "fenceoregen.admin")) {
            return true;
        }

        if (args.length != 2) {
            sendMessage(player, "commands.setlevel-usage");
            return true;
        }

        try {
            int level = Integer.parseInt(args[1]);
            String worldType = getWorldType(player.getWorld());
            int maxLevel = plugin.getGeneratorManager().getMaxLevel(worldType);

            if (level < 1 || level > maxLevel) {
                sendMessage(player, "errors.invalid-level",
                        "%min%", "1",
                        "%max%", String.valueOf(maxLevel));
                return true;
            }

            plugin.setPlayerLevel(player.getUniqueId(), level);
            plugin.savePlayerLevels();
            sendMessage(player, "success.level-set", "%level%", String.valueOf(level));
            return true;
        } catch (NumberFormatException e) {
            sendMessage(player, "errors.invalid-number");
            return true;
        }
    }

    private boolean handleUpgrade(Player player) {
        if (checkPermission(player, "fenceoregen.upgrade")) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        int currentLevel = plugin.getPlayerLevel(uuid);
        String worldType = getWorldType(player.getWorld());
        int maxLevel = plugin.getGeneratorManager().getMaxLevel(worldType);

        if (currentLevel >= maxLevel) {
            sendMessage(player, "errors.max-level", "%max%", String.valueOf(maxLevel));
            return true;
        }

        double cost = getUpgradeCost(currentLevel + 1);

        Economy econ = plugin.getEconomy();
        if (econ == null) {
            sendMessage(player, "errors.no-economy");
            return true;
        }

        if (!econ.has(player, cost)) {
            sendMessage(player, "errors.not-enough-money", "%cost%", String.format("%.2f", cost));
            return true;
        }

        econ.withdrawPlayer(player, cost);  // Trừ tiền
        plugin.setPlayerLevel(uuid, currentLevel + 1);
        plugin.savePlayerLevels();

        sendMessage(player, "success.upgraded",
                "%level%", String.valueOf(currentLevel + 1),
                "%cost%", String.format("%.2f", cost));
        return true;
    }

    private boolean handleCheckLevel(Player player, String[] args) {
        Player target = args.length > 1 ? getTargetPlayer(player, args[1]) : player;
        if (target == null) return true;

        int level = plugin.getPlayerLevel(target.getUniqueId());
        sendMessage(player, "commands.level-info", "%level%", String.valueOf(level));
        return true;
    }

    private Player getTargetPlayer(Player requester, String name) {
        if (checkPermission(requester, "fenceoregen.admin")) {
            return null;
        }

        Player target = plugin.getServer().getPlayer(name);
        if (target == null) {
            sendMessage(requester, "errors.player-not-found", "%player%", name);
        }
        return target;
    }

    private String getWorldType(World world) {
        if (world == null) return "overworld";
        return switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
    }

    private void sendHelpMenu(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Trợ giúp FenceOreGen" + ChatColor.GOLD + " ===");

        if (player.hasPermission("fenceoregen.use")) {
            sendMessage(player, "commands.main");
            sendMessage(player, "commands.level");
        }

        if (player.hasPermission("fenceoregen.upgrade")) {
            sendMessage(player, "commands.upgrade");
        }

        if (player.hasPermission("fenceoregen.admin")) {
            sendMessage(player, "commands.setlevel");
            sendMessage(player, "commands.reload");
        }
    }

    private boolean checkPermission(Player player, String permission) {
        if (player.hasPermission(permission)) return false;
        sendMessage(player, "errors.no-permission");
        return true;
    }

    private void sendMessage(CommandSender sender, String messageKey, String... replacements) {
        String message = messages.get(messageKey, "");
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i+1]);
        }
        sender.sendMessage(message);
    }

    public int getUpgradeCost(int level) {
        return upgradeCosts.getOrDefault(level, 0);
    }

    private void handleCommandError(Player player, Exception e) {
        sendMessage(player, "errors.command-error");
        if (plugin.isDebugMode()) {
            plugin.getLogger().log(Level.WARNING, "Command error", e);
        }
    }
}