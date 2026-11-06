package cn.blockforge.generated.arcanesingularity.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 每个维度只允许存在一台时间信标。
 *
 * <p>记录的是「这个维度归哪一台时间信标控制器管」，不是运行状态：放下控制器的那一刻就登记，
 * 拆掉控制器（或控制器被炸、被指令替换）就撤销。数据固定存在主世界，跨维度各存各的一条。</p>
 *
 * <p>登记位置如果和实际方块对不上（比如存档被外部工具改过、旧版本遗留下来的记录），
 * 这里会把它当成「过期记录」绕过：只认当前真的立着一台控制器的维度。</p>
 */
public class TimeBeaconData extends SavedData {
    private static final String DATA_NAME = "arcane_singularity_time_beacons";

    private final Map<ResourceKey<Level>, BlockPos> beacons = new HashMap<>();

    public static TimeBeaconData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(TimeBeaconData::load, TimeBeaconData::new, DATA_NAME);
    }

    @Nullable
    public BlockPos beacon(Level level) {
        return beacons.get(level.dimension());
    }

    public boolean isBeacon(Level level, BlockPos pos) {
        return pos.equals(beacons.get(level.dimension()));
    }

    /**
     * 尝试把这个位置登记成该维度唯一的时间信标。
     *
     * @return 登记成功（本来没人占，或占着的就是自己）返回 {@code true}；
     *         该维度已经被另一台控制器占着则返回 {@code false}
     */
    public boolean claim(Level level, BlockPos pos) {
        BlockPos existing = beacons.get(level.dimension());
        if (existing != null && !existing.equals(pos) && !isStale(level, existing)) {
            return false;
        }
        beacons.put(level.dimension(), pos.immutable());
        setDirty();
        return true;
    }

    /** 只撤销自己那一条，不会把别的维度或别的控制器踢下线。 */
    public void release(Level level, BlockPos pos) {
        if (!pos.equals(beacons.get(level.dimension()))) {
            return;
        }
        beacons.remove(level.dimension());
        setDirty();
    }

    /**
     * 登记的位置上已经没有时间信标控制器了。
     *
     * <p>区块没加载时一律当作「还在」——那种情况下查不到方块，贸然判定过期会让同一维度的
     * 记录被随手续上，玩家回来再拆一次就露馅了。</p>
     */
    private boolean isStale(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        return !(level.getBlockState(pos).getBlock()
                instanceof cn.blockforge.generated.arcanesingularity.block.TimeBeaconControllerBlock);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceKey<Level>, BlockPos> entry : beacons.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("Dimension", entry.getKey().location().toString());
            one.putLong("Pos", entry.getValue().asLong());
            list.add(one);
        }
        tag.put("Beacons", list);
        return tag;
    }

    public static TimeBeaconData load(CompoundTag tag) {
        TimeBeaconData data = new TimeBeaconData();
        ListTag list = tag.getList("Beacons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(one.getString("Dimension"));
            if (id == null) {
                continue;
            }
            data.beacons.put(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(one.getLong("Pos")));
        }
        return data;
    }
}
