package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.structure.ArmillaryAssembly;
import cn.blockforge.generated.arcanesingularity.structure.StargateRingAssembly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/**
 * 星门渲染 —— <b>独立出来</b>的一层，写法类比 {@link BlackHoleFx} / {@link LaserFx}
 * （不是旧版星门那一版的写法，而是「照着黑洞和激光的方式」重新写的）。
 *
 * <p>画法只有三句话：几何用 {@link RenderFx} 一个顶点一个顶点写、顶点只写
 * 「位置 + 颜色 + UV」（原版 {@code POSITION_COLOR_TEX}）、贴图全部交给
 * {@link FxLayers} 的层自己绑定。层跑的是原版 Core Shader {@code position_color_tex}，
 * 不碰实体着色器、不碰光照采样器、不碰方块图集。</p>
 *
 * <p>整座星门的视觉只剩三层，全部跟着同一个 {@code ringAngle}（门环的累计转角）走，所以开机
 * 由慢到快、关机由快到慢时，每一层的转速变化都是连着的，不会突然跳一帧：</p>
 * <ol>
 *     <li><b>门框这一圈</b>——{@code stargate_rim} 贴图按半径铺成的环带、内缘柔光、分段色环、
 *         刻度线和八颗符文珠，贴着门框内缘自转；</li>
 *     <li><b>传送膜</b>——<b>一张</b>完整的圆形膜，{@link RenderFx#discMappedSoft} 把贴图的内切圆
 *         一一对应地铺满整张圆盘，整张膜就绕着门心转。膜的最外圈从贴图到几何都淡成完全透明，
 *         所以边界是化开的，没有白边、也没有硬圆边。以前那种把 UV 按角度旋转的画法会把星系
 *         花纹扯成几条放射状的粗带，这一版彻底不用它；</li>
 *     <li><b>开合冲击</b>——开门瞬间的白热核心与两圈扩散的冲击波，门开稳之后自然退干净。</li>
 * </ol>
 *
 * <p>膜只有一层主膜 + 一层更淡的反向视差膜，两层都是完整的圆盘、外缘都淡出，所以从任何角度看
 * 都只是一张在转的圆形膜，不会出现「几条粗线」，也不会在门心留下一圈不透明的白边。</p>
 */
public final class StargateFx {
    /**
     * 传送膜的半径：门框内缘约 6.8，留一点缝，避免和门环方块穿插。
     * 传送判定读的是同一个常量，所以整张膜覆盖的地方都能传过去。
     */
    private static final double PORTAL_RADIUS = StargateRingAssembly.PORTAL_RADIUS;
    /** 门框等离子环的中心半径与内外缘。 */
    private static final double RIM_RADIUS = 6.60D;
    private static final double RIM_INNER = 6.30D;
    private static final double RIM_OUTER = 6.88D;
    /** 符文珠的颗数。 */
    private static final int BEADS = 8;
    /** 门框内缘刻度的道数。 */
    private static final int TICKS = 24;

    private static final RenderType PORTAL_LAYER = RenderFx.emissive(RenderFx.PORTAL);
    private static final RenderType RIM_LAYER = RenderFx.emissive(RenderFx.GATE_RIM);
    private static final RenderType FLAT_LAYER = RenderFx.emissive(RenderFx.FLAT);
    private static final RenderType SPARK_LAYER = RenderFx.emissive(RenderFx.GLOW);

    private StargateFx() {
    }

