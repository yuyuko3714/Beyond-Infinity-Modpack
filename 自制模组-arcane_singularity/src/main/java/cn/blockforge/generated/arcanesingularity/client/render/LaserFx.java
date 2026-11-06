package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 稳定激光渲染 —— 从方块实体渲染器里<b>独立出来</b>的一层，写法照搬 r16（玩家机上不再花屏的那一版）。
 *
 * <p>四台激光稳定器跟着转子一起转，所以炮口位置只由「转子角度 + 稳定器在蓝图里的方位角」决定：
 * 每一 tick 都按同一个角度算出炮口，激光从炮口射出、指向黑洞球心，稳定器转到哪儿激光跟到哪儿。</p>
 *
 * <p>画法（和黑洞那一层完全一致，层全部来自 {@link FxLayers}：原版 Core Shader +
 * 原版顶点格式，不碰实体着色器）：</p>
 * <ul>
 *     <li>{@code RenderFx.emissive(laser_beam.png)}：三层锥形光束，外圈冷光、
 *         主束、白热内芯，越靠近球心越细；</li>
 *     <li>{@code RenderFx.emissive(flat_white.png)}：一条纯色亮芯，不靠贴图透明度也看得见；</li>
 *     <li>{@code RenderFx.emissive(soft_glow.png)}：炮口闪光与沿光束飞行的高能粒子；</li>
 *     <li>{@code FxLayers.LINES}：绕光束缠绕的双螺旋轨迹。</li>
 * </ul>
 */
public final class LaserFx {
    /** 炮口相对稳定器方块中心朝内收的距离（方块表面）。 */
    private static final double MUZZLE_INSET = 0.58D;
    private static final double TAU = Math.PI * 2.0D;

    private static final RenderType LASER_LAYER = RenderFx.emissive(RenderFx.LASER);
    private static final RenderType GLOW_LAYER = RenderFx.emissive(RenderFx.GLOW);
    private static final RenderType FLAT_LAYER = RenderFx.emissive(RenderFx.FLAT);

    private LaserFx() {
    }

    /**
     * 画出四台稳定器的激光（调用时位姿已经平移到转子平面中心）。
     *
     * @param rotor 转子当前角度（度），与实体碰撞、方块模型用的是同一个角度
     * @param time  游戏时间（tick，含插值）
     * @param power 激光强度 0..1，停机时随进度衰减
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, double rotor, double time, float power) {
        if (!RenderFx.usable(pose) || !Double.isFinite(rotor) || power <= 0.02F) {
            return;
        }
        VertexConsumer laser = buffers.getBuffer(LASER_LAYER);
        VertexConsumer glow = buffers.getBuffer(GLOW_LAYER);
        VertexConsumer flat = buffers.getBuffer(FLAT_LAYER);
        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());

        // 四束激光在球心撞在一起的那一点高光
        RenderFx.glowCross(glow, pose.last(), 0.42D + 0.16D * (float) (0.5D + 0.5D * Math.sin(time * 0.42D)),
                255, 246, 216, (int) (130.0F * power));

        for (ArmillaryAssembly.RotorPart pod : ArmillaryAssembly.pods()) {
            renderBeam(pose, laser, glow, flat, lines, pod, rotor, time, power);
        }
    }

    /** 一台稳定器的激光：炮口闪光 + 锥形粗光束 + 螺旋粒子流 + 向球心高速流动的亮核。 */
    private static void renderBeam(PoseStack pose, VertexConsumer laser, VertexConsumer glow,
                                   VertexConsumer flat, VertexConsumer lines,
                                   ArmillaryAssembly.RotorPart pod, double rotor, double time, float power) {
        double radians = Math.toRadians(pod.angleDeg() + rotor);
        Vec3 inward = new Vec3(-Math.cos(radians), 0.0D, -Math.sin(radians));
        Vec3 center = new Vec3(Math.cos(radians) * pod.radius(), 0.0D, Math.sin(radians) * pod.radius());
        Vec3 muzzle = center.add(inward.scale(MUZZLE_INSET));
        Vec3 target = Vec3.ZERO;

        double flicker = 0.80D + 0.20D * Math.sin(time * 0.85D + pod.podIndex() * 1.7D);
        int alpha = Mth.clamp((int) (255.0D * power * flicker), 0, 255);
        // 贴图沿光束往黑洞方向流动：数值越大，花纹跑得越快
        float scroll = (float) ((time * 1.35D) % 1.0D);

        // 外层冷光 → 主光束 → 白热内芯（贴图在流）
        RenderFx.beamStrip(laser, pose.last(), muzzle, target, 0.46D, 0.34D, 1.0F, scroll,
                2.6F, 96, 180, 255, alpha * 2 / 3);
        RenderFx.beamStrip(laser, pose.last(), muzzle, target, 0.28D, 0.17D, 1.0F, scroll * 1.7F,
                5.2F, 148, 222, 255, alpha);
        RenderFx.beamStrip(laser, pose.last(), muzzle, target, 0.13D, 0.07D, 1.0F, scroll * 2.4F,
                8.0F, 255, 255, 255, alpha);
        // 平涂亮芯：纯顶点色的几何，贴图半透明也压不住它
        RenderFx.beamStripFlat(flat, pose.last(), muzzle, target, 0.085D, 0.04D,
                226, 246, 255, Mth.clamp((int) (215.0F * power), 0, 255));

        // 炮口闪光：激光的起点明显长在发射器上
        RenderFx.glowCross(glow, pose.last(), 0.50D + 0.09D * Math.sin(time * 0.5D + pod.podIndex()),
                255, 240, 202, (int) (205.0F * power));

        // 绕光束缠绕的双螺旋，往黑洞方向跑
        RenderFx.helix(lines, pose.last(), muzzle, target, 0.30D, 2.6D, 24,
                (time * 1.6D + pod.podIndex() * 1.1D) % TAU,
                146, 226, 255, (int) (140.0F * power));
        RenderFx.helix(lines, pose.last(), muzzle, target, 0.22D, 2.6D, 24,
                (-time * 1.1D + pod.podIndex() * 2.3D) % TAU,
                255, 238, 194, (int) (105.0F * power));

        // 高能粒子流：一串亮核沿着光束从发射器高速冲向黑洞
        for (int i = 0; i < 3; i++) {
            double travel = (time * 0.055D + i / 3.0D + pod.podIndex() * 0.17D) % 1.0D;
            Vec3 point = muzzle.add(target.subtract(muzzle).scale(travel));
            double size = 0.10D + 0.15D * travel;
            pose.pushPose();
            pose.translate(point.x, point.y, point.z);
            RenderFx.glowCross(glow, pose.last(), size, 255, 248, 220, (int) (195.0F * power));
            pose.popPose();
            double tail = Math.max(0.0D, travel - 0.11D);
            Vec3 trail = muzzle.add(target.subtract(muzzle).scale(tail));
            RenderFx.beamStripFlat(flat, pose.last(), trail, point, 0.06D + 0.03D * travel,
                    0.02D, 255, 250, 226, (int) (150.0F * power));
        }
    }
}
