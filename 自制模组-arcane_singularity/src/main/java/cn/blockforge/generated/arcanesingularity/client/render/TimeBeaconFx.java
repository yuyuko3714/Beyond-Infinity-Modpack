package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.TimeBeaconAssembly;
import cn.blockforge.generated.arcanesingularity.time.TimeMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 时间信标的光影 —— 从方块实体渲染器里<b>独立出来</b>的一层，写法与 {@link BlackHoleFx} /
 * {@link LaserFx} / {@link StargateFx} 完全一致：几何用 {@link RenderFx} 一个顶点一个顶点写，
 * 顶点只写「位置 + 颜色 + UV」（原版 {@code POSITION_COLOR_TEX}），贴图交给 {@link FxLayers}
 * 的层自己绑定，线框用 {@link FxLayers#LINES}。不碰实体着色器、不碰方块图集。</p>
 *
 * <p>三层视觉：</p>
 * <ol>
 *     <li><b>能量汇聚环</b>——炮管外两圈环方块上的能量带。转速越快越亮，颜色从冷白青一路烧成
 *         该模式对应的激光色（加速青、减速紫、正常金、停止红），这是「由慢到快、同时变色」的部分；</li>
 *     <li><b>炮口聚能</b>——充能后半段，能量从四面八方往枪口收，光球随进度涨大；</li>
 *     <li><b>激光柱</b>——一道极粗的高能光柱冲向天际：外辉光、主束、白热亮芯、双螺旋、上升光点、
 *         炮口冲击环与高空扩散环。</li>
 * </ol>
 *
 * <p>所有角度都用控制器给的累计转角，所以开机由慢到快、关机由快到慢的过程中，每一层都是连续的，
 * 不会跳帧。</p>
 */
public final class TimeBeaconFx {
    private static final RenderType LASER_LAYER = RenderFx.emissive(RenderFx.LASER);
    private static final RenderType BEAM_LAYER = RenderFx.emissive(RenderFx.BEAM);
    private static final RenderType GLOW_LAYER = RenderFx.emissive(RenderFx.GLOW);
    private static final RenderType FLAT_LAYER = RenderFx.emissive(RenderFx.FLAT);

    /** 主炮口（最粗的那截炮管顶面）：第一段激光从这里射出。 */
    public static final double MUZZLE_Y = ArcaneStructure.BEACON_MUZZLE_Y;
    /** 顶部两格时序炮管的底面：第一段激光打到这里，开始二次聚能。 */
    public static final double TOP_GAP_Y = ArcaneStructure.BEACON_TOP_GAP_Y;
    /** 顶部时序炮管的顶面：二次聚能完成后，第二段激光从这里射向天空。 */
    public static final double TOP_MUZZLE_Y = ArcaneStructure.BEACON_TOP_MUZZLE_Y;
    /** 激光柱长度：足够一路捅到天上，末端渐隐。 */
    private static final double BEAM_LENGTH = 224.0D;
    /** 两圈汇聚环的中心半径（直接取自蓝图，环改半径时这里自动跟着变）。 */
    private static final double[] RING_RADIUS = {
            averageRadius(0), averageRadius(1)
    };
    /** 两圈汇聚环的高度（环方块中心）。 */
    private static final double[] RING_Y = {
            ArcaneStructure.BEACON_RING_A_Y, ArcaneStructure.BEACON_RING_B_Y
    };
    /** 汇聚环上放光点的个数。 */
    private static final int RING_SPARKS = 12;

    /** 环还没转起来时的冷白青色。 */
    private static final int COOL_COLOR = 0xBFEFFF;

    private TimeBeaconFx() {
    }

    /** 蓝图里某一圈汇聚环的平均半径。 */
    private static double averageRadius(int ring) {
        List<TimeBeaconAssembly.RingPart> parts = TimeBeaconAssembly.ringParts(ring);
        if (parts.isEmpty()) {
            return 6.0D;
        }
        double sum = 0.0D;
        for (TimeBeaconAssembly.RingPart part : parts) {
            sum += part.radius();
        }
        return sum / parts.size();
    }

    /** 四种模式各自的激光主色。 */
    public static int colorOf(int modeId) {
        TimeMode mode = TimeMode.byId(modeId);
        return switch (mode) {
            case ACCELERATE -> 0x5FE6FF;
            case SLOW -> 0xB076FF;
            case STOP -> 0xFF4E6B;
            default -> 0xFFC46B;
        };
    }

    /**
     * 画出时间信标的全部光影（调用时位姿已经平移到控制器方块中心）。
     *
     * @param time      游戏时间（tick，含插值）
     * @param modeId    当前时间模式
     * @param ringAngle 两圈汇聚环的当前角度（度）
     * @param ramp      转速占比 0..1（决定环的亮度与颜色）
     * @param charge    炮口聚能进度 0..1
     * @param beam      激光强度 0..1
     * @param running   激光是否已经射出
     * @param shutdown  关机进度 0..1
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, double time, int modeId,
                              double[] ringAngle, int topRingIndex, float ramp, float charge, float beam,
                              boolean running, float shutdown, double barrelBob) {
        if (!RenderFx.usable(pose) || !Double.isFinite(time)) {
            return;
        }
        // 顶部两格时序炮管整体在上下浮动：两段激光的端点跟着它走，光柱始终贴着炮口
        double topGapY = TOP_GAP_Y + barrelBob;
        double topMuzzleY = TOP_MUZZLE_Y + barrelBob;
        int color = colorOf(modeId);
        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer flat = buffers.getBuffer(FLAT_LAYER);
        VertexConsumer glow = buffers.getBuffer(GLOW_LAYER);
        VertexConsumer laser = buffers.getBuffer(LASER_LAYER);
        VertexConsumer beamLayer = buffers.getBuffer(BEAM_LAYER);

        float fade = Mth.clamp(1.0F - shutdown, 0.0F, 1.0F);
        renderConvergenceRings(pose, lines, flat, glow, time, color, ringAngle, ramp, fade);
        // 二段式：先把主炮管的能量打到顶部时序炮管，二次聚能之后才射向天空
        float first = Mth.clamp(beam / 0.45F, 0.0F, 1.0F);
        float second = Mth.clamp((beam - 0.40F) / 0.60F, 0.0F, 1.0F);
        if (charge > 0.02F) {
            renderMuzzleCharge(pose, lines, flat, glow, time, color, charge, fade);
        }
        if (first > 0.02F) {
            renderFirstStage(pose, flat, glow, laser, beamLayer, time, color, first, fade, topGapY);
        }
        if (second > 0.02F) {
            double topAngle = topRingIndex >= 0 && topRingIndex < ringAngle.length ? ringAngle[topRingIndex] : 0.0D;
            renderTopCharge(pose, lines, flat, glow, time, color, second, topAngle, fade, topMuzzleY);
            renderSecondStage(pose, lines, flat, glow, laser, beamLayer, time, color, second, running, fade,
                    topMuzzleY);
        }
    }

    // ------------------------------------------------------------------
    // ① 能量汇聚环
    // ------------------------------------------------------------------

    private static void renderConvergenceRings(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                               VertexConsumer glow, double time, int color,
                                               double[] ringAngle, float ramp, float fade) {
        int bright = lerpColor(COOL_COLOR, color, ramp);
        int dark = mix(0x120A24, bright, 0.18F);
        float power = (0.32F + 0.68F * ramp) * fade;

        for (int i = 0; i < 2; i++) {
            double angle = i == 0 ? ringAngle[0] : ringAngle[1];
            double y = RING_Y[i];
            double radius = RING_RADIUS[i];
            if (!Double.isFinite(angle) || !Double.isFinite(y)) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.0D, y, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees((float) angle));

            // 环方块上的能量带：分段换色，转起来一眼能看出在动
            RenderFx.bandRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                    radius - 0.46D, radius + 0.46D, 96, 4,
                    rgba((int) (215.0F * power), bright),
                    rgba((int) (150.0F * power), dark));
            // 内外两道发光边
            RenderFx.glowRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                    radius + 0.62D, 0.55D, 72, rgba((int) (95.0F * power), bright));
            RenderFx.glowRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                    radius - 0.62D, 0.40D, 72, rgba((int) (120.0F * power), bright));

            // 每一格环方块上的光点：转得越快越亮，像一圈被推着走的灯
            double sparkSize = 0.13D + 0.05D * Math.sin(time * 1.7D + i * 2.0D);
            for (int k = 0; k < RING_SPARKS; k++) {
                double at = k * (Math.PI * 2.0D / RING_SPARKS);
                pose.pushPose();
                pose.translate(Math.cos(at) * radius, 0.0D, Math.sin(at) * radius);
                RenderFx.glowCross(glow, pose.last(), sparkSize, 255, 250, 236,
                        (int) (200.0F * power));
                pose.popPose();
            }
            // 环面上那层很淡的辉光，让整圈环在远处也带着光
            RenderFx.glowRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                    radius + 1.1D, 1.5D, 56, rgba((int) (38.0F * power), bright));
            pose.popPose();
        }

        // 两环之间拧成一股的能量丝：转得越快拧得越紧
        double yLow = RING_Y[0];
        double yHigh = RING_Y[1];
        double twist = 0.35D + 1.9D * ramp;
        int threadAlpha = (int) ((70.0F + 110.0F * ramp) * fade);
        RenderFx.helix(lines, pose.last(), new Vec3(0.0D, yLow, 0.0D), new Vec3(0.0D, yHigh, 0.0D),
                3.05D, twist, 64, ringAngle[0] * 0.12D, bright >> 16 & 0xFF, bright >> 8 & 0xFF,
                bright & 0xFF, threadAlpha);
        RenderFx.helix(lines, pose.last(), new Vec3(0.0D, yLow, 0.0D), new Vec3(0.0D, yHigh, 0.0D),
                2.35D, twist * 1.4D, 64, -ringAngle[1] * 0.12D, 255, 250, 236, threadAlpha * 2 / 3);

        // 底座那一圈缓慢扩散的能量涟漪（只在塔基高度，不干扰结构识别）
        for (int i = 0; i < 2; i++) {
            double wave = (time * 0.22D + i * 0.5D) % 1.0D;
            RenderFx.circleXZ(lines, pose.last(), 2.0D + 9.0D * wave, 72,
                    bright >> 16 & 0xFF, bright >> 8 & 0xFF, bright & 0xFF,
                    (int) (90.0D * (1.0D - wave) * power));
        }
    }

    // ------------------------------------------------------------------
    // ② 炮口聚能
    // ------------------------------------------------------------------

    private static void renderMuzzleCharge(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                           VertexConsumer glow, double time, int color, float charge,
                                           float fade) {
        float p = Mth.clamp(charge, 0.0F, 1.0F);
        int bright = lerpColor(COOL_COLOR, color, p);
        int alpha = (int) (230.0F * p * fade);

        pose.pushPose();
        pose.translate(0.0D, MUZZLE_Y, 0.0D);

        // 光球：越充越大，最后一刻猛地一亮
        double core = 0.35D + 1.55D * p * p + 0.10D * Math.sin(time * 0.6D);
        RenderFx.glowCross(glow, pose.last(), core, 255, 255, 255, alpha);
        pose.pushPose();
        RenderFx.billboard(pose);
        RenderFx.glowQuad(glow, pose.last(), core * 1.9D,
                bright >> 16 & 0xFF, bright >> 8 & 0xFF, bright & 0xFF, alpha / 3);
        pose.popPose();

        // 从四周往枪口收的能量丝
        double reach = 3.6D * (1.0D - p) + 0.45D;
        int threadAlpha = (int) (170.0F * p * fade);
        for (int i = 0; i < 8; i++) {
            double angle = time * 0.28D + i * (Math.PI * 2.0D / 8.0D);
            double inner = 0.12D;
            double height = 0.9D * (1.0D - p) + 0.1D;
            RenderFx.line(lines, pose.last(),
                    Math.cos(angle) * reach, height, Math.sin(angle) * reach,
                    Math.cos(angle) * inner, 0.0D, Math.sin(angle) * inner,
                    bright >> 16 & 0xFF, bright >> 8 & 0xFF, bright & 0xFF, threadAlpha);
        }
        // 一圈圈往内塌的环
        for (int i = 0; i < 3; i++) {
            double ring = (time * 0.9D + i / 3.0D) % 1.0D;
            double radius = reach * (1.0D - ring * 0.75D);
            RenderFx.circleXZ(lines, pose.last(), radius, 64,
                    bright >> 16 & 0xFF, bright >> 8 & 0xFF, bright & 0xFF,
                    (int) (150.0D * ring * p * fade));
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // ③ 第一段：主炮管 → 顶部时序炮管
    // ------------------------------------------------------------------

    /**
     * 第一段激光：从最粗的炮管中心笔直打到顶部两格时序炮管的底部。
     *
     * <p>这一段只走 {@link #TOP_GAP_Y} 的高度，所以画成一根短而粗的光柱，层数与主光柱一致
     * （外辉光 / 主束 / 白热亮芯 / 内高光），看起来就是主炮在「上弹」。</p>
     */
    private static void renderFirstStage(PoseStack pose, VertexConsumer flat, VertexConsumer glow,
                                         VertexConsumer laser, VertexConsumer beamLayer,
                                         double time, int color, float first, float fade, double topGapY) {
        float power = Mth.clamp(first, 0.0F, 1.0F) * fade;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        double top = Mth.lerp(power, MUZZLE_Y, topGapY);
        Vec3 from = new Vec3(0.0D, MUZZLE_Y, 0.0D);
        Vec3 to = new Vec3(0.0D, top, 0.0D);
        int alpha = (int) (240.0F * power);
        float scroll = (float) ((time * 1.4D) % 1.0F);
        double flicker = 0.9D + 0.1D * Math.sin(time * 1.7D);

        RenderFx.beamStrip(laser, pose.last(), from, to, 1.55D * flicker, 1.55D * flicker, 1.0F,
                scroll, 18.0F, red, green, blue, alpha / 3);
        RenderFx.beamStrip(laser, pose.last(), from, to, 0.95D, 0.95D, 1.0F,
                scroll * 1.8F, 30.0F, red, green, blue, alpha * 3 / 4);
        RenderFx.beamStripFlat(flat, pose.last(), from, to, 0.34D, 0.34D, 250, 252, 255, alpha);
        RenderFx.beamStrip(beamLayer, pose.last(), from, to, 0.58D, 0.58D, 1.0F,
                scroll * 2.4F, 42.0F, 255, 255, 255, alpha / 2);

        // 打在顶部时序炮管底部的那团光
        pose.pushPose();
        pose.translate(0.0D, topGapY, 0.0D);
        RenderFx.glowCross(glow, pose.last(), 0.42D + 0.18D * power, 255, 252, 244,
                (int) (220.0F * power));
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // ④ 二次聚能：顶部小型聚能环 + 四块反向晶体的能量带
    // ------------------------------------------------------------------

    private static void renderTopCharge(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                        VertexConsumer glow, double time, int color, float second,
                                        double topAngle, float fade, double topMuzzleY) {
        float p = Mth.clamp(second, 0.0F, 1.0F);
        int bright = lerpColor(COOL_COLOR, color, p);
        int alpha = (int) (235.0F * p * fade);
        double y = topMuzzleY;

        // 顶部炮口二次聚能的光球
        pose.pushPose();
        pose.translate(0.0D, y, 0.0D);
        double core = 0.30D + 1.10D * p * p + 0.08D * Math.sin(time * 0.9D);
        RenderFx.glowCross(glow, pose.last(), core, 255, 255, 255, alpha);
        pose.pushPose();
        RenderFx.billboard(pose);
        float near = RenderFx.nearFade(pose.last(), 2.0D, 7.0D);
        RenderFx.glowQuad(glow, pose.last(), core * 1.8D,
                bright >> 16 & 0xFF, bright >> 8 & 0xFF, bright & 0xFF, (int) (alpha / 3 * near));
        pose.popPose();
        pose.popPose();

        // 小型聚能环的能量带（环方块自己由渲染器画，这里只补光）
        double radius = 2.45D;
        pose.pushPose();
        pose.translate(0.0D, ArcaneStructure.BEACON_TOP_RING_Y, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees((float) topAngle));
        RenderFx.bandRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                radius - 0.40D, radius + 0.40D, 64, 4,
                rgba((int) (220.0F * p * fade), bright), rgba((int) (130.0F * p * fade), 0x120A24));
        pose.pushPose();
        pose.translate(0.0D, 0.03D, 0.0D);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                radius + 0.62D, 0.45D, 56, rgba((int) (110.0F * p * fade), bright));
        pose.popPose();
        pose.popPose();

        // 四块晶体位置上各甩一束光，颜色跟着模式走
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 0.5D * i + Math.toRadians(topAngle) * -1.0D;
            pose.pushPose();
            pose.translate(Math.cos(angle) * 2.0D, ArcaneStructure.BEACON_TOP_RING_Y + 1.0D,
                    Math.sin(angle) * 2.0D);
            RenderFx.glowCross(glow, pose.last(), 0.24D + 0.16D * p, 255, 252, 244,
                    (int) (200.0F * p * fade));
            pose.popPose();
        }

        // 光球往外荡的环
        for (int i = 0; i < 2; i++) {
            double wave = (time * 0.7D + i * 0.5D) % 1.0D;
            pose.pushPose();
            pose.translate(0.0D, y + 0.05D, 0.0D);
            RenderFx.circleXZ(lines, pose.last(), 0.5D + 2.6D * wave, 48,
                    bright >> 16 & 0xFF, bright >> 8 & 0xFF, bright & 0xFF,
                    (int) (150.0D * (1.0D - wave) * p * fade));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------
    // ⑤ 第二段：顶部炮口 → 天空
    // ------------------------------------------------------------------

    private static void renderSecondStage(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                          VertexConsumer glow, VertexConsumer laser, VertexConsumer beamLayer,
                                          double time, int color, float second, boolean running, float fade,
                                          double topMuzzleY) {
        float power = Mth.clamp(second, 0.0F, 1.0F) * fade;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        Vec3 base = new Vec3(0.0D, topMuzzleY, 0.0D);
        double flicker = 0.88D + 0.12D * Math.sin(time * 0.9D);
        float scroll = (float) ((time * 0.55D) % 1.0F);

        // 整根光柱分三段写，越往上越淡，末端化进天空里
        for (int segment = 0; segment < 3; segment++) {
            double t0 = segment / 3.0D;
            double t1 = (segment + 1) / 3.0D;
            Vec3 from = base.add(0.0D, BEAM_LENGTH * t0, 0.0D);
            Vec3 to = base.add(0.0D, BEAM_LENGTH * t1, 0.0D);
            int alpha = (int) (245.0F * power * flicker * (1.0D - segment * 0.30D));
            RenderFx.beamStrip(laser, pose.last(), from, to, 3.55D, 3.55D, 1.0F,
                    scroll * (1.0F + segment * 0.35F), 16.0F,
                    red, green, blue, alpha / 3);
            RenderFx.beamStrip(laser, pose.last(), from, to, 2.05D, 2.05D, 1.0F,
                    scroll * (1.6F + segment * 0.4F), 26.0F,
                    red, green, blue, alpha * 3 / 4);
            RenderFx.beamStripFlat(flat, pose.last(), from, to, 0.80D, 0.80D,
                    250, 252, 255, alpha);
            RenderFx.beamStrip(beamLayer, pose.last(), from, to, 1.30D, 1.30D, 1.0F,
                    scroll * 2.2F, 40.0F, 255, 255, 255, alpha / 2);
        }

        double spinPhase = time * 0.30D;
        RenderFx.helix(lines, pose.last(), base, base.add(0.0D, BEAM_LENGTH * 0.9D, 0.0D),
                3.35D, 15.0D, 128, spinPhase, red, green, blue, (int) (135.0F * power));
        RenderFx.helix(lines, pose.last(), base, base.add(0.0D, BEAM_LENGTH * 0.9D, 0.0D),
                2.75D, 15.0D, 128, -spinPhase * 1.3D, 255, 250, 236, (int) (95.0F * power));

        for (int i = 0; i < 7; i++) {
            double travel = (time * 0.022D + i / 7.0D) % 1.0D;
            double height = BEAM_LENGTH * travel;
            double spiral = travel * 26.0D + i;
            double radius = 1.4D + 0.6D * Math.sin(spiral);
            double half = 0.16D + 0.22D * travel;
            pose.pushPose();
            pose.translate(Math.cos(spiral) * radius, topMuzzleY + height, Math.sin(spiral) * radius);
            RenderFx.glowCross(glow, pose.last(), half, 255, 252, 244,
                    (int) (215.0F * power * (1.0D - travel * 0.55D)));
            pose.popPose();
        }

        if (running) {
            pose.pushPose();
            pose.translate(0.0D, topMuzzleY + 0.15D, 0.0D);
            for (int i = 0; i < 3; i++) {
                double wave = (time * 0.45D + i / 3.0D) % 1.0D;
                RenderFx.circleXZ(lines, pose.last(), 1.6D + 7.5D * wave, 96,
                        red, green, blue, (int) (185.0D * (1.0D - wave) * power));
                RenderFx.glowRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V,
                        1.6D + 7.5D * wave, 0.85D, 72,
                        rgba((int) (110.0D * (1.0D - wave) * power), color));
            }
            pose.popPose();
        }

        for (int i = 0; i < 3; i++) {
            double height = topMuzzleY + 46.0D + i * 40.0D;
            double pulse = (time * 0.19D + i * 0.31D) % 1.0D;
            double radius = 3.2D + 2.6D * pulse;
            pose.pushPose();
            pose.translate(0.0D, height, 0.0D);
            RenderFx.glowRing(flat, pose.last(), RenderFx.XZ_U, RenderFx.XZ_V, radius, 0.9D, 64,
                    rgba((int) (95.0D * (1.0D - pulse * 0.7D) * power), color));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------
    // 颜色工具
    // ------------------------------------------------------------------

    private static int rgba(int alpha, int rgb) {
        return ((Mth.clamp(alpha, 0, 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        int red = (int) Mth.lerp(clamped, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int green = (int) Mth.lerp(clamped, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int blue = (int) Mth.lerp(clamped, from & 0xFF, to & 0xFF);
        return (red << 16) | (green << 8) | blue;
    }

    private static int mix(int base, int bright, float amount) {
        return lerpColor(base, bright, amount);
    }
}
