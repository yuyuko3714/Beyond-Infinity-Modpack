package cn.blockforge.generated.arcanesingularity;

import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.registry.ModCreativeTabs;
import cn.blockforge.generated.arcanesingularity.registry.ModEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModItems;
import cn.blockforge.generated.arcanesingularity.registry.ModMenus;
import cn.blockforge.generated.arcanesingularity.network.WorldCoreNetwork;
import cn.blockforge.generated.arcanesingularity.structure.StructureDemolisher;
import cn.blockforge.generated.arcanesingularity.time.TimeBeaconService;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 奥术奇点与星门。
 *
 * <p>浑天仪黑洞发生器、配对星门、符文导管、投影搭建、成型终端与奥术法典。</p>
 */
@Mod(ArcaneSingularity.MOD_ID)
public final class ArcaneSingularity {
    public static final String MOD_ID = "arcane_singularity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcaneSingularity() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModMenus.MENUS.register(bus);
        // 世界之核的命令输入通道（客户端 → 服务端）
        WorldCoreNetwork.register();
        // 成型终端：潜行左键控制器 → 整座结构拆除（创造不返、生存返还）
        MinecraftForge.EVENT_BUS.addListener(StructureDemolisher::onLeftClickBlock);
        // 时间信标：每个维度 tick 结束时改写 dayTime，实现减速 / 加速 / 停止
        MinecraftForge.EVENT_BUS.addListener(TimeBeaconService::onLevelTick);
    }
}
