package FenceOreGen;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GeneratorManager {
    private final FenceOreGen plugin;
    private final Map<String, Map<Integer, Map<Material, Integer>>> generatorLevels = new HashMap<>();
    private final Map<String, Map<Integer, Map<Material, Double>>> worldLevelBlockChances = new HashMap<>();
    private final EnumSet<Material> replaceableBlocks = EnumSet.noneOf(Material.class);
    private final Random random = new Random();
    private final FileConfiguration config;
    private long spawnDelay = 0L;


    public GeneratorManager(FenceOreGen plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        worldLevelBlockChances.clear();
        replaceableBlocks.clear();
        loadConfig();
        this.spawnDelay = config.getInt("spawn-delay", 0);
        plugin.getLogger().info("[DEBUG] spawn-delay loaded: " + spawnDelay);
    }

    private void loadConfig() {
        if (config == null) {
            plugin.getLogger().severe("[FenceGen] Config is null! Cannot load configuration.");
            return;
        }
        worldLevelBlockChances.clear();
        replaceableBlocks.clear();

        // Load replaceable blocks
        List<String> replaceableList = config.getStringList("settings.replaceable-blocks");
        for (String materialName : replaceableList) {
            Material material = Material.matchMaterial(materialName.trim().toUpperCase());
            if (material != null) {
                replaceableBlocks.add(material);
            } else {
                plugin.getLogger().warning("[FenceGen] Invalid replaceable material: " + materialName);
            }
        }

        // Load generator levels
        ConfigurationSection generatorLevelsSection = config.getConfigurationSection("generator-levels");
        if (generatorLevelsSection == null) {
            plugin.getLogger().severe("[FenceGen] 'generator-levels' section is missing in config!");
            return;
        }

        for (String worldType : generatorLevelsSection.getKeys(false)) {
            ConfigurationSection worldSection = generatorLevelsSection.getConfigurationSection(worldType);
            if (worldSection == null) {
                plugin.getLogger().warning("[FenceGen] Skipping invalid world section: " + worldType);
                continue;
            }

            Map<Integer, Map<Material, Double>> levelMap = new HashMap<>();

            for (String levelKey : worldSection.getKeys(false)) {
                if (!levelKey.matches("\\d+")) {
                    plugin.getLogger().warning("[FenceGen] Skipping non-numeric level key '" + levelKey + "' in world " + worldType);
                    continue;
                }

                int level = Integer.parseInt(levelKey);
                ConfigurationSection levelSection = worldSection.getConfigurationSection(levelKey);
                if (levelSection == null) {
                    plugin.getLogger().warning("[FenceGen] Missing configuration for level " + level + " in world " + worldType);
                    continue;
                }

                plugin.getLogger().info("[DEBUG] Reading materials for " + worldType + " level " + level + ": " + levelSection.getKeys(false));

                Map<Material, Double> materials = new HashMap<>();
                for (Map.Entry<String, Object> entry : levelSection.getValues(false).entrySet()) {
                    String materialKey = entry.getKey();
                    Material material = Material.matchMaterial(materialKey.trim().toUpperCase());
                    if (material == null) {
                        plugin.getLogger().warning("[FenceGen] Invalid material '" + materialKey + "' in " + worldType + " level " + level);
                        continue;
                    }

                    try {
                        double chance = Double.parseDouble(entry.getValue().toString());
                        materials.put(material, chance);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("[FenceGen] Invalid chance value for '" + materialKey + "': " + entry.getValue());
                    }
                }

                if (!materials.isEmpty()) {
                    levelMap.put(level, materials);
                } else {
                    plugin.getLogger().warning("[FenceGen] No valid materials found in level " + level + " of world " + worldType);
                }
            }

            if (!levelMap.isEmpty()) {
                worldLevelBlockChances.put(worldType.toLowerCase(), levelMap);
            } else {
                plugin.getLogger().warning("[FenceGen] No valid levels found for world " + worldType);
            }
        }

        // Load spawn delay
        this.spawnDelay = config.getLong("spawn-delay", 0L);
    }


    public void addGeneratorLevel(String worldType, int level, Map<Material, Integer> materials) {
        worldType = worldType.toLowerCase();
        generatorLevels
                .computeIfAbsent(worldType, k -> new HashMap<>())
                .put(level, materials);
    }

    public int getMaxLevel(String worldType) {
        Map<Integer, Map<Material, Double>> levelMap = worldLevelBlockChances.get(worldType.toLowerCase());
        if (levelMap == null || levelMap.isEmpty()) return 1;
        return Collections.max(levelMap.keySet());
    }

    public Material getRandomBlock(String worldType, int level) {
        Map<Integer, Map<Material, Double>> levelMap = worldLevelBlockChances.get(worldType.toLowerCase());
        if (levelMap == null) {
            levelMap = worldLevelBlockChances.get("overworld"); // Fallback to overworld
            if (levelMap == null) return Material.STONE;
        }

        Map<Material, Double> chances = levelMap.getOrDefault(level, levelMap.get(1));
        if (chances == null || chances.isEmpty()) return Material.STONE;

        double total = chances.values().stream().mapToDouble(Double::doubleValue).sum();
        double randomValue = random.nextDouble() * total;
        double current = 0;

        for (Map.Entry<Material, Double> entry : chances.entrySet()) {
            current += entry.getValue();
            if (randomValue <= current) {
                return entry.getKey();
            }
        }

        return Material.STONE;
    }


    public Set<Material> getReplaceableBlocks() {
        return Collections.unmodifiableSet(replaceableBlocks);
    }

    public void spawnBlockAtDelayed(Location location, String worldType, int level) {
        if (spawnDelay <= 0) {
            spawnBlockAt(location, worldType, level);
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnBlockAt(location, worldType, level);
            }
        }.runTaskLater(plugin, spawnDelay);
    }

    public void spawnBlockAt(Location location, String worldType, int level) {
        if (location == null || location.getWorld() == null) return;

        Block block = location.getBlock();
        if (!replaceableBlocks.contains(block.getType())) return;

        block.setType(getRandomBlock(worldType, level));
    }
}
