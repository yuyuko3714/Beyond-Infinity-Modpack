package cn.blockforge.generated.arcanesingularity.client;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.client.render.ArmillaryPartRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.FxRenderTypes;
import cn.blockforge.generated.arcanesingularity.client.render.RenderFx;
import cn.blockforge.generated.arcanesingularity.client.render.SingularityRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.StargateRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.StargateRingPartRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.TimeBeaconRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.TimeBeaconRingPartRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.WorldCoreCoreRenderer;
import cn.blockforge.generated.arcanesingularity.client.render.WorldCoreRenderer;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModEntities;
import cn.blockforge.generated.arcanesingularity.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ArcaneSingularity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.SINGULARITY.get(), SingularityScreen::new);
            MenuScreens.register(ModMenus.STARGATE.get(), StargateScreen::new);
            MenuScreens.register(ModMenus.TIME_BEACON.get(), TimeBeaconScreen::new);
            MenuScreens.register(ModMenus.WORLD_CORE.get(), WorldCoreScreen::new);
            BlockEntityRenderers.register(ModBlockEntities.SINGULARITY_CONTROLLER.get(),
                    context -> new SingularityRenderer());
            BlockEntityRenderers.register(ModBlockEntities.STARGATE_CONTROLLER.get(),
                    context -> new StargateRenderer());
            BlockEntityRenderers.register(ModBlockEntities.TIME_BEACON_CONTROLLER.get(),
                    context -> new TimeBeaconRenderer());
            BlockEntityRenderers.register(ModBlockEntities.WORLD_CORE_CONTROLLER.get(),
                    context -> new WorldCoreRenderer());
            EntityRenderers.register(ModEntities.ARMILLARY_PART.get(), ArmillaryPartRenderer::new);
            EntityRenderers.register(ModEntities.STARGATE_RING_PART.get(), StargateRingPartRenderer::new);
            EntityRenderers.register(ModEntities.TIME_BEACON_RING_PART.get(), TimeBeaconRingPartRenderer::new);
            EntityRenderers.register(ModEntities.WORLD_CORE_CORE.get(), WorldCoreCoreRenderer::new);
            checkEffectTextures();
        });
    }

    /**
     * 一次性自检：六张光影贴图到底在不在资源里。
     *
     * <p>这些贴图是黑洞 / 激光 / 星门那几层光影直接绑定的，一旦某一张没打进 jar、
     * 或者被资源包顶掉，游戏里看到的就是原版的 missingno 粉黑格。
     * 与其让玩家截图来猜，不如开局就在日志里点名是哪一张。</p>
     */
    private static void checkEffectTextures() {
        try {
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            ResourceLocation[] textures = {
                    RenderFx.BLACK_HOLE, RenderFx.ACCRETION, RenderFx.PORTAL,
                    RenderFx.BEAM, RenderFx.LASER, RenderFx.GLOW, RenderFx.FLAT,
                    FxRenderTypes.GLYPH_OUTER, FxRenderTypes.GLYPH_MID, FxRenderTypes.GLYPH_CORE,
                    FxRenderTypes.SPARK, FxRenderTypes.BEAM};
            for (ResourceLocation texture : textures) {
                if (resources.getResource(texture).isEmpty()) {
                    ArcaneSingularity.LOGGER.warn("[arcane_singularity] 光影贴图缺失：{}（会显示成粉黑 missingno）", texture);
                }
            }
        } catch (RuntimeException exception) {
            ArcaneSingularity.LOGGER.warn("[arcane_singularity] 光影贴图自检没能完成：{}", exception.toString());
        }
    }
}
