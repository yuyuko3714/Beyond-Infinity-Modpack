package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.blockentity.TimeBeaconControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import cn.blockforge.generated.arcanesingularity.structure.TimeBeaconAssembly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 时间信标的方块实体渲染器。
 *
 * <p><b>光影部分独立在 {@link TimeBeaconFx}</b>（写法类比黑洞 / 激光：层全部来自 {@link FxLayers}、
 * 顶点按原版顶点格式只写必要字段、贴图由层自己绑）。这里只负责两件事：把两圈随转速旋转的
 * <b>汇聚环方块</b>画出来，以及把塔基位姿摆好之后交给 {@link TimeBeaconFx}。</p>
 *
 * <p>开机后汇聚环方块换成空模型、清掉碰撞，改由这里按 {@link TimeBeaconAssembly} 的角度画出
 * 每一个环方块自己的模型；碰撞交给带碰撞箱的实体，两者用的是同一个角度，所以看到的、撞到的
 * 永远是同一圈东西。</p>
 */
public class TimeBeaconRenderer implements BlockEntityRenderer<TimeBeaconControllerBlockEntity> {
    private static final BlockState CHRONO_MODEL = ModBlocks.CHRONO_RING.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);
    private static final BlockState BARREL_MODEL = ModBlocks.TIME_BARREL.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);
    private static final BlockState CRYSTAL_MODEL = ModBlocks.ARCANE_CRYSTAL_BLOCK.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);

    @Override
    public void render(TimeBeaconControllerBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null || !RenderFx.usable(pose)) {
            return;
        }
        double time = level.getGameTime() + partialTick;

        if (be.getProjectionTicks() > 0 && !be.isFormed()) {
            renderProjection(be, pose, buffers, (float) time);
        }
        if (!be.isFormed()) {
            return;
        }

        // 渲染坐标系原点在控制器方块中心；塔轴相对控制器偏移 BEACON_AXIS_*
        pose.pushPose();
        pose.translate(0.5D + ArcaneStructure.BEACON_AXIS_X, ArcaneStructure.BEACON_AXIS_Y,
                0.5D + ArcaneStructure.BEACON_AXIS_Z);

        if (be.isRingEngaged()) {
            renderRingBlocks(be, pose, buffers, time, packedLight, packedOverlay);
        }
        renderTopParts(be, pose, buffers, time, packedLight, packedOverlay);

        double[] angles = {be.ringAngleAt(time, 0), be.ringAngleAt(time, 1), be.ringAngleAt(time, 2)};
        // 顶部炮管整体在浮动，光柱的起点/终点也要跟着它走，否则激光会和炮口脱开
        TimeBeaconFx.render(pose, buffers, time, be.getMode().id(), angles, 2,
                be.spinRampAt(time), be.chargeAt(time), be.beamPowerAt(time),
                be.isRunning(), be.shutdownProgress(time), TimeBeaconAssembly.topBarrelBob(time));

        pose.popPose();
    }

    /**
     * 塔顶：两格悬浮的时序炮管（上下振动）、最顶上一层小型聚能环（正转）、
     * 四块奥术晶体（与环反向旋转）。
     *
     * <p>两根时序炮管是<b>一个刚体</b>：同一组浮动/摆动参数，倾斜绕两根合起来的中心
     * （{@link TimeBeaconAssembly#TOP_BARREL_PIVOT_Y}）做，所以看到的是一整截悬浮炮管在晃，
     * 而不是两个方块各震各的。位置全部按「相对炮塔中轴」换算——渲染位姿已经平移到中轴上，
     * 之前又把方块的绝对 local 坐标加了一遍，结果整截炮管偏出去 8 格。</p>
     *
     * <p>炮管在结构成型后就换成空模型，所以这里必须一直画；小环与晶体只在启动后隐藏，
     * 因此只在 {@code ringEngaged} 时补画。</p>
     */
    private void renderTopParts(TimeBeaconControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                double time, int packedLight, int packedOverlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        // ① 两格时序炮管：整截一起上下浮动、绕自身中心轻微摆动
        var barrels = ArcaneStructure.beaconTopBarrelOffsets();
        double bob = TimeBeaconAssembly.topBarrelBob(time);
        double swayX = Math.sin(time * 0.045D) * 0.045D;
        double swayZ = Math.cos(time * 0.052D) * 0.045D;
        float yaw = (float) (Math.sin(time * 0.030D) * 6.0D);
        float tiltX = (float) (Math.sin(time * 0.041D) * 1.6D);
        float tiltZ = (float) (Math.cos(time * 0.037D) * 1.6D);
        double pivotY = TimeBeaconAssembly.TOP_BARREL_PIVOT_Y;

        pose.pushPose();
        pose.translate(swayX, bob, swayZ);
        pose.translate(0.0D, pivotY, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(tiltX));
        pose.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        pose.translate(0.0D, -pivotY, 0.0D);
        for (BlockPos offset : barrels) {
            pose.pushPose();
            // 位姿原点在炮管中轴上：local 坐标要减掉中轴偏移，再加半格到方块中心
            pose.translate(offset.getX() - ArcaneStructure.BEACON_AXIS_X,
                    offset.getY() + 0.5D,
                    offset.getZ() - ArcaneStructure.BEACON_AXIS_Z);
            pose.translate(-0.5D, -0.5D, -0.5D);
            dispatcher.renderSingleBlock(BARREL_MODEL, pose, buffers, packedLight, packedOverlay);
            pose.popPose();
        }
        pose.popPose();

        if (!be.isRingEngaged()) {
            return;
        }

        // ② 最顶上一层小型聚能环：正转，由慢到快
        double ringAngle = be.ringAngleAt(time, 2);
        if (Double.isFinite(ringAngle)) {
            for (TimeBeaconAssembly.RingPart part : TimeBeaconAssembly.topRing()) {
                double radians = Math.toRadians(part.angleDeg() + ringAngle);
                pose.pushPose();
                pose.translate(Math.cos(radians) * part.radius(), part.y(), Math.sin(radians) * part.radius());
                pose.mulPose(Axis.YP.rotationDegrees((float) -ringAngle));
                pose.translate(-0.5D, -0.5D, -0.5D);
                dispatcher.renderSingleBlock(CHRONO_MODEL, pose, buffers, packedLight, packedOverlay);
                pose.popPose();
            }
        }

        // ③ 四块奥术晶体：与小型聚能环反向旋转
        double crystalAngle = be.topCrystalAngleAt(time);
        if (Double.isFinite(crystalAngle)) {
            var crystals = ArcaneStructure.beaconTopCrystalOffsets();
            for (int i = 0; i < crystals.size(); i++) {
                var offset = crystals.get(i);
                // 半径与方位角都以炮塔中轴为准：位姿原点已经在塔轴上了，
                // 直接拿方块绝对坐标去算会把中轴偏移 8 格也算进半径里，
                // 四块晶体就会绕着半径 10 的大圈飞到塔外去（这正是之前「旋转方式不对」的原因）。
                double dx = offset.getX() - ArcaneStructure.BEACON_AXIS_X;
                double dz = offset.getZ() - ArcaneStructure.BEACON_AXIS_Z;
                double baseAngle = Math.toDegrees(Math.atan2(dz, dx));
                double radians = Math.toRadians(baseAngle + crystalAngle);
                double radius = Math.hypot(dx, dz);
                pose.pushPose();
                pose.translate(Math.cos(radians) * radius, offset.getY() + 0.5D,
                        Math.sin(radians) * radius);
                pose.mulPose(Axis.YP.rotationDegrees((float) -crystalAngle));
                pose.translate(-0.5D, -0.5D, -0.5D);
                dispatcher.renderSingleBlock(CRYSTAL_MODEL, pose, buffers, packedLight, packedOverlay);
                pose.popPose();
            }
        }
    }

    /** 两圈汇聚环：每一格环方块画它自己的模型，整体绕炮管中轴转。 */
    private void renderRingBlocks(TimeBeaconControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                  double time, int packedLight, int packedOverlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (int ring = 0; ring < 2; ring++) {
            double angle = be.ringAngleAt(time, ring);
            if (!Double.isFinite(angle)) {
                continue;
            }
            for (TimeBeaconAssembly.RingPart part : TimeBeaconAssembly.ringParts(ring)) {
                double radians = Math.toRadians(part.angleDeg() + angle);
                pose.pushPose();
                pose.translate(Math.cos(radians) * part.radius(), part.y(), Math.sin(radians) * part.radius());
                // 方块自己也跟着转：环上的刻纹随刚体走，一眼能看出整圈在动
                pose.mulPose(Axis.YP.rotationDegrees((float) -angle));
                pose.translate(-0.5D, -0.5D, -0.5D);
                dispatcher.renderSingleBlock(CHRONO_MODEL, pose, buffers, packedLight, packedOverlay);
                pose.popPose();
            }
        }
    }

    private void renderProjection(TimeBeaconControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                  float time) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        GhostProjectionRenderer.render(level, be.getBlockPos(), StructureKind.TIMEBEACON,
                Direction.NORTH, pose, buffers, time);
    }

    @Override
    public boolean shouldRenderOffScreen(TimeBeaconControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2048;
    }
}
