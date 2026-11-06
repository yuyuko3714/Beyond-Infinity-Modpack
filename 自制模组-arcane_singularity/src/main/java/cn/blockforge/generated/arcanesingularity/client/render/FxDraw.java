package cn.blockforge.generated.arcanesingularity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 世界之核特效的绘制原语（配合 {@link FxLayers} 提供的层）。
 *
 * <p>所有几何都只写 <b>四顶点图元</b>（QUADS），每个顶点只写
 * 「位置 → 颜色 → UV」三样，正好填满原版 {@code POSITION_COLOR_TEX}：
 * 不写光照、不写 overlay、不写 UV2，所以不存在「多一个字段被按错误步长解读」的彩色乱三角。</p>
 *
 * <p><b>顶点流不会被写残。</b>{@link #quad} 先把四个顶点全部算完、全部检查过，再一起写：
 * 只要有一个顶点是 NaN / Inf，整片就一个顶点都不写。以前是逐个顶点检查、逐个顶点跳过，
 * 一个面片里漏掉一两个顶点，后面所有四边形就会按错误的分组重新拼，
 * 屏幕上直接出现几块横跨整个屏幕的大色块——也就是他说的「屏幕被花屏切成四个矩形」。</p>
 *
 * <p><b>贴近镜头不会糊屏。</b>每个图元都按 {@link FxView} 给出的系数淡出：
 * 大平面（{@link #glyphPlate}、{@link #softDisc}）细分后逐格淡出，光带（{@link #ribbon}）
 * 按段淡出，于是镜头穿过阵面 / 贴着环带时看到的是一层很薄的光，而不是满屏色板。</p>
 */
public final class FxDraw {
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Vec3 AXIS_X = new Vec3(1.0D, 0.0D, 0.0D);
    private static final Vec3 AXIS_Z = new Vec3(0.0D, 0.0D, 1.0D);

    /** 水平面（法线 +Y）的两个基向量。 */
    public static final Vec3 XZ_U = AXIS_X;
    public static final Vec3 XZ_V = AXIS_Z;
    /** 竖直面（法线 +Z）的两个基向量。 */
    public static final Vec3 XY_U = AXIS_X;
    public static final Vec3 XY_V = UP;

    /** 大平面细分成的格数（每边）。16×16 = 256 片，足够把「贴上来的那一块」单独淡掉。 */
    private static final int PLATE_CELLS = 16;

    private FxDraw() {
    }

    // ------------------------------------------------------------------
    // 颜色
    // ------------------------------------------------------------------

    /** {@code alpha} 为 0..255，{@code rgb} 为 0xRRGGBB。 */
    public static int rgba(int alpha, int rgb) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** 乘一个 0..1 的亮度系数（alpha 与 RGB 一起压暗）。 */
    public static int dim(int argb, float factor) {
        float f = Mth.clamp(factor, 0.0F, 1.0F);
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (Mth.clamp(a, 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }

    /** 只改 alpha。 */
    public static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    // ------------------------------------------------------------------
    // 顶点
    // ------------------------------------------------------------------

    /**
     * 写一个顶点。**只允许 {@link #quad} 调用**：它保证四个顶点要么一起进缓冲、要么一起不进。
     */
    private static void rawVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p,
                                  float u, float v, int argb) {
        consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
                .color(red(argb), green(argb), blue(argb), alpha(argb))
                .uv(u, v)
                .endVertex();
    }

    /** 四边形：{@code a→b→c→d} 螺旋，UV 依次取四角。 */
    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            float u0, float v0, float u1, float v1, int color) {
        quad(consumer, pose, a, b, c, d, u0, v0, u1, v1, color, color, color, color);
    }

    /**
     * 四边形：四个顶点先全部检查，再一起写。
     *
     * <p>任何一个坐标或 UV 不是有限数，<b>整个面片直接丢掉</b>——绝不写「三个顶点」。
     * QUADS 流里少写一个顶点，后面所有四边形都会错位重组，那一类花屏就是从这来的。</p>
     */
    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            float u0, float v0, float u1, float v1,
                            int ca, int cb, int cc, int cd) {
        if (consumer == null || pose == null) {
            return;
        }
        if (!finite(a, b, c, d) || !Float.isFinite(u0) || !Float.isFinite(v0)
                || !Float.isFinite(u1) || !Float.isFinite(v1)) {
            return;
        }
        rawVertex(consumer, pose, a, u0, v0, ca);
        rawVertex(consumer, pose, b, u1, v0, cb);
        rawVertex(consumer, pose, c, u1, v1, cc);
        rawVertex(consumer, pose, d, u0, v1, cd);
    }

    /**
     * 镜头位置（在 {@code pose} 的局部坐标系里）。
     *
     * <p>方块实体渲染时位姿是「方块原点 - 摄像机位置」，所以摄像机在局部坐标里的位置
     * 就是位姿矩阵求逆后对原点做变换。面片朝镜头翻、光带绕自己的轴转，都靠它。</p>
     */
    public static Vec3 eye(PoseStack.Pose pose) {
        return FxView.eye(pose);
    }

    // ------------------------------------------------------------------
    // 光带（替代一像素线）
    // ------------------------------------------------------------------

    /**
     * 一条正对镜头的带子：从 {@code a} 到 {@code b}，宽度 {@code halfWidth}（格）。
     *
     * <p>带的「横向」取线段方向与「从线段中点指向镜头」方向的叉积，所以无论镜头在
     * 哪一侧，看到的都是一条等宽的亮带，不会变成一条细线或直接消失。</p>
     *
     * <p>镜头正好落在这条带子上时叉积会退化（横向向量为零、方向随机翻转，带子会扭成
     * 一条乱飞的细丝），这一段直接不画；镜头离得近但没贴着时按 {@link FxView#thin} 淡出。
     * 两种情况都是「已经在你眼睛里的那一段」，不画看不出差别。</p>
     *
     * @param uScale UV 横向重复（贴图左侧 → 右侧）
     * @param v0     UV 纵向起点（配合滚动做出流动感）
     * @param v1     UV 纵向终点
     */
    public static void ribbon(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye,
                              Vec3 a, Vec3 b, double halfWidth,
                              float uScale, float v0, float v1, int colorA, int colorB) {
        if (!finite(a, b) || !finite(halfWidth) || halfWidth <= 0.0D) {
            return;
        }
        Vec3 dir = b.subtract(a);
        double dirLength = dir.length();
        if (dirLength < 1.0E-5D) {
            return;
        }
        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 toEye = eye == null ? new Vec3(0.0D, 0.0D, -1.0D) : eye.subtract(mid);
        // 镜头就在这条带子上（横向向量退化）→ 这一段不画，否则带子会随机翻面成一条乱丝
        if (toEye.lengthSqr() < 0.02D) {
            return;
        }
        float fade = FxView.thin(eye, mid, halfWidth * 2.0D);
        int ca = FxView.fade(colorA, fade);
        int cb = FxView.fade(colorB, fade);
        if (alpha(ca) <= 1 && alpha(cb) <= 1) {
            return;
        }
        Vec3 side = dir.cross(toEye);
        double sideLength = side.length();
        if (sideLength < dirLength * toEye.length() * 1.0E-3D) {
            // 视线几乎与带子平行：叉积接近零，方向不可信，退回一个稳定的横向
            side = dir.cross(UP);
            if (side.lengthSqr() < 1.0E-8D) {
                side = dir.cross(AXIS_X);
            }
            sideLength = side.length();
            if (sideLength < 1.0E-8D) {
                return;
            }
        }
        side = side.scale(halfWidth / sideLength);
        quad(consumer, pose, a.add(side), a.subtract(side), b.subtract(side), b.add(side),
                0.0F, v0, uScale, v1, ca, ca, cb, cb);
    }

    /** 纯色带子（采样纯白图，颜色全来自顶点色）。 */
    public static void flatRibbon(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye,
                                  Vec3 a, Vec3 b, double halfWidth, int colorA, int colorB) {
        ribbon(consumer, pose, eye, a, b, halfWidth, 0.5F, 0.5F, 0.5F, colorA, colorB);
    }

    /**
     * 平面上的一段环带：宽度沿<b>半径方向</b>铺开（始终躺在这个平面里）。
     *
     * <p>以前这里用的是「朝镜头的横向」：镜头一靠近，横向向量就翻来翻去，
     * 一整圈环带会扭成一堆朝准星扎的细丝。改用平面内的半径方向后，
     * 每一段都是稳定的梯形，只会随视角自然地变窄。</p>
     */
    public static void ringSegment(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye,
                                   Vec3 center, Vec3 inner0, Vec3 inner1, Vec3 outer0, Vec3 outer1,
                                   int color) {
        Vec3 mid = inner0.add(inner1).add(outer0).add(outer1).scale(0.25D);
        double size = Math.min(inner0.distanceTo(inner1), inner0.distanceTo(outer0));
        int faded = FxView.fade(color, FxView.near(eye, mid, size));
        if (alpha(faded) <= 1) {
            return;
        }
        quad(consumer, pose, inner0, outer0, outer1, inner1, 0.5F, 0.5F, 0.5F, 0.5F, faded);
    }

    /**
     * 平面上的圆环：由若干段梯形拼成，宽度沿半径方向铺开，所以从任何角度看都是一圈完整的环。
     *
     * @param u     圆环所在平面的第一根轴（单位向量）
     * @param v     圆环所在平面的第二根轴（单位向量）
     * @param width 环的线宽（格）
     */
    public static void ring(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye, Vec3 center,
                            Vec3 u, Vec3 v, double radius, double width, int segments, int color) {
        ring(consumer, pose, eye, center, u, v, radius, width, segments, color, color);
    }

    public static void ring(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye, Vec3 center,
                            Vec3 u, Vec3 v, double radius, double width, int segments,
                            int colorA, int colorB) {
        if (radius <= 0.0D || width <= 0.0D || segments < 3 || !finite(radius, width)) {
            return;
        }
        // 环躺在一个平面上。眼睛正好在这个平面的高度上时，整圈会被投影成一条横贯屏幕的线，
        // 离眼睛最近的那几段还会被拉成朝准星扎的细带——这里先按平面遮挡收一次亮度。
        Vec3 normal = u.cross(v);
        float face = 1.0F;
        if (normal.lengthSqr() >= 1.0E-8D) {
            face = FxView.plane(eye, center, normal.normalize(), radius);
            if (face <= 0.02F) {
                return;
            }
        }
        double inner = Math.max(0.0D, radius - width * 0.5D);
        double outer = radius + width * 0.5D;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 dir0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 dir1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            float blend = (float) i / segments;
            int color = FxView.fade(lerpColor(colorA, colorB, 1.0F - blend), face);
            ringSegment(consumer, pose, eye, center,
                    center.add(dir0.scale(inner)), center.add(dir1.scale(inner)),
                    center.add(dir0.scale(outer)), center.add(dir1.scale(outer)), color);
        }
    }

    public static int lerpColor(int a, int b, float t) {
        float f = Mth.clamp(t, 0.0F, 1.0F);
        int aa = (int) Mth.lerp(f, (float) alpha(a), (float) alpha(b));
        int ar = (int) Mth.lerp(f, (float) red(a), (float) red(b));
        int ag = (int) Mth.lerp(f, (float) green(a), (float) green(b));
        int ab = (int) Mth.lerp(f, (float) blue(a), (float) blue(b));
        return (Mth.clamp(aa, 0, 255) << 24) | (ar << 16) | (ag << 8) | ab;
    }

    // ------------------------------------------------------------------
    // 面片
    // ------------------------------------------------------------------

    /** 正对镜头的方形面片（贴图整张铺满），用于柔光、火花、阵心辉光。 */
    public static void billboard(VertexConsumer consumer, PoseStack pose, double half, int argb) {
        billboard(consumer, pose, half, 0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    public static void billboard(VertexConsumer consumer, PoseStack pose, double half,
                                 float u0, float v0, float u1, float v1, int argb) {
        if (!finite(half) || half <= 0.0D) {
            return;
        }
        // 位姿原点就是这团光的中心：原点离镜头多远，就是它离镜头多远
        int faded = FxView.fade(argb, FxView.thin(FxView.eye(pose.last()), Vec3.ZERO, half));
        if (alpha(faded) <= 1) {
            return;
        }
        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        quad(consumer, pose.last(),
                new Vec3(-half, -half, 0.0D), new Vec3(half, -half, 0.0D),
                new Vec3(half, half, 0.0D), new Vec3(-half, half, 0.0D),
                u0, v0, u1, v1, faded);
        pose.popPose();
    }

    /** 水平面上的一张方形贴图（魔法阵）：{@code radius} 为半径，贴图整张铺满，四角透明。 */
    public static void glyphPlate(VertexConsumer consumer, PoseStack.Pose pose, double radius, int argb) {
        glyphPlate(consumer, pose, FxView.eye(pose), radius, argb);
    }

    /**
     * 水平面上的一张方形贴图（魔法阵），<b>细分成 16×16 小格</b>逐格淡出。
     *
     * <p>整张阵图是一块 34×34 的大面片。镜头穿过它的高度时，靠近镜头的那一小块会占满屏幕，
     * 看上去就是一片带花纹的色板。切开以后，只有「确实贴着镜头」的那几格淡掉，
     * 阵的其余部分照样完整——抬头看时还是那张完整华丽的阵，站在阵里时看到的是身边薄薄一层光。</p>
     */
    public static void glyphPlate(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye,
                                  double radius, int argb) {
        if (!finite(radius) || radius <= 0.0D) {
            return;
        }
        int cells = PLATE_CELLS;
        double step = radius * 2.0D / cells;
        // 平面遮挡整张阵共用一个系数（只和眼睛到阵面的角度有关），逐格再乘各自的近场系数
        float face = FxView.plane(eye, Vec3.ZERO, UP, radius);
        for (int ix = 0; ix < cells; ix++) {
            double x0 = -radius + step * ix;
            double x1 = x0 + step;
            float u0 = (float) ix / cells;
            float u1 = (float) (ix + 1) / cells;
            for (int iz = 0; iz < cells; iz++) {
                double z0 = -radius + step * iz;
                double z1 = z0 + step;
                float v0 = (float) iz / cells;
                float v1 = (float) (iz + 1) / cells;
                double cx = (x0 + x1) * 0.5D;
                double cz = (z0 + z1) * 0.5D;
                Vec3 cellCenter = new Vec3(cx, 0.0D, cz);
                float fade = FxView.mul(FxView.near(eye, cellCenter, step), face);
                int faded = FxView.fade(argb, fade);
                if (alpha(faded) <= 1) {
                    continue;
                }
                quad(consumer, pose,
                        new Vec3(x0, 0.0D, z0), new Vec3(x1, 0.0D, z0),
                        new Vec3(x1, 0.0D, z1), new Vec3(x0, 0.0D, z1),
                        u0, v0, u1, v1, faded);
            }
        }
    }

    /** 水平面上的一团柔光（径向渐变贴图）：给魔法阵垫一层光晕。 */
    public static void softDisc(VertexConsumer consumer, PoseStack.Pose pose, double radius, int argb) {
        glyphPlate(consumer, pose, radius, argb);
    }

    /** 水平面上的一团柔光，用调用方给的镜头位置做淡出。 */
    public static void softDisc(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye,
                                double radius, int argb) {
        glyphPlate(consumer, pose, eye, radius, argb);
    }

    /**
     * 一圈朝外的短线（能量须、刻度这种）：从内端到外端各画一条细带，宽度落在平面内。
     */
    public static void spikes(VertexConsumer consumer, PoseStack.Pose pose, Vec3 eye, Vec3 center,
                              Vec3 u, Vec3 v, double innerRadius, double outerRadius, int count,
                              double width, int color) {
        if (count < 1 || !finite(innerRadius, outerRadius, width) || width <= 0.0D) {
            return;
        }
        // 这些须也躺在同一个平面上，同样要吃平面遮挡系数
        Vec3 normal = u.cross(v);
        float face = 1.0F;
        if (normal.lengthSqr() >= 1.0E-8D) {
            face = FxView.plane(eye, center, normal.normalize(), outerRadius);
            if (face <= 0.02F) {
                return;
            }
        }
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            Vec3 direction = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            Vec3 tangent = u.scale(-Math.sin(angle)).add(v.scale(Math.cos(angle)));
            double tangentLength = tangent.length();
            if (tangentLength < 1.0E-6D) {
                continue;
            }
            tangent = tangent.scale(width * 0.5D / tangentLength);
            Vec3 inner = center.add(direction.scale(innerRadius));
            Vec3 outer = center.add(direction.scale(outerRadius));
            int faded = FxView.fade(color, FxView.mul(FxView.near(eye, inner.lerp(outer, 0.5D), width), face));
            if (alpha(faded) <= 1) {
                continue;
            }
            quad(consumer, pose, inner.add(tangent), outer.add(tangent),
                    outer.subtract(tangent), inner.subtract(tangent),
                    0.5F, 0.5F, 0.5F, 0.5F, faded);
        }
    }

    public static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    public static boolean finite(Vec3... points) {
        for (Vec3 point : points) {
            if (point == null || !Double.isFinite(point.x) || !Double.isFinite(point.y)
                    || !Double.isFinite(point.z)) {
                return false;
            }
        }
        return true;
    }
}
