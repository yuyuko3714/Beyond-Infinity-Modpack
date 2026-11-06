package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    public static final TagKey<Item> SINGULARITY_CATALYSTS =
            TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(ArcaneSingularity.MOD_ID, "singularity_catalysts"));

    private ModTags() {
    }
}
