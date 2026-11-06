package cn.blockforge.generated.arcanesingularity.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record GateId(ResourceLocation dimension, BlockPos pos) {
    public static GateId of(Level level, BlockPos pos) {
        return new GateId(level.dimension().location(), pos.immutable());
    }

    public String encode() {
        return dimension + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    @Nullable
    public static GateId parse(String value) {
        String[] parts = value.split("\\|", 4);
        if (parts.length != 4) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(parts[0]);
        if (dimension == null) {
            return null;
        }
        try {
            return new GateId(dimension,
                    new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public String shortText() {
        return dimension + " " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
