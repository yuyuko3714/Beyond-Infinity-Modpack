package cn.blockforge.generated.arcanesingularity.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 多方块结构的区块强加载。
 *
 * <p>放下奇点摇篮或星门控制器时，把整座结构所覆盖到的每一个区块都加进原版的强加载列表
 * （{@link ServerLevel#setChunkForced(int, int, boolean)}，等价于 {@code /forceload add}），
 * 破坏控制器时再逐块撤销。这样浑天仪的转环、星门的门环实体和传送动画即使玩家走远也照常运转，
 * 不会因为区块卸载而停在半路。</p>
 *
 * <p>覆盖范围由蓝图直接算出（构件 + 地基的包围盒 → 区块列），不扫世界、不主动加载任何区块。</p>
 *
 * <p>强加载是「共享」的：这里记着每个区块被哪些控制器占着，只有当最后一座结构也撤销之后，
 * 才真的把这一块从原版强加载列表里删掉，相邻的两座星门不会互相把对方踢下线。
 * 这张表按 {@link Level} 弱引用保存，退出世界后自然释放；重新进世界时由控制器的
 * {@code tickServer} 第一次心跳把归属重新登记回来（原版的强加载列表本身就存在存档里）。</p>
 */
public final class ChunkKeeper {
    /** 单座结构最多强加载多少区块：蓝图如果被改坏，也不至于把整个世界都锁住。 */
    private static final int MAX_CHUNKS = 64;

    /** 蓝图包围盒缓存：{@code kind|facing -> {minX, maxX, minZ, maxZ}}（相对控制器）。 */
    private static final Map<String, int[]> BOUNDS = new HashMap<>();

    /** 区块 → 占着它的控制器坐标。按 Level 弱引用，避免退出世界后把旧维度留在内存里。 */
    private static final Map<Level, Map<Long, Set<BlockPos>>> OWNERS = new WeakHashMap<>();

    private ChunkKeeper() {
    }

    /**
     * 强加载整座结构覆盖的所有区块（重复调用是安全的空操作）。
     *
     * @return 这座结构覆盖的区块数量
     */
    public static int keep(Level level, BlockPos origin, StructureKind kind, Direction facing) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        List<ChunkPos> chunks = chunkColumns(origin, kind, facing);
        Map<Long, Set<BlockPos>> owners = OWNERS.computeIfAbsent(server, key -> new HashMap<>());
        BlockPos owner = origin.immutable();
        for (ChunkPos chunk : chunks) {
            owners.computeIfAbsent(chunk.toLong(), key -> new HashSet<>()).add(owner);
            server.setChunkForced(chunk.x, chunk.z, true);
        }
        return chunks.size();
    }

    /**
     * 撤销这座结构登记过的强加载。同一区块如果还被别的控制器占着，就留着不撤。
     *
     * @return 真正被解除强加载的区块数量
     */
    public static int release(Level level, BlockPos origin, StructureKind kind, Direction facing) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        List<ChunkPos> chunks = chunkColumns(origin, kind, facing);
        Map<Long, Set<BlockPos>> owners = OWNERS.get(server);
        int released = 0;
        for (ChunkPos chunk : chunks) {
            Set<BlockPos> set = owners == null ? null : owners.get(chunk.toLong());
            if (set != null) {
                set.remove(origin);
                if (!set.isEmpty()) {
                    continue;
                }
                owners.remove(chunk.toLong());
            }
            server.setChunkForced(chunk.x, chunk.z, false);
            released++;
        }
        if (owners != null && owners.isEmpty()) {
            OWNERS.remove(server);
        }
        return released;
    }

    /** 结构覆盖到的区块列（不看高度，只看 x/z）。连地基一起算，整座结构脚下的区块都保持加载。 */
    private static List<ChunkPos> chunkColumns(BlockPos origin, StructureKind kind, Direction facing) {
        int[] bounds = BOUNDS.computeIfAbsent(kind + "|" + facing.name(), key -> computeBounds(kind, facing));
        int minChunkX = (origin.getX() + bounds[0] - 1) >> 4;
        int maxChunkX = (origin.getX() + bounds[1] + 1) >> 4;
        int minChunkZ = (origin.getZ() + bounds[2] - 1) >> 4;
        int maxChunkZ = (origin.getZ() + bounds[3] + 1) >> 4;
        List<ChunkPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (result.size() >= MAX_CHUNKS) {
                    return result;
                }
                result.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return result;
    }

    /** 蓝图（构件 + 地基）相对控制器的 x/z 包围盒。蓝图是静态的，算一次缓存起来。 */
    private static int[] computeBounds(StructureKind kind, Direction facing) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (ArcaneStructure.Part part : ArcaneStructure.buildParts(kind, facing)) {
            BlockPos offset = part.offset();
            minX = Math.min(minX, offset.getX());
            maxX = Math.max(maxX, offset.getX());
            minZ = Math.min(minZ, offset.getZ());
            maxZ = Math.max(maxZ, offset.getZ());
        }
        if (minX > maxX || minZ > maxZ) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{minX, maxX, minZ, maxZ};
    }
}
