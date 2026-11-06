package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.structure.ArcaneStructure;
import cn.blockforge.generated.arcanesingularity.structure.WorldCoreAssembly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 世界之核的全部光影（重写版）。
 *
 * <p>这一版把「渲染」和「几何」彻底分开：</p>
 * <ul>
 *     <li>render layer 全部来自 {@link FxLayers}（r16 那一版写法）：着色器只用原版 Core Shader
 *         {@code position_color} / {@code position_color_tex}，顶点格式只用原版的
 *         {@code POSITION_COLOR_TEX}（位置 + 颜色 + UV），只有四顶点图元；
 *         <b>不写光照、不写 overlay、不依赖任何额外采样器</b>，所以不会出现
 *         「贴图被采成别的图 → 满屏彩色色块」那类花屏；</li>
 *     <li>魔法阵不再是几百条一像素线拼出来的，而是<b>三张画好的阵图</b>：
 *         外阵（双环 + 96 刻度 + 24 符文 + 六芒星 + 花瓣带，金）、中阵（青色法环，反向快转）、
 *         心阵（中央小符印）。图案是闭合的、按极坐标画的，所以怎么转都不会「画歪」，
 *         也不会因为距离一远就细到看不见；</li>
 *     <li>激光、能量丝、圆环全部是<b>正对镜头的有宽度带子</b>（{@link FxDraw#ribbon}），
 *         宽度按格算，近看远看都是同一道光；</li>
 *     <li>没有任何「离镜头太近就淡出」的逻辑，贴脸、抬头、走到远处都照样画。</li>
 * </ul>
 *
 * <p>五幕节奏（{@link WorldCoreAssembly}）：充能 → 三光汇聚 → 魔法阵展开并旋转 →
 * 上下对射 → 核心爆发，稳定后剩一道中轴光柱。</p>
 *
 * <p><b>爆发的核心不在这个类里画。</b>它是一个带碰撞箱的实体（{@code WorldCoreCoreEntity}），
 * 由 {@code WorldCoreCoreRenderer} 在实体阶段用方块模型画出来 —— 中心命令方块 + 两圈
 * 由小方块拼成的环。这里只留一团正对镜头的柔光。以前那些横跨几十格的水平光环
 * 是花屏的源头：玩家站在核心高度时它们会和视线共面，被投影成满屏细线。</p>
 */
public final class WorldCoreFx {
    /** 三道激光射到的天空点。 */
    public static final double GATHER_Y = ArcaneStructure.WORLD_CORE_GATHER_Y;
    /** 对撞点。 */
    public static final double FOCUS_Y = ArcaneStructure.WORLD_CORE_FOCUS_Y;

    private static final int EMITTER_BLACK_HOLE = 0xB076FF;
    private static final int EMITTER_STARGATE = 0x5FE6FF;
    private static final int EMITTER_BEACON = 0xFFC46B;
    private static final int[] EMITTER_COLORS = {EMITTER_BLACK_HOLE, EMITTER_STARGATE, EMITTER_BEACON};

    private static final int GOLD = 0xFFE3B0;
    private static final int PALE = 0xFFF4DE;
    private static final int CYAN = 0x9BE8FF;
    private static final int WHITE = 0xFFFFFF;

    private static final Vec3 GATHER = new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, GATHER_Y, 0.0D);
    private static final Vec3 FOCUS = new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, FOCUS_Y, 0.0D);

    private WorldCoreFx() {
    }

    /** 三个接口上激光器管口相对控制器的位置。 */
    public static List<Vec3> emitterMuzzles() {
        List<Vec3> result = new ArrayList<>();
        for (BlockPos offset : ArcaneStructure.worldCoreInterfaceOffsets()) {
            result.add(new Vec3(offset.getX(), ArcaneStructure.WORLD_CORE_EMITTER_MUZZLE_Y, offset.getZ()));
        }
        return result;
    }

    /** 控制器上方那台更高的激光器的顶面。 */
    public static Vec3 apexMuzzle() {
        return new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, ArcaneStructure.WORLD_CORE_APEX_MUZZLE_Y, 0.0D);
    }

    /**
     * 画出世界之核的全部光影（调用时位姿已经平移到控制器方块中心）。
     *
     * @param time        游戏时间（tick，含插值）
     * @param charge      接口激光器聚能 0..1
     * @param gather      三道光射出 0..1
     * @param glyphOpen   魔法阵展开 0..1
     * @param glyphAngle  魔法阵累计转角（度）
     * @param glyphRamp   魔法阵转速占比 0..1
     * @param converge    对射光束 0..1
     * @param burst       巨大命令方块显形 0..1
     * @param beam        稳定后的中轴光柱强度 0..1
     * @param running     是否已经稳定
     * @param shutdown    关机进度 0..1
     * @param commandMode 0 = 脉冲（橙），1 = 循环（紫）
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, double time,
                              float charge, float gather, float glyphOpen, double glyphAngle, float glyphRamp,
                              float converge, float burst, float beam, boolean running,
                              float shutdown, int commandMode) {
        if (!RenderFx.usable(pose) || !Double.isFinite(time)) {
            return;
        }
        float fade = Mth.clamp(1.0F - shutdown, 0.0F, 1.0F);
        if (fade <= 0.001F) {
            return;
        }
        Vec3 eye = FxDraw.eye(pose.last());
        VertexConsumer spark = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.SPARK));
        VertexConsumer beamTex = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.BEAM));
        VertexConsumer flat = buffers.getBuffer(FxRenderTypes.additive(FxRenderTypes.FLAT));

        renderEmitters(pose, spark, beamTex, flat, eye, time, charge, gather, fade);
        if (gather > 0.02F) {
            renderGatherPoint(pose, spark, flat, eye, time, gather, fade);
        }
        if (glyphOpen > 0.01F) {
            renderGlyph(pose, buffers, spark, flat, eye, time, glyphOpen, glyphAngle, glyphRamp, fade);
        }
        if (converge > 0.02F) {
            renderConverge(pose, beamTex, flat, spark, eye, time, converge, fade);
        }
        if (burst > 0.01F) {
            renderCommandCore(pose, buffers, spark, eye, time, burst, commandMode, fade);
        }
        if (beam > 0.05F) {
            renderAxisShaft(pose, beamTex, flat, eye, time, beam, running, fade);
        }
    }

    // ------------------------------------------------------------------
    // ① 三个接口激光器：聚能 + 三道激光
    // ------------------------------------------------------------------

    private static void renderEmitters(PoseStack pose, VertexConsumer spark, VertexConsumer beam, VertexConsumer flat,
                                       Vec3 eye, double time, float charge, float gather, float fade) {
        List<Vec3> muzzles = emitterMuzzles();
        float power = Mth.clamp(charge, 0.0F, 1.0F);
        for (int i = 0; i < muzzles.size(); i++) {
            Vec3 muzzle = muzzles.get(i);
            int color = EMITTER_COLORS[i % EMITTER_COLORS.length];
            float pulse = 0.88F + 0.12F * (float) Math.sin(time * 0.17D + i * 2.1D);

            // 管口光球：不管什么阶段都亮着，贴脸也不会消失
            pose.pushPose();
            pose.translate(muzzle.x, muzzle.y + 0.03D, muzzle.z);
            FxDraw.billboard(spark, pose, (0.32D + 0.34D * power) * pulse,
                    FxDraw.withAlpha(color, (int) ((75.0F + 120.0F * power) * fade)));
            FxDraw.billboard(spark, pose, (0.15D + 0.14D * power) * pulse,
                    FxDraw.rgba((int) ((85.0F + 120.0F * power) * fade), WHITE));
            pose.popPose();

            // 管口那圈水平能量环
            FxDraw.ring(flat, pose.last(), eye, muzzle.add(0.0D, 0.02D, 0.0D),
                    FxDraw.XZ_U, FxDraw.XZ_V, 0.34D, 0.05D, 48,
                    FxDraw.rgba((int) ((55.0F + 105.0F * power) * fade), color));

            if (gather <= 0.02F) {
                continue;
            }
            float strength = Mth.clamp(gather, 0.0F, 1.0F) * fade;
            Vec3 end = muzzle.lerp(GATHER, Mth.clamp(gather, 0.0F, 1.0F));
            beamTo(pose, beam, flat, eye, muzzle, end, color, strength, time, i);
        }
    }

    /**
     * 一束贴图激光：外层辉光 + 主束 + 白热亮芯 + 两道缠绕的螺旋。
     *
     * <p>宽度全部按格算（0.03 ~ 0.17 格），所以它永远是「一道光」，
     * 不会像以前那样在贴脸时铺成满屏彩色宽带。</p>
     */
    private static void beamTo(PoseStack pose, VertexConsumer beam, VertexConsumer flat, Vec3 eye,
                               Vec3 start, Vec3 end, int color, float strength, double time, int seed) {
        double length = start.distanceTo(end);
        if (length < 0.25D || !Double.isFinite(length)) {
            return;
        }
        float scroll = (float) ((time * 0.35D + seed * 0.37D) % 1.0D);
        float vScale = (float) (length * 0.35D);
        FxDraw.ribbon(beam, pose.last(), eye, start, end, 0.17D, 1.0F,
                scroll, scroll + vScale * 0.5F,
                FxDraw.rgba((int) (70.0F * strength), color), FxDraw.rgba((int) (70.0F * strength), color));
        FxDraw.ribbon(beam, pose.last(), eye, start, end, 0.085D, 1.0F,
                scroll * 1.7F, scroll * 1.7F + vScale,
                FxDraw.rgba((int) (150.0F * strength), color), FxDraw.rgba((int) (150.0F * strength), color));
        FxDraw.flatRibbon(flat, pose.last(), eye, start, end, 0.028D,
                FxDraw.rgba((int) (205.0F * strength), WHITE), FxDraw.rgba((int) (205.0F * strength), WHITE));

        Vec3 direction = end.subtract(start).scale(1.0D / length);
        Vec3 helper = Math.abs(direction.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side0 = direction.cross(helper);
        if (side0.lengthSqr() < 1.0E-8D) {
            return;
        }
        side0 = side0.normalize();
        Vec3 side1 = direction.cross(side0).normalize();
        for (int strand = 0; strand < 2; strand++) {
            Vec3 previous = null;
            for (int step = 0; step <= 40; step++) {
                double t = step / 40.0D;
                double angle = time * 0.24D + strand * Math.PI + t * Math.PI * 2.0D * 6.0D;
                Vec3 point = start.add(direction.scale(length * t))
                        .add(side0.scale(Math.cos(angle) * 0.30D))
                        .add(side1.scale(Math.sin(angle) * 0.30D));
                if (previous != null) {
                    int alpha = (int) ((26.0F + 80.0F * (1.0F - Math.abs(0.5D - t) * 2.0F)) * strength);
                    FxDraw.flatRibbon(flat, pose.last(), eye, previous, point, 0.016D,
                            FxDraw.rgba(alpha, color), FxDraw.rgba(alpha, color));
                }
                previous = point;
            }
        }
    }

    // ------------------------------------------------------------------
    // ② 天空汇聚点
    // ------------------------------------------------------------------

    private static void renderGatherPoint(PoseStack pose, VertexConsumer spark, VertexConsumer flat,
                                          Vec3 eye, double time, float gather, float fade) {
        float power = Mth.clamp(gather, 0.0F, 1.0F) * fade;
        double pulse = 0.88D + 0.12D * Math.sin(time * 0.18D);

        pose.pushPose();
        pose.translate(GATHER.x, GATHER.y + 0.12D, GATHER.z);
        FxDraw.billboard(spark, pose, 1.75D * pulse, FxDraw.rgba((int) (85.0F * power), PALE));
        FxDraw.billboard(spark, pose, 0.72D * pulse, FxDraw.rgba((int) (150.0F * power), WHITE));
        pose.popPose();

        FxDraw.ring(flat, pose.last(), eye, GATHER.add(0.0D, 0.14D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                2.30D * pulse, 0.10D, 96, FxDraw.rgba((int) (150.0F * power), PALE));
        FxDraw.ring(flat, pose.last(), eye, GATHER.add(0.0D, 0.18D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                2.85D * pulse, 0.06D, 96, FxDraw.rgba((int) (95.0F * power), GOLD));
        FxDraw.spikes(flat, pose.last(), eye, GATHER.add(0.0D, 0.16D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                2.25D, 2.65D, 24, 0.035D, FxDraw.rgba((int) (90.0F * power), PALE));
    }

    // ------------------------------------------------------------------
    // ③ 魔法阵：三层阵图，各自反向旋转
    // ------------------------------------------------------------------

    private static void renderGlyph(PoseStack pose, MultiBufferSource buffers, VertexConsumer spark,
                                    VertexConsumer flat, Vec3 eye, double time,
                                    float open, double angle, float ramp, float fade) {
        float p = Mth.clamp(open, 0.0F, 1.0F);
        double ease = 1.0D - Math.pow(1.0D - p, 3.0D);
        double radius = WorldCoreAssembly.GLYPH_RADIUS * ease;
        if (radius < 0.6D || !Double.isFinite(radius) || !Double.isFinite(angle)) {
            return;
        }
        int bright = (int) ((205.0F + 40.0F * Mth.clamp(ramp, 0.0F, 1.0F)) * p * fade);
        int outerAlpha = Mth.clamp(bright, 0, 255);
        int midAlpha = Mth.clamp((int) (bright * 0.92F), 0, 255);
        int coreAlpha = Mth.clamp((int) (bright * 0.95F), 0, 255);

        VertexConsumer glyphOuter = buffers.getBuffer(FxRenderTypes.blended(FxRenderTypes.GLYPH_OUTER));
        VertexConsumer glyphMid = buffers.getBuffer(FxRenderTypes.blended(FxRenderTypes.GLYPH_MID));
        VertexConsumer glyphCore = buffers.getBuffer(FxRenderTypes.blended(FxRenderTypes.GLYPH_CORE));

        pose.pushPose();
        pose.translate(ArcaneStructure.WORLD_CORE_AXIS_X, GATHER_Y - 0.08D, 0.0D);
        // 阵面抬到 40 格高之后，「镜头在这个局部坐标系里的位置」也跟着变了；
        // 不重算就会拿控制器原点的镜头位置去算光带朝向，环会歪着飘。这里按当前位姿重算一次。
        Vec3 glyphEye = FxDraw.eye(pose.last());

        // 阵面底下垫一层暖光（加法），让整张阵「亮」起来
        FxDraw.softDisc(spark, pose.last(), radius * 0.96D, FxDraw.rgba((int) (30.0F * p * fade), PALE));

        // 外阵：金色主阵，顺时针由快到慢
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees((float) angle));
        FxDraw.glyphPlate(glyphOuter, pose.last(), radius, FxDraw.rgba(outerAlpha, 0xFFF8EC));
        pose.popPose();

        // 中阵：青色法环，反向 1.7 倍快转，抬高一点
        pose.pushPose();
        pose.translate(0.0D, 0.03D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees((float) (-angle * 1.7D)));
        FxDraw.glyphPlate(glyphMid, pose.last(), radius * 0.56D, FxDraw.rgba(midAlpha, 0xE6FBFF));
        pose.popPose();

        // 心阵：白热小符印，再反向快转
        pose.pushPose();
        pose.translate(0.0D, 0.06D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees((float) (angle * 2.3D)));
        FxDraw.glyphPlate(glyphCore, pose.last(), radius * 0.27D, FxDraw.rgba(coreAlpha, WHITE));
        pose.popPose();

        // 外缘一圈细环 + 24 道能量须（按格算宽度，远了也不会细成看不见）
        FxDraw.ring(flat, pose.last(), glyphEye, new Vec3(0.0D, 0.02D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                radius * 0.985D, 0.045D, 160, FxDraw.rgba(Mth.clamp((int) (bright * 0.75F), 0, 255), GOLD));
        FxDraw.spikes(flat, pose.last(), glyphEye, new Vec3(0.0D, 0.02D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                radius * 0.99D, radius * 1.045D, 24, 0.03D,
                FxDraw.rgba(Mth.clamp((int) (bright * 0.6F), 0, 255), PALE));

        // 一圈慢慢往外扩的细涟漪
        double wave = (time * 0.013D) % 1.0D;
        FxDraw.ring(flat, pose.last(), glyphEye, new Vec3(0.0D, 0.05D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                radius * (0.25D + 0.75D * wave), 0.04D, 128,
                FxDraw.rgba(Mth.clamp((int) (70.0F * (1.0D - wave) * p * fade), 0, 255), PALE));

        pose.popPose();
    }

    // ------------------------------------------------------------------
    // ④ 上下对射
    // ------------------------------------------------------------------

    private static void renderConverge(PoseStack pose, VertexConsumer beam, VertexConsumer flat,
                                       VertexConsumer spark, Vec3 eye, double time,
                                       float converge, float fade) {
        float p = Mth.clamp(converge, 0.0F, 1.0F) * fade;
        if (p <= 0.01F) {
            return;
        }
        Vec3 fromTop = new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, GATHER_Y - 0.7D, 0.0D);
        Vec3 fromApex = apexMuzzle();
        double reach = Math.min(1.0D, p * 1.45D);
        beamTo(pose, beam, flat, eye, fromTop, fromTop.lerp(FOCUS, reach), CYAN, p, time, 5);
        beamTo(pose, beam, flat, eye, fromApex, fromApex.lerp(FOCUS, reach), GOLD, p, time, 6);

        if (reach < 0.999D) {
            return;
        }
        double pulse = 0.85D + 0.15D * Math.sin(time * 0.42D);
        pose.pushPose();
        pose.translate(FOCUS.x, FOCUS.y, FOCUS.z);
        FxDraw.billboard(spark, pose, 1.9D * pulse, FxDraw.rgba((int) (95.0F * p), PALE));
        FxDraw.billboard(spark, pose, 0.75D * pulse, FxDraw.rgba((int) (185.0F * p), WHITE));
        pose.popPose();
        FxDraw.ring(flat, pose.last(), eye, FOCUS, FxDraw.XZ_U, FxDraw.XZ_V,
                1.30D * pulse, 0.07D, 96, FxDraw.rgba((int) (185.0F * p), PALE));
        FxDraw.ring(flat, pose.last(), eye, FOCUS.add(0.0D, 0.03D, 0.0D), FxDraw.XZ_U, FxDraw.XZ_V,
                1.75D * pulse, 0.05D, 96, FxDraw.rgba((int) (120.0F * p), CYAN));
    }

    // ------------------------------------------------------------------
    // ⑤ 巨大命令方块
    // ------------------------------------------------------------------

    /**
     * 核心的柔光。
     *
     * <p><b>命令方块本体已经不在特效里画了。</b>它现在是 {@code WorldCoreCoreEntity}：
     * 一个真正有碰撞箱的实体，由实体渲染器用方块模型画出来（中心命令方块 + 两圈小方块拼的环），
     * 和浑天仪环、时间信标汇聚环是同一条路线。以前在特效缓冲里放大命令方块模型、再叠两圈
     * 水平光环，玩家站在核心高度时那些水平四边形会和视线共面、贴着镜头铺过去，
     * 屏幕上就是「细线朝准星汇聚」和「屏幕被切成四块」——那才是花屏的来源。</p>
     *
     * <p>这里只留一团正对镜头的柔光，尺寸按爆发进度长，贴到镜头时按 {@link FxView} 淡出。</p>
     */
    private static void renderCommandCore(PoseStack pose, MultiBufferSource buffers, VertexConsumer spark,
                                          Vec3 eye, double time, float burst,
                                          int commandMode, float fade) {
        float p = Mth.clamp(burst, 0.0F, 1.0F);
        double scale = 0.4D + 3.3D * p;
        int color = commandMode == 0 ? 0xFF9E4A : 0xC06BFF;
        double bob = Math.sin(time * 0.06D) * 0.22D * p;

        pose.pushPose();
        pose.translate(ArcaneStructure.WORLD_CORE_AXIS_X, FOCUS_Y + bob, 0.0D);
        FxDraw.billboard(spark, pose, scale * 1.35D, FxDraw.rgba((int) (55.0F * p * fade), color));
        FxDraw.billboard(spark, pose, scale * 0.55D, FxDraw.rgba((int) (120.0F * p * fade), WHITE));
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 稳定态：一道细的中轴光柱 + 往上飘的环
    // ------------------------------------------------------------------

    private static void renderAxisShaft(PoseStack pose, VertexConsumer beam, VertexConsumer flat, Vec3 eye,
                                        double time, float beamPower, boolean running, float fade) {
        float power = Mth.clamp(beamPower, 0.0F, 1.0F) * fade;
        Vec3 from = new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, FOCUS_Y, 0.0D);
        Vec3 to = new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, GATHER_Y, 0.0D);
        float scroll = (float) ((time * 0.22D) % 1.0D);
        FxDraw.ribbon(beam, pose.last(), eye, from, to, 0.055D, 1.0F,
                scroll, scroll + 6.0F,
                FxDraw.rgba((int) (60.0F * power), PALE), FxDraw.rgba((int) (60.0F * power), PALE));
        FxDraw.flatRibbon(flat, pose.last(), eye, from, to, 0.02D,
                FxDraw.rgba((int) (85.0F * power), WHITE), FxDraw.rgba((int) (85.0F * power), WHITE));
        if (!running) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double travel = (time * 0.012D + i / 3.0D) % 1.0D;
            double y = FOCUS_Y + (GATHER_Y - FOCUS_Y) * travel;
            int alpha = Mth.clamp((int) (70.0D * (1.0D - travel) * power), 0, 255);
            FxDraw.ring(flat, pose.last(), eye, new Vec3(ArcaneStructure.WORLD_CORE_AXIS_X, y, 0.0D),
                    FxDraw.XZ_U, FxDraw.XZ_V, 0.9D + 0.8D * travel, 0.045D, 72,
                    FxDraw.rgba(alpha, PALE));
        }
    }
}
