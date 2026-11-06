package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.block.ProjectionGhostBlock;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.ProjectionService;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 把多方块投影渲染成「目标构件材质的半透明虚影」，而不是一圈线框。
 *
 * <p>方块模型直接从方块渲染器取烘焙模型，再把每个 quad 的顶点摊开写进
 * {@link RenderFx#blockAtlas()}（{@link FxLayers#atlas()}：原版 Core Shader
 * {@code position_color_tex} + 方块图集）。顶点只写「位置 + 颜色 + UV」，
 * 和那个层的 {@code POSITION_COLOR_TEX} 格式严格一一对应，不写 overlay / 光照 / 法线——
 * 少写字段会让整片虚影错位，多写字段则会让 {@code endVertex()} 直接抛异常。</p>
 *
 * <p>完整地基有几百格，逐格画六个面太贵，这里只画它的顶面——反正玩家看投影就是看地面会铺到哪儿。</p>
 */
public final class GhostProjectionRenderer {
    private GhostProjectionRenderer() {
    }

    public static void render(Level level, BlockPos controllerPos, StructureKind kind, Direction facing,
                              PoseStack pose, MultiBufferSource buffers, float time) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        VertexConsumer consumer = buffers.getBuffer(RenderFx.blockAtlas());
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.115F);
        RandomSource random = RandomSource.create(20240712L);
        for (ArcaneStructure.Part part : ArcaneStructure.buildParts(kind, facing)) {
            if (part.type() == ArcaneStructure.PartType.CONTROLLER) {
                continue;
            }
            BlockPos target = controllerPos.offset(part.offset());
            // 区块没加载就不要去查方块状态：渲染线程不该碰尚未就绪的区块（退出世界时尤其危险）。
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockState current = level.getBlockState(target);
            if (current.getBlock() == part.block()) {
                continue;
            }
            // 自然地形成型终端会直接换掉，所以不算「被占住」，虚影不该标红。
            boolean blocked = !current.isAir() && !(current.getBlock() instanceof ProjectionGhostBlock)
                    && !ProjectionService.isNaturalTerrain(current);
            boolean foundation = part.type() == ArcaneStructure.PartType.FOUNDATION;
            float red = blocked ? 1.0F : 0.72F;
            float green = blocked ? 0.42F : 0.82F;
            float blue = blocked ? 0.42F : 1.0F;
            float alpha = foundation ? 0.12F + 0.08F * pulse
                    : blocked ? 0.26F + 0.10F * pulse : 0.30F + 0.16F * pulse;
            BlockState ghostState = part.block().defaultBlockState();
            BakedModel model = blockRenderer.getBlockModel(ghostState);
            pose.pushPose();
            pose.translate(part.offset().getX(), part.offset().getY(), part.offset().getZ());
            PoseStack.Pose last = pose.last();
            if (foundation) {
                emit(consumer, last, model, ghostState, Direction.UP, random, red, green, blue, alpha);
            } else {
                for (Direction side : Direction.values()) {
                    emit(consumer, last, model, ghostState, side, random, red, green, blue, alpha);
                }
                emit(consumer, last, model, ghostState, null, random, red, green, blue, alpha);
            }
            pose.popPose();
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, BakedModel model, BlockState state,
                             Direction side, RandomSource random,
                             float red, float green, float blue, float alpha) {
        List<BakedQuad> quads = model.getQuads(state, side, random);
        if (quads == null || quads.isEmpty()) {
            return;
        }
        for (BakedQuad quad : quads) {
            emitQuad(consumer, pose, quad, red, green, blue, alpha);
        }
    }

    /**
     * 把烘焙 quad 摊成「位置 + 颜色 + UV」四个顶点。
     *
     * <p>顶点数组每个顶点占 {@code length / 4} 个 int，前六项依次是 x、y、z、打包颜色、u、v；
     * 这里按长度动态算步长，不写死格式大小。</p>
     */
    private static void emitQuad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad,
                                 float red, float green, float blue, float alpha) {
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        if (stride < 6) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            int base = i * stride;
            float x = Float.intBitsToFloat(data[base]);
            float y = Float.intBitsToFloat(data[base + 1]);
            float z = Float.intBitsToFloat(data[base + 2]);
            int packed = data[base + 3];
            float vr = ((packed >> 16) & 0xFF) / 255.0F;
            float vg = ((packed >> 8) & 0xFF) / 255.0F;
            float vb = (packed & 0xFF) / 255.0F;
            float va = ((packed >>> 24) & 0xFF) / 255.0F;
            float u = Float.intBitsToFloat(data[base + 4]);
            float v = Float.intBitsToFloat(data[base + 5]);
            consumer.vertex(pose.pose(), x, y, z)
                    .color(red * vr, green * vg, blue * vb, alpha * va)
                    .uv(u, v)
                    .endVertex();
        }
    }
}
