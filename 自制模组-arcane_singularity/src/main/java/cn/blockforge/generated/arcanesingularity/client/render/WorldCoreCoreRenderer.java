package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.entity.WorldCoreCoreEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 世界之核核心的实体渲染器。
 *
 * <p>和浑天仪环、时间信标汇聚环走同一条路：<b>所有几何都是真实的小方块模型</b>，
 * 交给原版 {@code renderSingleBlock} 画，不在特效缓冲里拼大面片。</p>
 *
 * <p>以前那个版本在特效阶段直接画一个放大 3.7 倍的命令方块，再在外面叠两圈水平的
 * 半透明光环。光环是几十格宽的水平四边形，玩家站在核心附近的高度时，这些四边形正好
 * 和视线共面、贴着镜头铺过去，屏幕上就出现「所有细线朝准星汇聚」和「屏幕被切成四块」。
 * 现在换成：</p>
 * <ul>
 *     <li>中心：命令方块 / 循环命令方块模型，按爆发进度由小放大，像末地水晶那样自转轻摆；</li>
 *     <li>外面两圈：由 {@code armillary_ring} 小方块拼出来的环，一圈水平、一圈立起来，
 *         转向相反。方块有厚度、有棱角，从任何角度看都只是「一圈小方块在转」，
 *         不可能再被投影成横跨整个屏幕的线条；</li>
 *     <li>爆发瞬间：一圈小方块从核心向外炸开，扩散出去就消失。</li>
 * </ul>
 */
public class WorldCoreCoreRenderer extends EntityRenderer<WorldCoreCoreEntity> {
    private static final BlockState PULSE_CORE = Blocks.COMMAND_BLOCK.defaultBlockState();
    private static final BlockState CYCLE_CORE = Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
    /** 由小方块拼环用的环段模型（和浑天仪环同一个模型，视觉上是同一套机械）。 */
    private static final BlockState RING_SEGMENT = ModBlocks.ARMILLARY_RING.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);

    /** 核心模型完全展开后的缩放（3.7 格宽）。 */
    private static final double CORE_MAX_SCALE = 3.7D;

    public WorldCoreCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public void render(WorldCoreCoreEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        Level level = entity.level();
        if (level == null) {
            return;
        }
        float burst = Mth.clamp(entity.burstAt(partialTick), 0.0F, 1.0F);
        if (burst <= 0.01F) {
            return;
        }
        double time = level.getGameTime() + partialTick;
        double scale = 0.4D + (CORE_MAX_SCALE - 0.4D) * burst;
        int mode = entity.getCommandMode();
        BlockState core = mode == 0 ? PULSE_CORE : CYCLE_CORE;
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        // 实体坐标是碰撞箱底面中心，先抬到核心中心
        pose.pushPose();
        pose.translate(0.0D, WorldCoreCoreEntity.CORE_HEIGHT * 0.5D, 0.0D);

        double bob = Math.sin(time * 0.06D) * 0.22D * burst;
        pose.translate(0.0D, bob, 0.0D);

        double spin = time * (2.4D + 1.6D * (1.0D - burst));

        // 爆发瞬间：一圈小方块从核心向外炸开
        double shock = Math.min(1.0D, burst * 2.2D);
        if (shock < 1.0D) {
            double shockRadius = scale * (1.0D + 3.4D * shock);
            double shockBlock = 0.18D + 0.30D * (1.0D - shock);
            renderRing(dispatcher, pose, buffers, shockRadius, shockBlock, spin * 2.0D, 0.0D);
        }

        // 中心：命令方块本体，自转 + 缓慢倾斜
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) spin));
        pose.mulPose(Axis.XP.rotationDegrees((float) (18.0D * Math.sin(time * 0.045D))));
        pose.mulPose(Axis.ZP.rotationDegrees((float) (12.0D * Math.cos(time * 0.037D))));
        pose.scale((float) scale, (float) scale, (float) scale);
        pose.translate(-0.5D, -0.5D, -0.5D);
        dispatcher.renderSingleBlock(core, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        // 外面两圈：一圈水平、一圈立起来，由小方块拼成，转向相反
        renderRing(dispatcher, pose, buffers, scale * 1.55D, 0.52D, spin * 1.4D, 0.0D);
        renderRing(dispatcher, pose, buffers, scale * 1.75D, 0.42D, -spin * 0.85D, 70.0D);

        pose.popPose();
    }

    /**
     * 用方块模型拼一圈环。
     *
     * @param radius  环的半径（格）
     * @param size    每块方块的边长（格，1 表示整格）
     * @param spinDeg 整圈此刻转过的角度
     * @param tiltDeg 环面绕 X 轴倾斜的角度（0 = 水平；70 = 立起来）
     */
    private void renderRing(BlockRenderDispatcher dispatcher, PoseStack pose, MultiBufferSource buffers,
                            double radius, double size, double spinDeg, double tiltDeg) {
        if (!Double.isFinite(radius) || radius <= 0.05D || !Double.isFinite(spinDeg)) {
            return;
        }
        // 每两块之间留一点点缝，看起来才像一节一节的机械环；段数按半径走，远了也不会太稀
        int segments = Mth.clamp((int) Math.round(Math.PI * 2.0D * radius / 0.95D), 12, 72);
        float blockScale = Mth.clamp((float) size, 0.08F, 1.0F);

        pose.pushPose();
        if (Math.abs(tiltDeg) > 0.01D) {
            pose.mulPose(Axis.XP.rotationDegrees((float) tiltDeg));
        }
        for (int i = 0; i < segments; i++) {
            double angle = 360.0D * i / segments + spinDeg;
            double radians = Math.toRadians(angle);
            pose.pushPose();
            pose.translate(Math.cos(radians) * radius, 0.0D, Math.sin(radians) * radius);
            // 让方块跟着环的切线方向摆正，转起来才是一整圈
            pose.mulPose(Axis.YP.rotationDegrees((float) -angle));
            pose.scale(blockScale, blockScale, blockScale);
            pose.translate(-0.5D, -0.5D, -0.5D);
            dispatcher.renderSingleBlock(RING_SEGMENT, pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(WorldCoreCoreEntity entity) {
        return RenderFx.BEAM;
    }
}
