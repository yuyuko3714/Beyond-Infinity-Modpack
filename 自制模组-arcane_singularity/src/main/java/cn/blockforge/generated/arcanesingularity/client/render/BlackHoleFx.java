package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 黑洞渲染 —— 从方块实体渲染器里<b>独立出来</b>的一层，写法照搬 r16（玩家机上不再花屏的那一版）。
 *
 * <p>画法只有三句话（层全部来自 {@link FxLayers}）：</p>
 * <ol>
 *     <li>几何用 {@link RenderFx} 一个个顶点写，顶点只写「位置 + 颜色 + UV」
 *         （原版 {@code POSITION_COLOR_TEX}，不写 overlay / 光照 / 法线）；</li>
 *     <li>贴图交给 {@link FxLayers} 的层自己绑定，着色器是原版 Core Shader
 *         {@code position_color_tex}，<b>完全不碰实体着色器</b>（玩家整合包里那套被改坏过）；</li>
 *     <li>线框用 {@link FxLayers#LINES}；所有叠加层只写颜色不写深度，
 *         几百片重叠的粒子环换个角度也不会互相切出硬边。</li>
 * </ol>
 *
 * <p>这里包含黑洞的全部视觉：视界本体（贴图球，整体自转就是动画）、引力透镜柔光、两圈光子环、
 * 三圈紫黑分段环、两层吸积盘，以及停机塌缩波与开机三阶段的能量特效。
 * 所有自转都用 {@code spin}（{@code SingularityControllerBlockEntity#rawSpinAngleAt} 的积分角），
 * 所以开机由慢到快、关机由快到慢，中途不会跳角度。</p>
 */
public final class BlackHoleFx {
    private static final RenderType HOLE = RenderFx.emissive(RenderFx.BLACK_HOLE);
    private static final RenderType DISK = RenderFx.emissive(RenderFx.ACCRETION);
    private static final RenderType FLAT = RenderFx.emissive(RenderFx.FLAT);
    private static final RenderType GLOW = RenderFx.emissive(RenderFx.GLOW);
    private static final RenderType BEAM = RenderFx.emissive(RenderFx.BEAM);

    private static final double BASE_RADIUS = 1.72D;

    private BlackHoleFx() {
    }

    /**
     * 黑洞主体：视界 + 光环 + 吸积盘。
     *
     * @param time         游戏时间（tick，含插值）
     * @param spin         转子的累计积分角（度），决定各层转速
     * @param stable       是否稳定运行
     * @param collapse     开机坍缩进度 0..1（未成形时用）
     * @param shuttingDown 是否正在停机
     * @param shutdown     停机进度 0..1
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, double time, double spin,
                              boolean stable, float collapse, boolean shuttingDown, float shutdown) {
        if (!RenderFx.usable(pose) || !Double.isFinite(time) || !Double.isFinite(spin)) {
            return;
        }
        double holeRadius = shuttingDown ? BASE_RADIUS * (1.0D - shutdown)
                : stable ? BASE_RADIUS + Math.sin(time * 0.12D) * 0.05D
                : 1.78D * collapse;
        if (!Double.isFinite(holeRadius) || holeRadius <= 0.05D) {
            return;
        }
        float life = shuttingDown ? Mth.clamp((float) (1.0D - shutdown), 0.0F, 1.0F) : 1.0F;

        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer hole = buffers.getBuffer(HOLE);
        VertexConsumer disk = buffers.getBuffer(DISK);
        VertexConsumer flat = buffers.getBuffer(FLAT);

        // ① 视界本体：贴上完整漩涡材质的球，整体自转就是「材质在动」的动画。
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(spin * 0.55D)));
        pose.mulPose(Axis.ZP.rotationDegrees((float) ((time * 0.7D) % 360.0D)));
        RenderFx.sphere(hole, pose.last(), holeRadius, 24, 32, 255, 255, 255, 238);
        pose.popPose();

        // ② 引力透镜柔光：正对镜头的软环，永远贴着视界轮廓
        pose.pushPose();
        RenderFx.billboard(pose);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V,
                holeRadius * 1.18D, holeRadius * 0.46D, 96,
                ((int) (86.0F * life) << 24) | 0x9A6BFF);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V,
                holeRadius * 1.03D, holeRadius * 0.13D, 96,
                ((int) (150.0F * life) << 24) | 0xE8D8FF);
        pose.popPose();

        // 光子环
        RenderFx.circle(lines, pose.last(), holeRadius * 1.02D, 120, 255, 226, 255, (int) (220.0F * life));
        RenderFx.circle(lines, pose.last(), holeRadius * 1.12D, 120, 168, 126, 255, (int) (180.0F * life));

        // ③ 紫黑相间的三圈环：跟着积分角由慢到快转，停机时由快到慢刹住
        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(
                    (float) ArmillaryAssembly.normalize(spin * (0.42D + i * 0.19D) + i * 63.0D)));
            pose.mulPose(Axis.XP.rotationDegrees(16.0F + i * 30.0F));
            double ringRadius = holeRadius * (1.42D + i * 0.32D);
            double width = 0.10D + i * 0.03D;
            RenderFx.bandRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V,
                    ringRadius - width, ringRadius + width, 144, 6,
                    ((int) (200.0F * life) << 24) | 0xB06BFF,
                    ((int) (210.0F * life) << 24) | 0x1A0A2E);
            pose.popPose();
        }

        // ④ 吸积盘：两层反向进动的圆盘，UV 只取内切圆，方角不会跑出来
        int diskAlpha = (int) (195.0F * life);
        double diskRadius = holeRadius * 1.9D + 0.8D;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(spin * 0.78D)));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        RenderFx.disc(disk, pose.last(), diskRadius, 96, 0.5F,
                (float) Math.toRadians(ArmillaryAssembly.normalize(spin * 0.5D)), 0.0D,
                255, 172, 92, diskAlpha, diskAlpha * 3 / 4);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(-spin * 0.61D + 40.0D)));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        RenderFx.disc(disk, pose.last(), diskRadius * 1.25D, 96, 0.5F,
                (float) Math.toRadians(ArmillaryAssembly.normalize(-spin * 0.4D)), 0.08D,
                168, 132, 255, diskAlpha * 3 / 5, diskAlpha / 4);
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 终末之环启动：另一套更炫的黑洞
    // ------------------------------------------------------------------

    /** 六级轨道环的半径（× 视界半径）、倾角（度）、自转系数与主色。 */
    private static final double[] HALO_RING_RADIUS = {1.22D, 1.44D, 1.68D, 1.94D, 2.22D, 2.52D};
    private static final float[] HALO_RING_TILT = {12.0F, 38.0F, 64.0F, 92.0F, 122.0F, 150.0F};
    private static final double[] HALO_RING_SPIN = {0.62D, -0.47D, 0.86D, -0.73D, 0.38D, 1.04D};
    private static final int[] HALO_RING_COLOR = {0x8FF4FF, 0xD9A6FF, 0x6BE0FF, 0xB57BFF, 0xE8FBFF, 0x9A6BFF};
    /** 每层轨道环上跑的光点数。 */
    private static final int HALO_ORBIT_SPARKS = 9;

    /**
     * 用<b>终末之环</b>（{@code goety_revelation:halo_of_the_end}）启动时的黑洞。
     *
     * <p>和 {@link #render} 是同一套写法（原版 RenderType + 逐顶点几何、自转全部乘
     * {@code spin} 所以开机关机都能自然加减速），只是把视觉叠满：</p>
     * <ol>
     *     <li>两层视界：本体黑球 + 贴得很紧、反向自转的紫色能量壳；</li>
     *     <li>透镜光晕：五圈软光环 + 两波向外荡开的爱因斯坦环；</li>
     *     <li>四圈光子环 + <b>六层</b>倾角各不相同的紫青分段轨道环，每环上还跑着光点；</li>
     *     <li>三层吸积盘：橙白主盘、紫色次盘、垂直于盘面的极向涡旋；</li>
     *     <li>上下对穿的两条螺旋能量丝，以及从视界往外推的脉冲涟漪。</li>
     * </ol>
     * <p>用终焉回响启动时走的还是原来的 {@link #render}，两套互不影响。</p>
     */
    public static void renderEndHalo(PoseStack pose, MultiBufferSource buffers, double time, double spin,
                                     boolean stable, float collapse, boolean shuttingDown, float shutdown) {
        if (!RenderFx.usable(pose) || !Double.isFinite(time) || !Double.isFinite(spin)) {
            return;
        }
        double holeRadius = shuttingDown ? BASE_RADIUS * (1.0D - shutdown)
                : stable ? BASE_RADIUS + Math.sin(time * 0.12D) * 0.06D
                : 1.78D * collapse;
        if (!Double.isFinite(holeRadius) || holeRadius <= 0.05D) {
            return;
        }
        float life = shuttingDown ? Mth.clamp((float) (1.0D - shutdown), 0.0F, 1.0F) : 1.0F;
        // 两个周期不同的呼吸：叠在一起才像活的，单一正弦会显得机械
        double breath = 0.5D + 0.5D * Math.sin(time * 0.19D);
        double quick = 0.5D + 0.5D * Math.sin(time * 0.53D + 0.9D);

        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer hole = buffers.getBuffer(HOLE);
        VertexConsumer disk = buffers.getBuffer(DISK);
        VertexConsumer flat = buffers.getBuffer(FLAT);
        VertexConsumer glow = buffers.getBuffer(GLOW);

        // ① 两层视界：本体黑球 + 贴得很紧的紫色能量壳（壳会跟着转子反向自转）
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(-spin * 0.72D)));
        pose.mulPose(Axis.XP.rotationDegrees((float) ((time * 0.9D) % 360.0D)));
        RenderFx.sphere(hole, pose.last(), holeRadius, 24, 32, 255, 255, 255, 240);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(spin * 0.94D + 120.0D)));
        pose.mulPose(Axis.ZP.rotationDegrees((float) ((-time * 1.3D) % 360.0D)));
        RenderFx.sphere(hole, pose.last(), holeRadius * 1.035D, 12, 18, 182, 128, 255,
                (int) (62.0F * life));
        pose.popPose();

        // ② 正对镜头的透镜光晕与爱因斯坦环（父位姿里只有平移，billboard 才稳）
        pose.pushPose();
        RenderFx.billboard(pose);
        RenderFx.glowQuad(glow, pose.last(), holeRadius * (2.2D + 0.45D * breath),
                150, 110, 255, (int) (32.0F * life));
        RenderFx.glowQuad(glow, pose.last(), holeRadius * (1.75D + 0.3D * quick),
                214, 202, 255, (int) (38.0F * life));
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, holeRadius * 1.44D,
                holeRadius * 0.22D, 72, ((int) (58.0F * life) << 24) | 0x9C6BFF);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, holeRadius * 1.26D,
                holeRadius * 0.30D, 72, ((int) (80.0F * life) << 24) | 0x8A5BFF);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, holeRadius * 1.10D,
                holeRadius * 0.14D, 72, ((int) (110.0F * life) << 24) | 0xC9B0FF);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, holeRadius * 1.03D,
                holeRadius * 0.10D, 72, ((int) (180.0F * life) << 24) | 0xF6EEFF);
        // 两波向外荡开的环：从视界边缘推出去、边推边淡，像引力波
        for (int i = 0; i < 2; i++) {
            double wave = (time * 0.42D + i * 0.5D) % 1.0D;
            double radius = holeRadius * (1.05D + 1.55D * wave);
            RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, radius,
                    holeRadius * 0.15D, 72, ((int) (150.0D * (1.0D - wave) * life) << 24) | 0xB8A0FF);
        }
        pose.popPose();

        // 光子环：四圈，越外越淡
        RenderFx.circle(lines, pose.last(), holeRadius * 1.01D, 140, 255, 238, 255, (int) (240.0F * life));
        RenderFx.circle(lines, pose.last(), holeRadius * 1.09D, 140, 174, 136, 255, (int) (200.0F * life));
        RenderFx.circle(lines, pose.last(), holeRadius * 1.18D, 132, 120, 238, 255, (int) (148.0F * life));
        RenderFx.circle(lines, pose.last(), holeRadius * 1.31D, 124, 208, 172, 255, (int) (104.0F * life));

        // ③ 六层倾斜的分段轨道环：倾角各不相同，转速快慢交错，环上还跑着光点
        for (int i = 0; i < HALO_RING_RADIUS.length; i++) {
            double radius = holeRadius * HALO_RING_RADIUS[i];
            double halfWidth = 0.055D + i * 0.018D;
            int bright = ((int) ((200.0F - i * 14.0F) * life) << 24) | HALO_RING_COLOR[i];
            int dark = ((int) ((132.0F - i * 10.0F) * life) << 24) | 0x1C0B32;
            double angle = ArmillaryAssembly.normalize(spin * HALO_RING_SPIN[i] + i * 37.0D);
            pose.pushPose();
            // 先倾到自己的平面，再绕环面法线自转，花纹才会绕着环跑
            pose.mulPose(Axis.XP.rotationDegrees(HALO_RING_TILT[i]));
            pose.mulPose(Axis.YP.rotationDegrees((float) angle));
            RenderFx.bandRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                    radius - halfWidth, radius + halfWidth, 88, 4, bright, dark);
            int sparkAlpha = (int) ((168.0F - i * 12.0F) * life);
            double sparkSize = 0.10D + 0.035D * Math.sin(time * 1.4D);
            for (int k = 0; k < HALO_ORBIT_SPARKS; k++) {
                double at = k * (Math.PI * 2.0D / HALO_ORBIT_SPARKS)
                        + Math.toRadians(angle) * 0.35D;
                pose.pushPose();
                pose.translate(Math.cos(at) * radius, 0.0D, Math.sin(at) * radius);
                RenderFx.glowCross(glow, pose.last(), sparkSize, 255, 246, 255, sparkAlpha);
                pose.popPose();
            }
            pose.popPose();
        }

        // ④ 三层吸积盘：橙白主盘 + 紫色次盘 + 垂直于盘面的极向涡旋
        int diskAlpha = (int) (215.0F * life);
        double diskRadius = holeRadius * 2.1D + 0.9D;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(spin * 0.82D)));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        RenderFx.disc(disk, pose.last(), diskRadius, 96, 0.5F,
                (float) Math.toRadians(ArmillaryAssembly.normalize(spin * 0.55D)), 0.0D,
                255, 178, 96, diskAlpha, diskAlpha * 3 / 5);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(-spin * 0.66D + 40.0D)));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        RenderFx.disc(disk, pose.last(), diskRadius * 1.22D, 96, 0.5F,
                (float) Math.toRadians(ArmillaryAssembly.normalize(-spin * 0.44D)), 0.09D,
                168, 118, 255, diskAlpha * 3 / 5, diskAlpha / 4);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) ArmillaryAssembly.normalize(spin * 0.31D + 90.0D)));
        pose.mulPose(Axis.ZP.rotationDegrees(24.0F));
        RenderFx.disc(disk, pose.last(), diskRadius * 0.92D, 72, 0.5F,
                (float) Math.toRadians(ArmillaryAssembly.normalize(-spin * 0.7D)), -0.05D,
                120, 200, 255, diskAlpha / 3, 0);
        pose.popPose();

        // ⑤ 上下对穿的两条螺旋能量丝
        int threadAlpha = (int) (118.0D * life);
        RenderFx.helix(lines, pose.last(), new Vec3(0.0D, -holeRadius * 2.6D, 0.0D),
                new Vec3(0.0D, holeRadius * 2.6D, 0.0D), holeRadius * 1.25D, 5.0D, 96,
                time * 0.22D, 190, 236, 255, threadAlpha);
        RenderFx.helix(lines, pose.last(), new Vec3(0.0D, -holeRadius * 2.2D, 0.0D),
                new Vec3(0.0D, holeRadius * 2.2D, 0.0D), holeRadius * 1.5D, 4.0D, 96,
                time * 0.22D + Math.PI, 214, 150, 255, threadAlpha * 9 / 10);

        // ⑥ 从视界往外推的脉冲涟漪
        for (int i = 0; i < 3; i++) {
            double wave = (time * 0.35D + i / 3.0D) % 1.0D;
            double radius = holeRadius * (1.12D + 2.5D * wave);
            RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, radius,
                    holeRadius * 0.10D, 72, ((int) (110.0D * (1.0D - wave) * life) << 24) | 0xA88BFF);
        }
    }

    /** 停机动画：能量向内塌缩成一点，随后方块才恢复。 */
    public static void renderShutdown(PoseStack pose, MultiBufferSource buffers, float shutdown) {
        if (!RenderFx.usable(pose) || shutdown <= 0.0F) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer glow = buffers.getBuffer(GLOW);
        double wave = 1.0D - shutdown;
        RenderFx.circleXZ(lines, pose.last(), 1.0D + 12.0D * wave, 120, 190, 150, 255,
                (int) (170.0F * shutdown));
        RenderFx.circleXZ(lines, pose.last(), 0.6D + 8.0D * wave, 96, 255, 200, 140,
                (int) (140.0F * shutdown));
        RenderFx.glowCross(glow, pose.last(), 0.35D + 1.5D * wave, 255, 238, 202,
                (int) (180.0F * shutdown));
    }

    /** 启动三阶段：充能脉冲、汇聚冲击环、坍缩爆闪。 */
    public static void renderStartup(PoseStack pose, MultiBufferSource buffers, double time,
                                     boolean stable, float animation, float converge, float collapse,
                                     float flash, float charge) {
        if (!RenderFx.usable(pose) || stable || animation <= 0.0F) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer glow = buffers.getBuffer(GLOW);
        VertexConsumer beam = buffers.getBuffer(BEAM);

        if (charge > 0.02F) {
            double pulse = (animation % 40.0D) / 40.0D;
            RenderFx.circleXZ(lines, pose.last(), 1.2D + pulse * (2.6D + 5.0D * charge), 96,
                    255, 226, 160, (int) (140.0D * (1.0D - pulse) * charge));
        }
        if (converge > 0.02F) {
            double pulse = (animation % 26.0D) / 26.0D;
            RenderFx.circleXZ(lines, pose.last(), 1.0D + pulse * (2.0D + 6.0D * converge), 104,
                    150, 214, 255, (int) (185.0D * (1.0D - pulse) * converge));
            for (int i = 0; i < 3; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees((float) ((time * (2.0D + i * 0.6D) + i * 120.0D) % 360.0D)));
                pose.mulPose(Axis.XP.rotationDegrees(64.0F + i * 11.0F));
                RenderFx.circle(lines, pose.last(), 2.4D + i * 0.7D + 1.4D * converge, 96,
                        255, 210, 150, (int) (95.0D * converge));
                pose.popPose();
            }
        }
        if (collapse > 0.02F) {
            // 坍缩时向内收束的能量丝
            for (int i = 0; i < 6; i++) {
                double angle = time * 0.35D + i * Math.PI / 3.0D;
                double inner = 3.6D * (1.0D - collapse) + 0.1D;
                double outer = inner + 2.6D;
                RenderFx.line(lines, pose.last(),
                        Math.cos(angle) * outer, Math.sin(angle * 1.7D) * 0.5D, Math.sin(angle) * outer,
                        Math.cos(angle) * inner, Math.sin(angle * 1.7D) * 0.2D, Math.sin(angle) * inner,
                        200, 230, 255, 150);
            }
        }
        if (flash > 0.02F) {
            RenderFx.glowCross(glow, pose.last(), 0.8D + 3.4D * flash, 255, 255, 255,
                    (int) (200.0F * flash));
            RenderFx.circleXZ(lines, pose.last(), 1.6D + 15.0D * flash, 128, 255, 244, 214,
                    (int) (230.0D * (1.0D - flash * 0.55D)));
            // 爆闪时那一层薄薄的能量壳
            RenderFx.sphere(beam, pose.last(), 0.9D + 2.2D * flash, 12, 20, 200, 240, 255,
                    (int) (120.0D * flash));
        }
    }

    /** 上下两台注入器打进球心的能量束（黑洞口上方 / 下方，和黑洞是一体的）。 */
    public static void renderInjectors(PoseStack pose, MultiBufferSource buffers, double time,
                                       boolean stable, float progress, float charge, int phase,
                                       float shutdown) {
        if (!RenderFx.usable(pose)) {
            return;
        }
        // 顶部注入器：成型后一直悬浮，光柱起点跟着它上下走（本地坐标 = 高度 - 转子平面高度）
        double topY = ArmillaryAssembly.injectorY(time) - ArmillaryAssembly.CENTER_Y;
        // 底部注入激光：起点就在底座地面上（方块 y=0 的中心，也就是转子平面下方 8 格）。
        // 以前写在 -6（地面之上两格多），看起来像悬在半空里凭空射出。
        double bottomY = -8.0D;
        float fade = 1.0F - shutdown;
        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer glow = buffers.getBuffer(BEAM);

        if (progress > 0.03F || shutdown > 0.0F) {
            double flicker = 0.85D + 0.15D * Math.sin(time * 0.7D);
            double base = stable ? 170.0D : 40.0D + 135.0D * progress;
            int alpha = (int) (base * flicker * fade);
            float scroll = (float) ((time * 1.1D) % 1.0D);
            RenderFx.beamStrip(glow, pose.last(), new Vec3(0.0D, topY, 0.0D), Vec3.ZERO, 0.26D, 0.18D,
                    1.0F, scroll, 4.0F, 128, 220, 255, alpha);
            RenderFx.beamStrip(glow, pose.last(), new Vec3(0.0D, bottomY, 0.0D), Vec3.ZERO, 0.26D, 0.18D,
                    1.0F, -scroll, 4.0F, 255, 140, 255, alpha);
            RenderFx.beamStrip(glow, pose.last(), new Vec3(0.0D, topY, 0.0D), Vec3.ZERO, 0.08D, 0.05D,
                    1.0F, scroll * 1.8F, 6.0F, 255, 255, 240, Math.min(246, alpha + 60));
            RenderFx.beamStrip(glow, pose.last(), new Vec3(0.0D, bottomY, 0.0D), Vec3.ZERO, 0.08D, 0.05D,
                    1.0F, -scroll * 1.8F, 6.0F, 255, 255, 240, Math.min(246, alpha + 60));
        }
        if (!stable && phase >= 1 && charge > 0.05F) {
            double size = 0.9D + 1.5D * charge;
            double turns = 3.0D + 5.0D * charge;
            int alpha = (int) ((45.0D + 120.0D * charge) * fade);
            RenderFx.helix(lines, pose.last(), new Vec3(0.0D, bottomY + 1.4D, 0.0D),
                    new Vec3(0.0D, topY - 1.4D, 0.0D), size, turns, 72, time * 0.16D,
                    128, 214, 255, alpha);
            RenderFx.helix(lines, pose.last(), new Vec3(0.0D, bottomY + 1.4D, 0.0D),
                    new Vec3(0.0D, topY - 1.4D, 0.0D), size, turns, 72,
                    time * 0.16D + Math.PI, 255, 176, 255, alpha);
        }
    }
}
