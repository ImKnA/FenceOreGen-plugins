package FenceOreGen;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.HashMap;
import java.util.Map;

public class FenceUtils {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final Map<Material, Boolean> fenceCache = new HashMap<>();
    private static final Map<Material, Boolean> waterCache = new HashMap<>();

    static {
        // Khởi tạo cache cho các loại hàng rào
        for (Material material : Material.values()) {
            String name = material.name();
            if (name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")) {
                fenceCache.put(material, true);
            }
        }

        // Khởi tạo cache cho nước
        waterCache.put(Material.WATER, true);
    }

    public static boolean isFence(Material material) {
        return material != null && fenceCache.getOrDefault(material, false);
    }

    public static boolean isWater(Material material) {
        return material != null && waterCache.getOrDefault(material, false);
    }

    public static boolean isBetweenFenceAndWater(Block center) {
        if (center == null) return false;

        for (BlockFace face : HORIZONTAL_FACES) {
            Block b1 = center.getRelative(face);
            Block b2 = center.getRelative(face.getOppositeFace());

            if ((isFence(b1.getType()) && isWater(b2.getType())) ||
                    (isWater(b1.getType()) && isFence(b2.getType()))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAirOrWater(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type.isAir() || isWater(type);
    }
}