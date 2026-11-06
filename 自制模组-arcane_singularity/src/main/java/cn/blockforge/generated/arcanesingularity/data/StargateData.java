package cn.blockforge.generated.arcanesingularity.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局星门配对表。数据固定保存在主世界，因此可以跨维度配对。
 */
public class StargateData extends SavedData {
    private static final String DATA_NAME = "arcane_singularity_stargates";
    private static final int OFFLINE_AFTER_TICKS = 120;
    private final Map<GateId, GateRecord> gates = new HashMap<>();

    public static StargateData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(StargateData::load, StargateData::new, DATA_NAME);
    }

    public GateRecord record(GateId id) {
        GateRecord record = gates.get(id);
        if (record == null) {
            record = new GateRecord();
            gates.put(id, record);
            setDirty();
        }
        return record;
    }

    @Nullable
    public GateRecord get(GateId id) {
        return gates.get(id);
    }

    @Nullable
    public GateId partnerOf(GateId id) {
        GateRecord record = gates.get(id);
        return record == null ? null : record.partner;
    }

    public String sourceOf(GateId id) {
        GateRecord record = gates.get(id);
        return record == null ? "" : record.source;
    }

    public boolean isOnline(GateId id, long now) {
        GateRecord record = gates.get(id);
        return record != null
                && record.powered
                && now - record.lastSeen <= OFFLINE_AFTER_TICKS
                && record.forcedUntil <= now;
    }

    /**
     * 这扇门此刻是不是真的开着（跨维度读记录，不依赖方块实体在场）。
     *
     * <p>传送要求<b>两端同时开门</b>：只要对面记录里不是开启状态（或已经掉线 / 刚被强制关闭），
     * 这边就不能把人送过去。</p>
     */
    public boolean isGateOpen(GateId id, long now) {
        GateRecord record = gates.get(id);
        return record != null
                && record.opened
                && now - record.lastSeen <= OFFLINE_AFTER_TICKS
                && record.forcedUntil <= now;
    }

    /** 方块实体每次重算开门条件时把结果写回全局表，另一扇门跨维度也能读到。 */
    public void reportOpen(GateId id, boolean opened, long now) {
        GateRecord record = record(id);
        record.lastSeen = now;
        if (record.opened != opened) {
            record.opened = opened;
            setDirty();
        }
    }

    public long forcedUntil(GateId id) {
        GateRecord record = gates.get(id);
        return record == null ? 0L : record.forcedUntil;
    }

    /** 这扇门是不是被玩家「强制关闭」过。锁着的时候不会因为条件重新满足而自己弹开。 */
    public boolean isCloseLocked(GateId id) {
        GateRecord record = gates.get(id);
        return record != null && record.closeLocked;
    }

    /** 解除关闭锁：重新拉红石、或者断开能量再接通时调用。 */
    public void unlock(GateId id) {
        GateRecord record = gates.get(id);
        if (record == null || !record.closeLocked) {
            return;
        }
        record.closeLocked = false;
        record.forcedUntil = 0L;
        setDirty();
    }

    public boolean pair(GateId first, GateId second) {
        if (first.equals(second)) {
            return false;
        }
        unpair(first);
        unpair(second);
        GateRecord a = record(first);
        GateRecord b = record(second);
        a.partner = second;
        b.partner = first;
        a.powered = false;
        b.powered = false;
        a.forcedUntil = 0L;
        b.forcedUntil = 0L;
        setDirty();
        return true;
    }

    public void unpair(GateId id) {
        GateRecord record = gates.get(id);
        if (record == null || record.partner == null) {
            return;
        }
        GateId oldPartner = record.partner;
        record.partner = null;
        record.opened = false;
        GateRecord other = gates.get(oldPartner);
        if (other != null && id.equals(other.partner)) {
            other.partner = null;
            other.powered = false;
            other.opened = false;
        }
        setDirty();
    }

    public void report(GateId id, boolean powered, String source, long now) {
        GateRecord record = record(id);
        String text = source == null ? "" : source;
        GateRecord partner = record.partner == null ? null : gates.get(record.partner);
        boolean dirty = false;
        if (record.powered && !powered && partner != null) {
            partner.forcedUntil = now + 200L;
            dirty = true;
        }
        if (record.powered != powered || !record.source.equals(text)) {
            dirty = true;
        }
        record.powered = powered;
        record.source = text;
        // lastSeen 只用于判断在线，不写进存档也不值得把整个存档反复标脏。
        record.lastSeen = now;
        if (dirty) {
            setDirty();
        }
    }

    public void invalidate(GateId id, long now) {
        GateRecord record = gates.get(id);
        if (record == null) {
            return;
        }
        record.powered = false;
        record.opened = false;
        record.lastSeen = now;
        if (record.partner != null) {
            GateRecord partner = gates.get(record.partner);
            if (partner != null) {
                partner.forcedUntil = now + 200L;
            }
        }
        setDirty();
    }

    /**
     * 强制关闭这一对星门：给主动关闭的那扇门上锁（条件再满足也不会自己弹开），
     * 对端只做一段短时压制（它靠 {@link #isCloseLocked(GateId)} 读到这边的锁，同样保持关闭）。
     */
    public void forceClosePair(GateId id, long now) {
        GateRecord record = record(id);
        record.powered = false;
        record.opened = false;
        record.closeLocked = true;
        record.forcedUntil = now + 300L;
        if (record.partner != null) {
            GateRecord partner = gates.get(record.partner);
            if (partner != null) {
                partner.powered = false;
                // 一端被强制关闭＝两边都不再是「同时开着」，立刻撤掉对面的开门记录，
                // 免得这 20 tick 的刷新空档里还能从对面传走。
                partner.opened = false;
                partner.forcedUntil = now + 300L;
            }
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<GateId, GateRecord> entry : gates.entrySet()) {
            GateRecord record = entry.getValue();
            CompoundTag one = new CompoundTag();
            one.putString("Gate", entry.getKey().encode());
            one.putBoolean("Powered", record.powered);
            one.putBoolean("Opened", record.opened);
            one.putBoolean("CloseLocked", record.closeLocked);
            one.putLong("LastSeen", record.lastSeen);
            one.putLong("ForcedUntil", record.forcedUntil);
            one.putString("Source", record.source);
            if (record.partner != null) {
                one.putString("Partner", record.partner.encode());
            }
            list.add(one);
        }
        tag.put("Gates", list);
        return tag;
    }

    public static StargateData load(CompoundTag tag) {
        StargateData data = new StargateData();
        ListTag list = tag.getList("Gates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag one = list.getCompound(i);
            GateId id = GateId.parse(one.getString("Gate"));
            if (id == null) {
                continue;
            }
            GateRecord record = new GateRecord();
            record.powered = one.getBoolean("Powered");
            record.opened = one.getBoolean("Opened");
            record.closeLocked = one.getBoolean("CloseLocked");
            record.lastSeen = one.getLong("LastSeen");
            record.forcedUntil = one.getLong("ForcedUntil");
            record.source = one.getString("Source");
            record.partner = GateId.parse(one.getString("Partner"));
            data.gates.put(id, record);
        }
        return data;
    }

    public static final class GateRecord {
        public boolean powered;
        /** 这扇门的传送面此刻是不是张开着；两端都为真才允许传送。 */
        public boolean opened;
        /** 玩家主动「关闭星门」后置位：条件全部满足也不自动重开，直到红石重新触发或能量重接。 */
        public boolean closeLocked;
        public long lastSeen;
        public long forcedUntil;
        public String source = "";
        @Nullable
        public GateId partner;
    }
}
