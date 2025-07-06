package FenceOreGen;

import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

@SuppressWarnings("CallToPrintStackTrace")
@Getter
public final class FenceOreGen extends JavaPlugin {

    private GeneratorManager generatorManager;
    private MessageManager messageManager;
    private FileConfiguration playerData;
    private File playerDataFile;
    private FileConfiguration config;
    private final Map<UUID, Integer> playerLevels = new HashMap<>();
    private final Map<String, String> messages = new HashMap<>();
    private final Map<Integer, Map<Material, Integer>> generatorLevels = new HashMap<>();
    private boolean enablePlugin;
    private boolean debugMode;
    private double upgradeCost;
    private double upgradeCostMultiplier;
    private boolean enableParticles;
    private boolean enableSounds;
    private String particleType;
    private String soundType;
    private float soundVolume;
    private float soundPitch;
    private boolean preventBreak;
    private int breakCooldown;
    private boolean enableWorldguard;
    private final Set<String> disabledWorlds = new HashSet<>();
    private final Set<Material> triggerBlocks = new HashSet<>();
    private final Set<Material> replaceableBlocks = new HashSet<>();
    private Economy economy;


    // Sửa phương thức onEnable()
    @Override
    public void onEnable() {
        saveDefaultConfig();
        initPlayerData();

        if (!setupEconomy()) {
            getLogger().severe("Vault không tìm thấy! Tắt plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        messageManager = new MessageManager(this);
        loadConfiguration(); // Load basic config

        generatorManager = new GeneratorManager(this, config); // Initialize generatorManager
        loadGeneratorLevels(); // Now load generator levels

        migrateOldData();
        loadPlayerLevels();

        registerListeners();
        registerCommands();

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        getLogger().info("FenceOreGen đã được kích hoạt thành công!");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        savePlayerLevels();
        getLogger().info("FenceOreGen đã tắt");
    }

    private void registerCommands() {
        if (getCommand("fencegen") != null) {
            Objects.requireNonNull(getCommand("fencegen")).setExecutor(new FenceCommand(this));
        }
    }


    private void initPlayerData() {
        this.playerDataFile = new File(getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                if (playerDataFile.createNewFile()) {
                    getLogger().info("Đã tạo file playerdata.yml mới");
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Không thể tạo file playerdata.yml", e);
            }
        }
        this.playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void loadConfiguration() {
        try {
            getLogger().info("Initializing configuration...");

            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin directory");
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                getLogger().info("Creating default config.yml...");
                try (InputStream in = getResource("config.yml")) {
                    if (in == null) {
                        throw new IOException("Default config.yml not found in JAR");
                    }
                    Files.copy(in, configFile.toPath());
                }
            }

            this.config = YamlConfiguration.loadConfiguration(configFile);
            getLogger().info("Successfully loaded config.yml");

            readConfigValues(); // Load basic config values
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load configuration", e);
        }
    }

    private void readConfigValues() {
        try {
            // Load basic settings
            this.enablePlugin = config.getBoolean("settings.enable-plugin", true);
            this.debugMode = config.getBoolean("settings.debug-mode", false);
            this.upgradeCost = Math.max(0, config.getDouble("settings.upgrade-cost", 1000.0));
            this.upgradeCostMultiplier = Math.max(1, config.getDouble("settings.upgrade-cost-multiplier", 1.5));
            this.enableParticles = config.getBoolean("settings.enable-particles", true);
            this.enableSounds = config.getBoolean("settings.enable-sounds", true);
            this.particleType = config.getString("settings.particle-type", "WATER_SPLASH");
            this.soundType = config.getString("settings.sound-type", "BLOCK_WATER_AMBIENT");
            this.soundVolume = (float) Math.max(0, config.getDouble("settings.sound-volume", 0.5));
            this.soundPitch = (float) Math.max(0, config.getDouble("settings.sound-pitch", 1.2));
            this.preventBreak = config.getBoolean("protection.prevent-break", true);
            this.breakCooldown = Math.max(0, config.getInt("protection.break-cooldown", 300));
            this.enableWorldguard = config.getBoolean("protection.enable-worldguard", false);

            disabledWorlds.clear();
            triggerBlocks.clear();
            replaceableBlocks.clear();

            loadStringList(disabledWorlds);
            loadMaterialList("settings.trigger-blocks", triggerBlocks);
            loadMaterialList("settings.replaceable-blocks", replaceableBlocks);

        } catch (Exception e) {
            getLogger().severe("[FenceGen] Error reading config values: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add this new method
    private void loadGeneratorLevels() {
        if (config.isConfigurationSection("generator-levels")) {
            ConfigurationSection worldsSection = config.getConfigurationSection("generator-levels");

            assert worldsSection != null;
            for (String world : worldsSection.getKeys(false)) {
                ConfigurationSection levelSection = worldsSection.getConfigurationSection(world);
                if (levelSection == null) continue;

                for (String levelKey : levelSection.getKeys(false)) {
                    int level;
                    try {
                        level = Integer.parseInt(levelKey);
                    } catch (NumberFormatException e) {
                        getLogger().warning("Invalid level number: " + levelKey);
                        continue;
                    }

                    ConfigurationSection materialSection = levelSection.getConfigurationSection(levelKey);
                    if (materialSection == null) continue;

                    Map<Material, Integer> materials = new HashMap<>();
                    for (String mat : materialSection.getKeys(false)) {
                        try {
                            Material material = Material.valueOf(mat.toUpperCase());
                            materials.put(material, materialSection.getInt(mat));
                            if (debugMode) {
                                getLogger().info("[DEBUG] Reading materials for " + world + " level " + level + ": " + materials.keySet());
                            }
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Invalid material: " + mat);
                        }
                    }

                    generatorManager.addGeneratorLevel(world, level, materials);
                }
            }
        }
    }


            private void migrateOldData() {
        File oldPlayerFile = new File(getDataFolder(), "players.yml");
        File oldLevelFile = new File(getDataFolder(), "levels.yml");

        // Migrate từ players.yml cũ
        if (oldPlayerFile.exists()) {
            try {
                FileConfiguration oldData = YamlConfiguration.loadConfiguration(oldPlayerFile);
                for (String key : oldData.getKeys(false)) {
                    if (!playerData.contains(key)) {
                        playerData.set(key, oldData.get(key));
                    }
                }
                savePlayerData();

                File backupFile = new File(getDataFolder(), "players_backup.yml");
                if (oldPlayerFile.renameTo(backupFile)) {
                    getLogger().info("Đã sao lưu file players.yml cũ");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Không thể migrate dữ liệu từ players.yml cũ", e);
            }
        }

        // Migrate từ levels.yml cũ
        if (oldLevelFile.exists()) {
            try {
                FileConfiguration oldLevels = YamlConfiguration.loadConfiguration(oldLevelFile);
                for (String uuidStr : oldLevels.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        int level = oldLevels.getInt(uuidStr);
                        playerLevels.put(uuid, level);
                        playerData.set("levels." + uuidStr, level);
                    } catch (IllegalArgumentException e) {
                        getLogger().warning("UUID không hợp lệ trong levels.yml: " + uuidStr);
                    }
                }
                savePlayerData();

                File backupFile = new File(getDataFolder(), "levels_backup.yml");
                if (oldLevelFile.renameTo(backupFile)) {
                    getLogger().info("Đã sao lưu file levels.yml cũ");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Không thể migrate dữ liệu từ levels.yml cũ", e);
            }
        }
    }



    public void savePlayerData() {
        try {
            // Lưu cả player levels vào playerdata.yml
            for (Map.Entry<UUID, Integer> entry : playerLevels.entrySet()) {
                playerData.set("levels." + entry.getKey().toString(), entry.getValue());
            }

            playerData.save(playerDataFile);
            if (debugMode) {
                getLogger().info("Đã lưu dữ liệu người chơi");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Không thể lưu playerdata.yml", e);
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;

        economy = rsp.getProvider();
        return true;
    }

    public void savePlayerLevels() {
        savePlayerData(); // Sử dụng chung phương thức save
    }

    private void loadMaterialList(String path, Set<Material> target) {
        for (String name : config.getStringList(path)) {
            try {
                target.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material in " + path + ": " + name);
            }
        }
    }


    public void loadPlayerLevels() {
        if (playerData.contains("levels")) {
            ConfigurationSection levelsSection = playerData.getConfigurationSection("levels");
            assert levelsSection != null;
            for (String uuidStr : levelsSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    int level = levelsSection.getInt(uuidStr);
                    playerLevels.put(uuid, level);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("UUID không hợp lệ trong playerdata: " + uuidStr);
                }
            }
        }
    }

    private void loadStringList(Set<String> target) {
        target.addAll(config.getStringList("protection.disabled-worlds"));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FencePlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new WaterInteractionListener(this), this);
    }

    public int getPlayerLevel(UUID uuid) {
        return playerLevels.getOrDefault(uuid, 1);
    }

    public void setPlayerLevel(UUID uuid, int level) {
        playerLevels.put(uuid, level);
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorlds.contains(worldName);
    }

}