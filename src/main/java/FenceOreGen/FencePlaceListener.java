package FenceOreGen;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;

public class FencePlaceListener implements Listener {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private final FenceOreGen plugin;

    public FencePlaceListener(FenceOreGen plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFencePlaced(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (!FenceUtils.isFence(placed.getType()) || plugin.isWorldDisabled(placed.getWorld().getName())) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        int level = plugin.getPlayerLevel(uuid);

        for (BlockFace face : HORIZONTAL_FACES) {
            Block adjacent = placed.getRelative(face);
            if (FenceUtils.isWater(adjacent.getType())) {
                Block spawnBlock = placed.getRelative(face.getOppositeFace());
                trySpawnOre(spawnBlock, level);
                break;
            }
        }
    }

    private String getWorldType(World world) {
        if (world == null) return "overworld";
        return switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> "overworld";
        };
    }

    private void trySpawnOre(Block block, int level) {
        if (FenceUtils.isAirOrWater(block)) {
            // Thêm worldType khi gọi getRandomBlock()
            String worldType = getWorldType(block.getWorld());
            Material newOre = plugin.getGeneratorManager().getRandomBlock(worldType, level);
            if (newOre != null) {
                block.setType(newOre);
                // Play effects...
            }
        }
    }
}