    /**
     * 画出星门的全部光影（调用时位姿已经平移到门心、并转到了门的朝向）。
     *
     * @param time      游戏时间（tick，含插值）
     * @param open      门是否处于打开状态
     * @param progress  开门进度 0..1（关门时反向）
     * @param ringAngle 门环累计转角（度），传送膜、符文珠、等离子环都跟它走
     * @param yaw       门朝向绕 Y 轴的转角（度），柔光面片要先把它转回去才能正对镜头
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, double time, boolean open,
                              float progress, double ringAngle, float yaw) {
        if (!RenderFx.usable(pose) || !Double.isFinite(time) || !Double.isFinite(ringAngle)) {
            return;
        }
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        double ease = clamped * clamped * (3.0D - 2.0D * clamped);

        VertexConsumer lines = buffers.getBuffer(RenderFx.lines());
        VertexConsumer flat = buffers.getBuffer(FLAT_LAYER);
        VertexConsumer spark = buffers.getBuffer(SPARK_LAYER);
        VertexConsumer rim = buffers.getBuffer(RIM_LAYER);

        // 门框这一圈：无论开关都在，开门时整圈点旺
        renderFrame(pose, lines, flat, spark, rim, time, open, ease, ringAngle, yaw);

        if (ease <= 0.004D) {
            // 关着：门心只留一点待机能量
            renderIdle(pose, lines, flat, spark, time, yaw);
            return;
        }

        renderMembrane(pose, buffers.getBuffer(PORTAL_LAYER), spark, time, ease, ringAngle, yaw);
        renderBurst(pose, lines, flat, spark, time, ease, yaw);
    }

    // ------------------------------------------------------------------
    // ① 门框：等离子环、内缘柔光、刻度、符文珠
    // ------------------------------------------------------------------

    private static void renderFrame(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                    VertexConsumer spark, VertexConsumer rim, double time, boolean open,
                                    double ease, double ringAngle, float yaw) {
        double spin = ArmillaryAssembly.normalize(ringAngle);
        double power = open ? 1.0D : 0.45D + 0.35D * ease;
        int pulse = (int) (22.0D * Math.sin(time * 0.16D) * power);
        int segments = open ? 96 : 72;

        // 贴着门框内缘的等离子环：贴图按半径映射，沿圆周自转，转速跟着门环一起涨
        double uvSpin = Math.toRadians(ArmillaryAssembly.normalize(spin + time * 22.0D * power));
        RenderFx.radialBand(rim, pose.last(), RenderFx.XY_U, RenderFx.XY_V, RIM_INNER, RIM_OUTER, segments,
                0.24F, 0.47F, uvSpin,
                rgba((int) (225.0D * power) + pulse, 0xDCEEFF),
                rgba((int) (70.0D * power), 0x9A6BFF));

        // 内缘柔光：纯顶点色，永远是一圈软光，不会因为贴图透明而看不见
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, RIM_RADIUS - 0.30D, 0.46D, 48,
                rgba((int) (120.0D * power) + pulse, 0xB79BFF));

        // 分段色环：跟着门环角转，转速一变，分段就会明显地拉开 / 挤紧
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees((float) spin));
        RenderFx.bandRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V,
                RIM_RADIUS - 1.02D, RIM_RADIUS - 0.80D, 96, 4,
                rgba((int) (190.0D * power), 0x8EE6FF),
                rgba((int) (120.0D * power), 0x2B0F5A));
        pose.popPose();

        // 内缘刻度：一圈细密的辐条，转起来像齿轮
        for (int i = 0; i < TICKS; i++) {
            double angle = Math.toRadians(spin * 0.5D + i * (360.0D / TICKS));
            double inner = RIM_RADIUS - 0.62D;
            double outer = RIM_RADIUS - 0.44D;
            RenderFx.line(lines, pose.last(), Math.cos(angle) * inner, Math.sin(angle) * inner, 0.0D,
                    Math.cos(angle) * outer, Math.sin(angle) * outer, 0.0D,
                    180, 210, 255, (int) (60.0D * power));
        }

        // 八颗符文珠：柔光球 + 线框，绕门心公转，越转越快
        int beadAlpha = (int) (200.0D * power) + pulse;
        for (int i = 0; i < BEADS; i++) {
            double angle = Math.toRadians(spin + i * (360.0D / BEADS));
            double bx = Math.cos(angle) * RIM_RADIUS;
            double by = Math.sin(angle) * RIM_RADIUS;
            pose.pushPose();
            pose.translate(bx, by, 0.0D);
            billboardGlow(pose, spark, yaw, 0.34D + 0.05D * Math.sin(time * 0.31D + i * 1.7D),
                    236, 242, 255, Mth.clamp(beadAlpha, 0, 255));
            RenderFx.box(lines, pose.last(), -0.15D, -0.15D, -0.15D, 0.15D, 0.15D, 0.15D,
                    255, 240, 190, Mth.clamp(beadAlpha, 0, 255));
            pose.popPose();
        }

        // 门外那一圈很淡的紫光：让整扇门在远处也带着光
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, RIM_RADIUS + 0.42D, 0.90D, 40,
                rgba((int) (46.0D * power), 0x7A4FE0));
    }

    /** 关着的门：门心一团缓慢呼吸的符文光。 */
    private static void renderIdle(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                   VertexConsumer spark, double time, float yaw) {
        double breathe = 0.5D + 0.5D * Math.sin(time * 0.11D);
        RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, 1.15D + 0.25D * breathe,
                0.42D, 48, rgba((int) (60.0D + 45.0D * breathe), 0x8E7BFF));
        billboardGlow(pose, spark, yaw, 0.34D + 0.10D * breathe, 200, 214, 255,
                (int) (80.0D + 50.0D * breathe));

