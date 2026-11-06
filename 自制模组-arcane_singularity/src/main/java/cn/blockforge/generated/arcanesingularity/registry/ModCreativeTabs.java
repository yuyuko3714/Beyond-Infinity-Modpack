package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组专属的创造模式标签页。
 *
 * <p>所有方块与道具都收在「奥术奇点与星门」这一页里，按「结构构件 → 能量网络 → 控制器 → 道具」
 * 的顺序排列，方便一次把多方块要用的东西配齐。</p>
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArcaneSingularity.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.arcane_singularity"))
                    .icon(() -> new ItemStack(ModItems.ECHO_OF_THE_END.get()))
                    .displayItems((parameters, output) -> {
                        // 结构构件
                        output.accept(ModItems.RUNE_BRICKS.get());
                        output.accept(ModItems.CHISELED_RUNE_BRICKS.get());
                        output.accept(ModItems.ARCANE_CRYSTAL_BLOCK.get());
                        output.accept(ModItems.RUNE_PILLAR.get());
                        output.accept(ModItems.ARMILLARY_RING.get());
                        output.accept(ModItems.LASER_INJECTOR.get());
                        output.accept(ModItems.LASER_STABILIZER.get());
                        output.accept(ModItems.STARGATE_FRAME.get());
                        output.accept(ModItems.ARCANE_LAMP.get());
                        output.accept(ModItems.CHRONO_RING.get());
                        output.accept(ModItems.TIME_BARREL.get());
                        output.accept(ModItems.WORLD_CORE_INTERFACE.get());
                        output.accept(ModItems.WORLD_CORE_EMITTER.get());
                        output.accept(ModItems.WORLD_CORE_APEX.get());
                        // 符文导管与能量端口
                        output.accept(ModItems.ENERGY_CABLE.get());
                        output.accept(ModItems.WORLD_CORE_CABLE.get());
                        output.accept(ModItems.ENERGY_INPUT_PORT.get());
                        output.accept(ModItems.ENERGY_OUTPUT_PORT.get());
                        // 双核心控制器
                        output.accept(ModItems.SINGULARITY_CONTROLLER.get());
                        output.accept(ModItems.STARGATE_CONTROLLER.get());
                        output.accept(ModItems.TIME_BEACON_CONTROLLER.get());
                        output.accept(ModItems.WORLD_CORE_CONTROLLER.get());
                        // 道具与典籍
                        output.accept(ModItems.ARCANE_SHARD.get());
                        output.accept(ModItems.ECHO_OF_THE_END.get());
                        output.accept(ModItems.WORLD_CORE.get());
                        output.accept(ModItems.FORMATION_TERMINAL.get());
                        output.accept(ModItems.PAIRING_ROD.get());
                        output.accept(ModItems.ARCANE_CODEX.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
