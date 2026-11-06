package cn.blockforge.generated.arcanesingularity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 近场视图守卫 —— 专门治「花屏」的那一类：<b>巨大面片从镜头里穿过去</b>。
 *
 * <p>他描述的两个症状其实是同一件事：</p>
 * <ol>
 *     <li><b>整屏被切成几块色板</b>：一块十几格宽的阵面 / 环带 / 光柱四边形压在镜头上，
 *         一个面片就占掉半个屏幕，几个叠在一起就是几块彩色矩形；</li>
 *     <li><b>一大堆细线全朝准星扎过去</b>：镜头正好在某个水平阵面（魔法阵、汇聚环）的高度上，
 *         面片离镜头越近，投影越往视线的消失点收；细分成一小段一小段的环带如果还按
 *         「方向 × 到镜头」去算横向，「贴到眼睛上的那几段」横向会瞬间翻转，
 *         扭成一条条乱飞的细丝，全朝着准星聚成一片。</li>
 * </ol>
 *
 * <p>所以这里给每个图元算一个「该淡多少」的系数。关键点是<b>按图元自己的尺寸来算</b>：
 * 只有当一个图元在屏幕上「大得离谱」时才淡。格子是两三格宽的阵面格子、离你几十格远时，
 * 视角尺寸很小，一点都不会淡；同一个格子贴到眼前才淡下去。这样既不会误伤正常视角，
 * 也不会在镜头穿过面片时糊满整屏。</p>
 *
 * <p><b>只淡不消失。</b>所有系数都有下限（{@link #FLOOR} / {@link #THIN_FLOOR} /
 * {@link #GRAZE_FLOOR}），而且随距离平滑过渡，没有开关式跳变；转动视角不会改变这些系数
 * （只看位置和朝向比），所以「转个头特效就没了」不会发生。</p>
 */
public final class FxView {
    /** 大平面的最低可见度。 */
    public static final float FLOOR = 0.18F;
    /** 细长几何（光带、光斑）的最低可见度。 */
    public static final float THIN_FLOOR = 0.20F;
    /** 几乎躺在一个平面里看时，那个平面的最低可见度。 */
    public static final float GRAZE_FLOOR = 0.12F;

    /** 图元的视角尺寸（尺寸 / 距离）小于它：完全不淡。 */
    private static final double ANGULAR_FULL = 0.16D;
    /** 图元的视角尺寸大于它：淡到下限。 */
    private static final double ANGULAR_FLOOR = 0.55D;
    /** 视线与平面法线的余弦低于这个值就算「几乎贴着平面」。 */
    private static final double GRAZE_AMOUNT = 0.50D;

    private FxView() {
    }

    // ------------------------------------------------------------------
    // 摄像机在局部坐标系里的位置
    // ------------------------------------------------------------------

    /**
     * 摄像机在 {@code pose} 局部坐标系里的位置。
     *
     * <p>方块实体渲染时位姿是「世界坐标 - 摄像机位置」，摄像机正好落在局部坐标的原点上，
     * 所以它在这个坐标系里的位置就是原点反算回来的点。</p>
     *
     * <p>不真的做 4×4 求逆（每帧要算几百次，还要防奇异矩阵）：位姿永远是「旋转 × 平移」，
     * 逆变换就是 {@code -Rᵀ·T}，九次乘法就够。</p>
     */
    public static Vec3 eye(PoseStack.Pose pose) {
        if (pose == null) {
            return new Vec3(0.0D, 0.0D, -1.0D);
        }
        Matrix4f matrix = pose.pose();
        double t0 = matrix.m30();
        double t1 = matrix.m31();
        double t2 = matrix.m32();
        double x = -(matrix.m00() * t0 + matrix.m01() * t1 + matrix.m02() * t2);
        double y = -(matrix.m10() * t0 + matrix.m11() * t1 + matrix.m12() * t2);
        double z = -(matrix.m20() * t0 + matrix.m21() * t1 + matrix.m22() * t2);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return new Vec3(0.0D, 0.0D, -1.0D);
        }
        return new Vec3(x, y, z);
    }

    // ------------------------------------------------------------------
    // 系数
    // ------------------------------------------------------------------

    /**
     * 按「图元尺寸 / 到镜头的距离」淡出。
     *
     * @param size 图元最短的那条边（格）。阵面格子就传格子边长，环带就传带宽，
     *             光柱就传直径——传对了才不会误伤正常视角。
     */
    public static float near(Vec3 eye, Vec3 point, double size) {
        if (eye == null || point == null) {
            return 1.0F;
        }
        double distance = eye.distanceTo(point);
        if (!Double.isFinite(distance)) {
            return FLOOR;
        }
        double footprint = Math.max(0.02D, Math.abs(size));
        double angular = footprint / Math.max(distance, footprint * 0.25D);
        double t = Mth.clamp((angular - ANGULAR_FULL) / (ANGULAR_FLOOR - ANGULAR_FULL), 0.0D, 1.0D);
        return 1.0F - (1.0F - FLOOR) * (float) smooth(t);
    }

    /** 细长几何（光带、光斑）：尺寸按给的 {@code half} 算，收敛得更硬一些。 */
    public static float thin(Vec3 eye, Vec3 point, double size) {
        if (eye == null || point == null) {
            return 1.0F;
        }
        double distance = eye.distanceTo(point);
        if (!Double.isFinite(distance)) {
            return THIN_FLOOR;
        }
        double footprint = Math.max(0.05D, Math.abs(size));
        double angular = footprint / Math.max(distance, footprint * 0.25D);
        double t = Mth.clamp((angular - 0.55D) / 0.85D, 0.0D, 1.0D);
        return 1.0F - (1.0F - THIN_FLOOR) * (float) smooth(t);
    }

    /**
     * 平面遮挡系数：正对着这个平面看是 1；镜头几乎躺在平面里、或者离平面只有一点点时，
     * 收到 {@link #GRAZE_FLOOR}。
     *
     * <p>一个平面被斜到几乎和视线平行时，它本来就该缩成一条线；这时候如果还按整片铺开画，
     * 就是满屏的花纹色板。用「眼睛到平面的距离 / 眼睛到面心的距离」正合适：
     * 抬头看阵是满亮度，站在阵的平面里就只剩薄薄一层光。</p>
     */
    public static float plane(Vec3 eye, Vec3 center, Vec3 normal, double extent) {
        if (eye == null || center == null || normal == null) {
            return 1.0F;
        }
        double lengthSqr = normal.lengthSqr();
        if (lengthSqr < 1.0E-8D) {
            return 1.0F;
        }
        Vec3 toCenter = center.subtract(eye);
        double view = toCenter.length();
        if (!Double.isFinite(view) || view < 1.0E-5D) {
            return GRAZE_FLOOR;
        }
        // 只有镜头已经进了这个平面的「势力范围」才需要收敛：
        // 站在几十格外看一圈水平环，看到的本来就是一条细线，那是正常画面，不能淡。
        double reach = Math.max(1.0D, Math.abs(extent));
        double closeness = Mth.clamp((reach * 1.8D - view) / (reach * 0.8D), 0.0D, 1.0D);
        if (closeness <= 0.0D) {
            return 1.0F;
        }
        double depth = Math.abs(toCenter.dot(normal.scale(1.0D / Math.sqrt(lengthSqr))));
        double cosine = depth / view;
        double graze = Mth.clamp(cosine / GRAZE_AMOUNT, 0.0D, 1.0D);
        double factor = smooth(graze) * smooth(closeness);
        return GRAZE_FLOOR + (1.0F - GRAZE_FLOOR) * (float) factor;
    }

    /**
     * 光柱系数：镜头到光柱轴线的距离越近越淡，躲进光柱里也不会被四面墙糊住。
     *
     * @param tube 光柱的半径（格）
     */
    public static float beam(Vec3 eye, Vec3 a, Vec3 b, double tube) {
        if (eye == null || a == null || b == null) {
            return 1.0F;
        }
        double clearance = Math.max(0.55D, Math.abs(tube) * 1.35D);
        double distance = distanceToSegment(eye, a, b);
        if (!Double.isFinite(distance)) {
            return THIN_FLOOR;
        }
        double t = Mth.clamp(distance / clearance, 0.0D, 1.0D);
        return THIN_FLOOR + (1.0F - THIN_FLOOR) * (float) smooth(t);
    }

    /** 一个点到线段的距离。 */
    public static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 direction = b.subtract(a);
        double lengthSqr = direction.lengthSqr();
        if (lengthSqr < 1.0E-8D) {
            return point.distanceTo(a);
        }
        double t = Mth.clamp(point.subtract(a).dot(direction) / lengthSqr, 0.0D, 1.0D);
        return point.distanceTo(a.add(direction.scale(t)));
    }

    // ------------------------------------------------------------------
    // 颜色
    // ------------------------------------------------------------------

    /** 只把 alpha 乘上系数。 */
    public static int fade(int argb, float factor) {
        float f = Mth.clamp(factor, 0.0F, 1.0F);
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (Mth.clamp(a, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    /** 两个系数连乘（距离淡出 × 平面遮挡）。 */
    public static float mul(float a, float b) {
        return Mth.clamp(a * b, 0.0F, 1.0F);
    }

    private static double smooth(double t) {
        double clamped = Mth.clamp(t, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }
}
