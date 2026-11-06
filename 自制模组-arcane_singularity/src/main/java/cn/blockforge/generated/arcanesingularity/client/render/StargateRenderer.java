package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.block.StargateControllerBlock;
import cn.blockforge.generated.arcanesingularity.blockentity.StargateControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.structure.StargateRingAssembly;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 星门方块实体渲染器。
 *
 * <p><b>光影部分已经独立到 {@link StargateFx}</b>（写法类比黑洞 / 激光：层全部来自 {@link FxLayers}、
 * 顶点按原版顶点格式只写必要字段、贴图由层自己绑）。这里只负责随门环转动的那圈<b>门框方块</b>，
 * 以及把门心位姿摆好之后交给 {@link StargateFx} 去画传送面和门环特效。</p>
 *
 * <p>门打开后，门框方块会换成空模型、清掉碰撞，改由这里按 {@link StargateRingAssembly}
 * 的角度画出来：蓝图里每一个门框方块都画它自己的方块模型，整圈像车轮一样绕门心自转，
 * 与带碰撞的门环实体共用同一套角度，所以看到的、撞到的是同一圈东西。关闭时车轮刹停、
 * 方块恢复，再收起门内的传送面。</p>
 */
public class StargateRenderer implements BlockEntityRenderer<StargateControllerBlockEntity> {
    private static final double CENTER_Y = StargateRingAssembly.CENTER_LOCAL_Y + 0.5D;

    private static final BlockState FRAME_MODEL = ModBlocks.STARGATE_FRAME.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);

    @Override
    public void render(StargateControllerBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null || !RenderFx.usable(pose)) {
            return;
        }
        BlockState state = level.getBlockState(be.getBlockPos());
        Direction facing = state.getBlock() instanceof StargateControllerBlock
                ? state.getValue(StargateControllerBlock.FACING)
                : Direction.NORTH;
        double time = level.getGameTime() + partialTick;

        if (be.getProjectionTicks() > 0 && !be.isFormed()) {
            renderProjection(be, pose, buffers, (float) time, facing);
        }
        if (!be.isFormed()) {
            return;
        }

        // 门的局部坐标系：x = 门左右，y = 竖直，z = 门法线。与结构蓝图的 rotateLocal 一致。
        float yaw = switch (facing) {
            case EAST -> 270.0F;
            case WEST -> 90.0F;
            case SOUTH -> 180.0F;
            default -> 0.0F;
        };
        // 门环角度是对速度曲线的积分：开门时由慢到快，关门时由快到慢刹回原位。
        // 符文珠、传送面漩涡都跟这个角度走，绝不用「时间 × 当前转速」，否则加速阶段会乱跳。
        double ringAngle = be.ringAngleAt(time);

        pose.pushPose();
        pose.translate(0.5D, CENTER_Y, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));

        if (be.isRingSpinning()) {
            renderRingBlocks(be, pose, buffers, time, packedLight, packedOverlay);
        }
        StargateFx.render(pose, buffers, time, be.isOpen(), be.openProgress(time), ringAngle, yaw);

        pose.popPose();
    }

    /** 门环：每个门框方块画它自己的模型，整体绕门心像车轮一样转。 */
    private void renderRingBlocks(StargateControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                  double time, int packedLight, int packedOverlay) {
        double ringAngle = be.ringAngleAt(time);
        if (!Double.isFinite(ringAngle)) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (StargateRingAssembly.RingPart part : StargateRingAssembly.ringParts()) {
            double radians = Math.toRadians(part.angleDeg() + ringAngle);
            pose.pushPose();
            pose.translate(Math.cos(radians) * part.radius(), Math.sin(radians) * part.radius(), 0.0D);
            // 方块自身也转：符文面朝切线方向，一眼就能看出整圈轮子在动
            pose.mulPose(Axis.ZP.rotationDegrees((float) ringAngle));
            pose.translate(-0.5D, -0.5D, -0.5D);
            // 用原版方块渲染器画，材质 / 朝向 / 剔除都交给游戏自己处理
            dispatcher.renderSingleBlock(FRAME_MODEL, pose, buffers, packedLight, packedOverlay);
            pose.popPose();
        }
    }

    private void renderProjection(StargateControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                  float time, Direction facing) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        GhostProjectionRenderer.render(level, be.getBlockPos(), StructureKind.STARGATE,
                facing, pose, buffers, time);
    }

    @Override
    public boolean shouldRenderOffScreen(StargateControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2048;
    }
}
