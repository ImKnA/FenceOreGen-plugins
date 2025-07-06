package FenceOreGen;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MessageManager {
    private final JavaPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private File configFile;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        configFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!configFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                try (InputStream in = plugin.getResource("messages.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    } else {
                        plugin.saveResource("messages.yml", false);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create messages.yml file!");
                e.printStackTrace();
            }
        }

        loadMessages();
    }

    public void loadMessages() {
        messages.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        loadSection(config, "commands");
        loadSection(config, "errors");
        loadSection(config, "success");
        loadSection(config, "titles");
        loadSection(config, "messages");

        setDefaults();
    }

    private void loadSection(ConfigurationSection config, String path) {
        if (config.isConfigurationSection(path)) {
            Objects.requireNonNull(config.getConfigurationSection(path)).getKeys(true).forEach(key -> {
                String fullPath = path + "." + key;
                String message = config.getString(fullPath);
                if (message != null) {
                    messages.put(fullPath, ChatColor.translateAlternateColorCodes('&', message));
                }
            });
        }
    }

    public void reload() {
        loadMessages(); // Đọc lại messages.yml và cập nhật lại map messages
    }

    private void setDefaults() {
        // Đặt các message mặc định nếu chưa có trong file
        messages.putIfAbsent("errors.no-permission", "&cYou don't have permission to use this command!");
        messages.putIfAbsent("errors.player-only", "&cThis command is for players only!");
        messages.putIfAbsent("errors.player-not-found", "&cPlayer not found: %player%");
        messages.putIfAbsent("errors.invalid-level", "&cLevel must be between %min% and %max%");
        messages.putIfAbsent("errors.max-level", "&cYou have reached the maximum level (%max%)!");
        messages.putIfAbsent("errors.invalid-number", "&cInvalid number!");
        messages.putIfAbsent("errors.command-error", "&cAn error occurred while executing the command!");

        messages.putIfAbsent("success.level-set", "&aYour generator level has been set to %level%");
        messages.putIfAbsent("success.upgraded", "&aUpgraded to level %level% for %cost%$");

        messages.putIfAbsent("commands.main", "&6/fencegen &e- Show help menu");
        messages.putIfAbsent("commands.level", "&6/fencegen level [player] &e- Check generator level");
        messages.putIfAbsent("commands.upgrade", "&6/fencegen upgrade &e- Upgrade your generator");
        messages.putIfAbsent("commands.setlevel", "&6/fencegen setlevel <level> &e- Set generator level (Admin)");
        messages.putIfAbsent("commands.reload", "&6/fencegen reload &e- Reload configuration (Admin)");
        messages.putIfAbsent("commands.level-info", "&aGenerator level: %level%");

        messages.putIfAbsent("plugin-enabled", "&aFenceOreGen has been enabled!");
        messages.putIfAbsent("plugin-disabled", "&aFenceOreGen has been disabled!");
    }

    public String get(String path, String defaultValue) {
        return messages.getOrDefault(path, defaultValue);
    }

}