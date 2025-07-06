package FenceOreGen;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;


public class WaterInteractionListener implements Listener {

    private final FenceOreGen plugin;
    private final GeneratorManager generatorManager;
    private final int maxPlayerDistance;
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public WaterInteractionListener(FenceOreGen plugin) {
        this.plugin = plugin;
        this.generatorManager = plugin.getGeneratorManager();
        this.maxPlayerDistance = plugin.getConfig().getInt("max-player-distance", 5); // dùng thực sự
    }

    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();

        if (from.getType() != Material.WATER) return;
        if (to.getType() != Material.AIR) return;

        // Nước chảy ngang
        for (BlockFace face : HORIZONTAL_FACES) {
            Block neighbor = to.getRelative(face);
            if (neighbor.getType().name().contains("FENCE")) {
                event.setCancelled(true);
                trySpawn(to);
                return;
            }
        }

        // Nước chảy từ trên xuống
        Block below = to.getRelative(BlockFace.DOWN);
        if (below.getType().name().contains("FENCE")) {
            event.setCancelled(true);
            trySpawn(to);
        }
    }

    private void trySpawn(Block spawnBlock) {
        if (!spawnBlock.getType().isAir()) return;

        Location spawnLoc = spawnBlock.getLocation();
        Player closest = getNearestPlayer(spawnLoc);
        int level = (closest != null) ? plugin.getPlayerLevel(closest.getUniqueId()) : 1;

        // Thêm xác định world type
        String worldType = "overworld";
        if (spawnBlock.getWorld().getEnvironment() == World.Environment.NETHER) {
            worldType = "nether";
        } else if (spawnBlock.getWorld().getEnvironment() == World.Environment.THE_END) {
            worldType = "the_end";
        }

        generatorManager.spawnBlockAtDelayed(spawnLoc, worldType, level);
    }

    private Player getNearestPlayer(Location loc) {
        Player closest = null;
        double minDist = Double.MAX_VALUE;
        for (Player player : Bukkit.getOnlinePlayers()) {
            double dist = player.getLocation().distance(loc);
            if (dist < minDist && dist <= maxPlayerDistance) {
                closest = player;
                minDist = dist;
            }
        }
        return closest;
    }
}

