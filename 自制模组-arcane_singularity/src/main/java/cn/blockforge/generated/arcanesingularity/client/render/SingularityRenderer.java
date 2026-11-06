package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.block.ArcaneProperties;
import cn.blockforge.generated.arcanesingularity.blockentity.SingularityControllerBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlocks;
import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
import cn.blockforge.generated.arcanesingularity.structure.StructureKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 浑天仪黑洞发生器的方块实体渲染器。
 *
 * <p><b>职责已经拆开</b>：黑洞、激光、注入器这些「光影」都在各自的独立类里
 * （{@link BlackHoleFx}、{@link LaserFx}），星门那边也一样（{@link StargateFx}）。
 * 这个类只负责随转子转动的<b>实体方块</b>部分：浑天仪环、四台激光稳定器、中环四块奥术晶体、
 * 最顶那圈环，以及三个符文珠环。</p>
 *
 * <p>绘制路线和 r16 一致：所有光晕层来自 {@link FxLayers}（原版 Core Shader
 * {@code position_color} / {@code position_color_tex} + 原版顶点格式），顶点只写必要的字段；
 * 方块本身交给原版 {@code BlockRenderDispatcher.renderSingleBlock}。不碰实体着色器，
 * 所以既不会出现「顶点被按错误步长解读 → 满屏彩色乱三角」，也不会出现贴图绑不上的 missingno。</p>
 *
 * <p>所有自转角度都取自 {@link SingularityControllerBlockEntity#rawSpinAngleAt(double)} /
 * {@link SingularityControllerBlockEntity#layerAngleAt(double, double, double)}：
 * 它们是对速度曲线积分出来的连续角度，所以开机由慢到快、关机由快到慢，中途不会跳角度。</p>
 */
public class SingularityRenderer implements BlockEntityRenderer<SingularityControllerBlockEntity> {
    private static final double TAU = Math.PI * 2.0D;

    private static final BlockState RING_MODEL = ModBlocks.ARMILLARY_RING.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);
    private static final BlockState POD_MODEL = ModBlocks.LASER_STABILIZER.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);
    private static final BlockState TOP_CRYSTAL_MODEL = ModBlocks.ARCANE_CRYSTAL_BLOCK.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true);
    /** 中环那四块会外扩、绕圈转的奥术晶体本体模型。 */
    private static final BlockState CRYSTAL_MODEL = ModBlocks.ARCANE_CRYSTAL_BLOCK.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);
    /** 顶部那座悬浮的激光注入器本体模型（方块本身成型后是空模型）。 */
    private static final BlockState INJECTOR_MODEL = ModBlocks.LASER_INJECTOR.get().defaultBlockState()
            .setValue(ArcaneProperties.FORMED, true)
            .setValue(ArcaneProperties.ACTIVE, false);

    private static final RenderType FLAT_LAYER = RenderFx.emissive(RenderFx.FLAT);
    private static final RenderType GLOW_LAYER = RenderFx.emissive(RenderFx.GLOW);

    /**
     * 符文珠三个环。每个环先绕 {@link #RUNE_BASE_AXIS} 摆到自己的平面上（法线 X / Y / Z，两两垂直），
     * 再绕 {@link #RUNE_TILT_AXIS} 翻滚——那条轴一定落在环面内，所以滚的是「环的一条直径」。
     * 三个环的翻滚轴换算到世界坐标后正好是 Z / X / Y 三根互相垂直的轴。
     */
    private static final Axis[] RUNE_BASE_AXIS = {Axis.ZP, null, Axis.XP};
    private static final Axis[] RUNE_TILT_AXIS = {Axis.ZP, Axis.XP, Axis.ZP};
    /** 每个环绕自己法线自转的系数：珠子沿着环跑，正负号不同方向就各不一样。 */
    private static final double[] RUNE_SPIN = {0.34D, -0.51D, 0.72D};
    /** 每个环绕自己一条直径翻滚的系数：三个环速度和方向都不同。 */
    private static final double[] RUNE_TILT = {0.23D, -0.16D, 0.31D};
    /** 三个符文珠环的半径（互相垂直、球心相同，套成浑天仪）。 */
    private static final double[] RUNE_RADII = {6.6D, 7.7D, 8.7D};
    /** 每个环上的符文珠颗数。 */
    private static final int RUNE_BEADS = 14;

    /** 晶体拖尾取样的颗数与相邻两颗之间的时间间隔（tick）。 */
    private static final int CRYSTAL_TRAIL_SAMPLES = 8;
    private static final double CRYSTAL_TRAIL_STEP_TICKS = 1.0D;

    @Override
    public void render(SingularityControllerBlockEntity be, float partialTick, PoseStack pose,
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

        float animation = be.getAnimationTicks() + partialTick;
        boolean stable = be.isStable();
        boolean active = be.isActive();
        boolean shuttingDown = be.isShuttingDownAt(time);
        float shutdown = be.shutdownProgress(time);
        int phase = be.phase();
        float progress = stable ? 1.0F
                : Mth.clamp(animation / (float) SingularityControllerBlockEntity.RITUAL_LENGTH, 0.0F, 1.0F);
        float charge = stable ? 1.0F : Mth.clamp(animation / 200.0F, 0.0F, 1.0F);
        float converge = stable ? 1.0F : Mth.clamp((animation - 200.0F) / 200.0F, 0.0F, 1.0F);
        float collapse = stable ? 1.0F : Mth.clamp((animation - 400.0F) / 200.0F, 0.0F, 1.0F);
        float flash = stable ? 0.0F : (float) Math.pow(collapse, 3.0D);
        // 连续的自转积分角：每一层再各自乘系数，转速变化时不会跳角度
        double spin = be.rawSpinAngleAt(time);

        float beamPower = stable ? 1.0F : Math.max(charge * 0.5F, converge);
        if (shuttingDown) {
            beamPower = Math.max(beamPower, 1.0F - shutdown);
        }

        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer flat = buffers.getBuffer(FLAT_LAYER);
        VertexConsumer glow = buffers.getBuffer(GLOW_LAYER);

        pose.pushPose();
        pose.translate(0.5D, ArmillaryAssembly.CENTER_Y, 0.5D);

        renderRuneRings(be, pose, lines, flat, glow, time, stable, progress);
        // 顶环中央的激光注入器：成型后就脱离方块形态，一直悬着上下浮动（关机也照样浮着）
        renderInjector(be, pose, buffers, time, packedLight, packedOverlay);
        if (active) {
            renderRotor(be, pose, buffers, packedLight, packedOverlay, spin);
            renderCrystals(be, pose, buffers, time, packedLight, packedOverlay, spin,
                    shuttingDown, shutdown, glow, flat);
            renderTopRing(be, pose, buffers, time, packedLight, packedOverlay);
            LaserFx.render(pose, buffers, spin, time, beamPower);
        }
        BlackHoleFx.renderInjectors(pose, buffers, time, stable, progress, charge, phase, shutdown);
        // 终末之环启动的那一次走炫酷版黑洞；终焉回响启动的仍是原来那套。
        if (be.isEndHalo()) {
            BlackHoleFx.renderEndHalo(pose, buffers, time, spin, stable, collapse, shuttingDown, shutdown);
        } else {
            BlackHoleFx.render(pose, buffers, time, spin, stable, collapse, shuttingDown, shutdown);
        }
        if (shuttingDown) {
            BlackHoleFx.renderShutdown(pose, buffers, shutdown);
        }
        BlackHoleFx.renderStartup(pose, buffers, time, stable, animation, converge, collapse, flash, charge);

        pose.popPose();
    }

    /**
     * 三个符文珠环：环心重合，三个环面两两垂直（法线分别是 X / Y / Z），每个环做两个转动——
     *
     * <ol>
     *     <li>符文珠沿着环自己跑：绕环的法线自转，三个环的转向各不相同；</li>
     *     <li>环面本身<b>绕自己的一条直径翻滚</b>：像陀螺仪的万向环那样翻起来，三个环分别绕
     *         世界 Z / X / Y 三根互相垂直的轴翻，速度和方向都不一样。</li>
     * </ol>
     */
    private void renderRuneRings(SingularityControllerBlockEntity be, PoseStack pose, VertexConsumer lines,
                                 VertexConsumer flat, VertexConsumer glow, double time,
                                 boolean stable, float progress) {
        int bandAlpha = stable ? 150 : (int) (58 + 84 * progress);
        int beadAlpha = stable ? 230 : (int) (110 + 100 * progress);
        int light = (bandAlpha << 24) | 0xD8B478;
        int dark = (Math.max(24, bandAlpha / 3) << 24) | 0x4A2F75;
        for (int i = 0; i < RUNE_RADII.length; i++) {
            double radius = RUNE_RADII[i];
            float spinDeg = (float) ArmillaryAssembly.normalize(be.layerAngleAt(time, RUNE_SPIN[i], i * 37.0D));
            float tiltDeg = (float) ArmillaryAssembly.normalize(be.layerAngleAt(time, RUNE_TILT[i], 0.0D));

            pose.pushPose();
            // ① 把水平环面摆到这个环自己的平面上（法线 X / Y / Z）
            Axis base = RUNE_BASE_AXIS[i];
            if (base != null) {
                pose.mulPose(base.rotationDegrees(90.0F));
            }
            // ② 绕环面内的一条直径翻滚（陀螺仪样式）
            pose.mulPose(RUNE_TILT_AXIS[i].rotationDegrees(tiltDeg));
            // ③ 珠子绕环的法线公转
            pose.mulPose(Axis.YP.rotationDegrees(spinDeg));

            // 环本身：分段明暗的符文环 + 内外两道细亮边
            RenderFx.bandRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                    radius - 0.12D, radius + 0.12D, 192, 6, light, dark);
            planeCircle(lines, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V, radius + 0.15D, 112,
                    255, 222, 150, stable ? 165 : 110);
            planeCircle(lines, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V, radius - 0.15D, 112,
                    150, 200, 255, stable ? 135 : 92);

            // 环上的符文珠：跟着环面一起翻、一起转，绕到哪一侧都看得见
            for (int bead = 0; bead < RUNE_BEADS; bead++) {
                double at = bead * (TAU / RUNE_BEADS);
                pose.pushPose();
                pose.translate(Math.cos(at) * radius, 0.0D, Math.sin(at) * radius);
                RenderFx.glowCross(glow, pose.last(), 0.20D, 255, 236, 176, beadAlpha);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** 在 {@code u}/{@code v} 张成的平面上画一圈线。 */
    private void planeCircle(VertexConsumer lines, PoseStack.Pose pose, Vec3 u, Vec3 v, double radius,
                             int segments, int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = TAU * i / segments;
            double a1 = TAU * (i + 1) / segments;
            double c0 = Math.cos(a0) * radius;
            double s0 = Math.sin(a0) * radius;
            double c1 = Math.cos(a1) * radius;
            double s1 = Math.sin(a1) * radius;
            RenderFx.line(lines, pose,
                    u.x * c0 + v.x * s0, u.y * c0 + v.y * s0, u.z * c0 + v.z * s0,
                    u.x * c1 + v.x * s1, u.y * c1 + v.y * s1, u.z * c1 + v.z * s1,
                    red, green, blue, alpha);
        }
    }

    /**
     * 实体化的转子：整层方块（环 + 四台稳定器）作为刚体绕中轴旋转。
     * 每个构件都画它自己的方块模型，位置与碰撞实体使用同一套角度与半径，所以看到的和撞到的一致。
     */
    private void renderRotor(SingularityControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                             int packedLight, int packedOverlay, double rotor) {
        if (!Double.isFinite(rotor)) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (ArmillaryAssembly.RotorPart part : ArmillaryAssembly.rotorParts()) {
            double angle = part.angleDeg() + rotor;
            double radians = Math.toRadians(angle);
            BlockState state = part.pod() ? POD_MODEL : RING_MODEL;
            pose.pushPose();
            pose.translate(Math.cos(radians) * part.radius(), 0.0D, Math.sin(radians) * part.radius());
            // 方块自己也跟着转：环上的刻纹、稳定器的炮管朝向都随刚体走
            pose.mulPose(Axis.YP.rotationDegrees((float) -angle));
            pose.translate(-0.5D, -0.5D, -0.5D);
            dispatcher.renderSingleBlock(state, pose, buffers, packedLight, packedOverlay);
            pose.popPose();
        }
    }

    /**
     * 中环四块奥术晶体：开机时脱离方块形态、先向外扩出一段，再沿着圆平面由慢到快转；
     * 停机时由快到慢，最后同时往回收，正好在方块恢复的那一帧落回原位。
     *
     * <p>拖尾不是随便撒的光点：每一个拖尾点取的都是「这块晶体前几 tick <b>真正待过的位置</b>」
     * （同一个积分角的过去值、同一个外扩曲线的过去值）。所以它加速时拖尾自然变长、刹车时自然收短，
     * 向外扩的那段还会被拖成一条向外飘的轨迹，全程不会出现凭空冒出来的亮斑或突然断开。</p>
     */
    private void renderCrystals(SingularityControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                double time, int packedLight, int packedOverlay, double rotor,
                                boolean shuttingDown, float shutdown,
                                VertexConsumer glow, VertexConsumer flat) {
        List<ArmillaryAssembly.RotorPart> crystals = ArmillaryAssembly.crystalParts();
        if (crystals.isEmpty() || !Double.isFinite(rotor)) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        // 停机末段把拖尾一起收掉，免得方块都已经复位了还剩一截残影
        float trailFade = shuttingDown ? Mth.clamp(1.0F - shutdown, 0.0F, 1.0F) : 1.0F;
        for (ArmillaryAssembly.RotorPart part : crystals) {
            double outward = be.crystalOutwardAt(time);
            if (!Double.isFinite(outward)) {
                continue;
            }
            if (trailFade > 0.02F) {
                Vec3 previous = null;
                for (int i = CRYSTAL_TRAIL_SAMPLES; i >= 1; i--) {
                    double past = time - i * CRYSTAL_TRAIL_STEP_TICKS;
                    double pastAngle = part.angleDeg() + be.rotorAngleAt(past);
                    double pastRadius = part.radius() + be.crystalOutwardAt(past);
                    if (!Double.isFinite(pastAngle) || !Double.isFinite(pastRadius)) {
                        previous = null;
                        continue;
                    }
                    double radians = Math.toRadians(pastAngle);
                    Vec3 point = new Vec3(Math.cos(radians) * pastRadius, 0.0D, Math.sin(radians) * pastRadius);
                    float near = 1.0F - (float) i / (CRYSTAL_TRAIL_SAMPLES + 1.0F);
                    int alpha = Mth.clamp((int) ((24.0F + 150.0F * near * near) * trailFade), 0, 255);
                    double size = 0.10D + 0.30D * near * near;
                    pose.pushPose();
                    pose.translate(point.x, point.y, point.z);
                    RenderFx.glowCross(glow, pose.last(), size, 208, 172, 255, alpha);
                    pose.popPose();
                    if (previous != null) {
                        // 相邻两个尾迹点之间补一条渐细的纯色光带，拖尾才连成一条「扯出来的尾巴」
                        RenderFx.beamStripFlat(flat, pose.last(), previous, point,
                                0.04D + 0.10D * near, 0.02D, 196, 156, 255, alpha / 2);
                    }
                    previous = point;
                }
            }

            double radians = Math.toRadians(ArmillaryAssembly.normalize(part.angleDeg() + rotor));
            double radius = part.radius() + outward;
            pose.pushPose();
            pose.translate(Math.cos(radians) * radius, 0.0D, Math.sin(radians) * radius);
            pose.mulPose(Axis.YP.rotationDegrees((float) -(part.angleDeg() + rotor)));
            pose.translate(-0.5D, -0.5D, -0.5D);
            dispatcher.renderSingleBlock(CRYSTAL_MODEL, pose, buffers, packedLight, packedOverlay);
            pose.popPose();
        }
    }

    /**
     * 顶环中央的激光注入器：成型后方块被换成空模型（无碰撞），这里把它画成悬浮的样子。
     *
     * <p>上下浮动 + 极慢的横向漂移 + 缓慢自转，看起来就是被能量托在顶环正中央；
     * 浮动量取自 {@link ArmillaryAssembly#injectorHover(double)}，和它向下射出的注入光束
     * 用的是同一个值，所以光柱起点永远贴着注入器底部。</p>
     *
     * <p>只有方块真的已经被标成 {@code active}（空模型）时才画，否则会和世界里的方块
     * 重叠成两层。</p>
     */
    private void renderInjector(SingularityControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                double time, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos offset = ArcaneStructure.singularityInjectorOffset();
        BlockState state = level.getBlockState(be.getBlockPos().offset(offset));
        if (state.getBlock() != ModBlocks.LASER_INJECTOR.get()
                || !state.hasProperty(ArcaneProperties.ACTIVE)
                || !state.getValue(ArcaneProperties.ACTIVE)) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        double hover = ArmillaryAssembly.injectorHover(time);
        double driftX = ArmillaryAssembly.injectorDriftX(time);
        double driftZ = ArmillaryAssembly.injectorDriftZ(time);
        float yaw = (float) ArmillaryAssembly.normalize(time * 0.35D);
        // 渲染坐标系原点在控制器方块中心上方 CENTER_Y、转子平面上：注入器方块中心相对它是 10 格
        double centerY = ArmillaryAssembly.INJECTOR_Y - ArmillaryAssembly.CENTER_Y + hover;
        pose.pushPose();
        pose.translate(offset.getX() + driftX, centerY, offset.getZ() + driftZ);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5D, -0.5D, -0.5D);
        dispatcher.renderSingleBlock(INJECTOR_MODEL, pose, buffers, packedLight, packedOverlay);
        pose.popPose();
    }

    /**
     * 最顶上那圈浑天仪环：启动后一边上下震动、一边由慢到快转；停机时震动收回去、
     * 转速由快到慢刹回原位。它的转动方向与中环相反，两层环套在一起转才像浑天仪。
     */
    private void renderTopRing(SingularityControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                               double time, int packedLight, int packedOverlay) {
        double angle = be.topRingAngleAt(time);
        double bob = be.topRingBobAt(time);
        if (!Double.isFinite(angle) || !Double.isFinite(bob)) {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        // 顶环方块中心在 y=18.5，渲染坐标系已经平移到转子平面，所以这里减掉 CENTER_Y
        double planeY = ArmillaryAssembly.TOP_RING_Y - ArmillaryAssembly.CENTER_Y + bob;
        for (ArmillaryAssembly.RotorPart part : ArmillaryAssembly.topParts()) {
            double radians = Math.toRadians(part.angleDeg() + angle);
            BlockState state = part.block() == ModBlocks.ARMILLARY_RING.get()
                    ? RING_MODEL : TOP_CRYSTAL_MODEL;
            pose.pushPose();
            pose.translate(Math.cos(radians) * part.radius(), planeY, Math.sin(radians) * part.radius());
            pose.mulPose(Axis.YP.rotationDegrees((float) -angle));
            pose.translate(-0.5D, -0.5D, -0.5D);
            dispatcher.renderSingleBlock(state, pose, buffers, packedLight, packedOverlay);
            pose.popPose();
        }
    }

    private void renderProjection(SingularityControllerBlockEntity be, PoseStack pose,
                                  MultiBufferSource buffers, float time) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        GhostProjectionRenderer.render(level, be.getBlockPos(), StructureKind.SINGULARITY,
                Direction.NORTH, pose, buffers, time);
    }

    @Override
    public boolean shouldRenderOffScreen(SingularityControllerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 2048;
    }
}
