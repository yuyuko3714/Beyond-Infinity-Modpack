package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * 世界之核的方块实体渲染器。
 *
 * <p>光影全部独立在 {@link WorldCoreFx}（写法类比黑洞 / 激光：层全部来自 {@link FxLayers}、
 * 顶点按原版顶点格式只写必要字段、贴图由层自己绑）。这里只负责摆好控制器中心位姿，把节拍交给它。</p>
 *
 * <p>一个渲染器管完整座仪式场：三道接口激光、天空的魔法阵、上下对射、中轴光柱，
 * 都在同一个坐标系里画出来，所以它们之间的对位永远是准的。</p>
 *
 * <p>爆发出来的那个核心是实体（带碰撞箱），由 {@code WorldCoreCoreRenderer} 用方块模型渲染，
 * 不在这里往特效缓冲里塞方块。</p>
 */
public class WorldCoreRenderer implements BlockEntityRenderer<WorldCoreControllerBlockEntity> {
    @Override
    public void render(WorldCoreControllerBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null || !RenderFx.usable(pose)) {
            return;
        }
        double time = level.getGameTime() + partialTick;

        if (be.getProjectionTicks() > 0 && !be.isFormed()) {
            GhostProjectionRenderer.render(level, be.getBlockPos(), StructureKind.WORLD_CORE,
                    Direction.NORTH, pose, buffers, (float) time);
        }
        if (!be.isFormed()) {
            return;
        }

        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        WorldCoreFx.render(pose, buffers, time,
                be.chargeAt(time), be.gatherAt(time), be.glyphOpenAt(time),
                be.glyphAngleRawAt(time), be.glyphSpinRampAt(time),
                be.convergeAt(time), be.coreBurstAt(time),
                be.beamPowerAt(time), be.isRunning(), be.shutdownProgress(time),
                be.getCommandMode());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(WorldCoreControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2048;
    }
}