        // 一圈慢慢转的符文刻度：像表盘，不是几根光秃秃的线
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees((float) ((time * 0.5D) % 360.0D)));
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0D * i / 12.0D;
            // 每三格留一根长刻度，转起来才看得出方位
            double inner = i % 3 == 0 ? 0.62D : 0.76D;
            double outer = 0.98D + 0.08D * breathe;
            RenderFx.line(lines, pose.last(), Math.cos(angle) * inner, Math.sin(angle) * inner, 0.0D,
                    Math.cos(angle) * outer, Math.sin(angle) * outer, 0.0D,
                    150, 176, 255, (int) (65.0D + 40.0D * breathe));
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // ② 传送膜：一张完整的圆形膜绕着门心转
    // ------------------------------------------------------------------

    private static void renderMembrane(PoseStack pose, VertexConsumer swirl, VertexConsumer spark,
                                       double time, double ease, double ringAngle, float yaw) {
        double radius = PORTAL_RADIUS * ease;
        int alpha = (int) (238.0D * ease);
        if (radius <= 0.05D || alpha < 6) {
            return;
        }
        // 主膜：整张贴图铺满圆盘；贴图边缘本身已经淡出，几何再把最外圈收成全透明 ——
        // 膜的边界是「化开」的，没有白边、也没有硬邦邦的圆边
        double spinDeg = ArmillaryAssembly.normalize(ringAngle * 0.62D + time * 0.90D);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees((float) spinDeg));
        RenderFx.discMappedSoft(swirl, pose.last(), radius, 0.88D, 96, 0.0D,
                246, 236, 255, alpha, 0);
        pose.popPose();

        // 视差膜：同一张贴图反向慢转、半径更小、更淡，膜才有「深处还在涌」的层次
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees((float) (-spinDeg * 0.52D)));
        RenderFx.discMappedSoft(swirl, pose.last(), radius * 0.70D, 0.86D, 72, 0.0D,
                208, 198, 255, (int) (alpha * 0.42D), 0);
        pose.popPose();

        // 门心一团柔光：正对镜头画，从门的哪一侧看都是一团软的圆光
        double core = (0.44D + 0.10D * Math.sin(time * 0.42D)) * ease;
        billboardGlow(pose, spark, yaw, core, 250, 252, 255, (int) (130.0D * ease));
    }

    // ------------------------------------------------------------------
    // ③ 开合冲击
    // ------------------------------------------------------------------

    private static void renderBurst(PoseStack pose, VertexConsumer lines, VertexConsumer flat,
                                    VertexConsumer spark, double time, double ease, float yaw) {
        // 开合瞬间的白热核心 + 能量壳
        if (ease < 0.62D) {
            double hot = 1.0D - ease / 0.62D;
            if (hot > 0.02D) {
                billboardGlow(pose, spark, yaw, 0.55D + 1.8D * hot, 255, 255, 255,
                        (int) (185.0D * hot));
                RenderFx.circle(lines, pose.last(), 0.8D + 4.2D * hot, 96, 205, 228, 255,
                        (int) (165.0D * hot));
                RenderFx.circle(lines, pose.last(), 1.4D + 6.2D * hot, 120, 255, 242, 214,
                        (int) (135.0D * hot));
            }
        }

        // 两圈扩散的冲击波：贴图柔光 + 细线两种，一道宽一道窄
        if (ease > 0.30D) {
            double wave = (ease - 0.30D) / 0.70D;
            double radius = 2.0D + 16.0D * wave;
            RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, radius, 1.30D, 72,
                    rgba((int) (135.0D * (1.0D - wave)), 0xD9C8FF));
            RenderFx.circle(lines, pose.last(), radius + 0.4D, 128, 255, 246, 220,
                    (int) (200.0D * (1.0D - wave)));
        }
        if (ease > 0.55D) {
            double wave = (ease - 0.55D) / 0.45D;
            double radius = 1.0D + 9.0D * wave;
            RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V, radius, 0.85D, 56,
                    rgba((int) (115.0D * (1.0D - wave)), 0x9FD8FF));
        }

        // 门开稳之后，门心持续往外冒的一点点涟漪
        if (ease > 0.9D) {
            double beat = (time * 0.35D) % 1.0D;
            RenderFx.glowRing(flat, pose.last(), RenderFx.XY_U, RenderFx.XY_V,
                    0.8D + 5.4D * beat, 0.55D, 56,
                    rgba((int) (60.0D * (1.0D - beat)), 0xC7D8FF));
        }
    }

    private static int rgba(int alpha, int rgb) {
        return ((Mth.clamp(alpha, 0, 255)) << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * 正对镜头的柔光点。
     *
     * <p>{@link RenderFx#billboard} 是「在当前位姿上再乘一次镜头朝向」，而这里拿到的位姿已经带了
     * 门的朝向 {@code yaw}，直接乘会让光片偏掉一个门的转角——东西朝向的门上，光片会侧对镜头、
     * 细成一条亮边。所以先把门的 yaw 转回去，再乘镜头朝向，得到的才是真正正对镜头的一团柔光。</p>
     *
     * <p>以前门心用的是 {@link RenderFx#glowCross} 的三张互相垂直的硬面片：贴图是柔光，
     * 但面片本身是方的，凑近或者侧着看就会切出白亮的方角 / 亮边。改成正对镜头的柔光面片之后，
     * 从任何角度看都是一团软光。</p>
     */
    private static void billboardGlow(PoseStack pose, VertexConsumer spark, float yaw, double half,
                                      int red, int green, int blue, int alpha) {
        if (alpha <= 0 || half <= 0.0D || !Double.isFinite(half)) {
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        RenderFx.billboard(pose);
        RenderFx.glowQuad(spark, pose.last(), half, red, green, blue, alpha);
        pose.popPose();
    }
}
