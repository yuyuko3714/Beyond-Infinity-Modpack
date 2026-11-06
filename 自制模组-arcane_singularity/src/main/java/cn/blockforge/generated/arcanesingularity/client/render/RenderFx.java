package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 光影绘制原语（r16 那一版写法）。
 *
 * <p><b>这条路线是玩家机上验证过不再花屏的：</b>所有层都由 {@link FxLayers} 提供，
 * 跑的是原版 Core Shader {@code position_color} / {@code position_color_tex}，
 * 顶点格式是原版的 {@code POSITION_COLOR_TEX}（位置 + 颜色 + UV），
 * 贴图由层自己直接绑 PNG。</p>
 *
 * <p><b>顶点里没有的字段一个都不写：</b>{@code NEW_ENTITY} 那套要 overlay、lightmap、法线，
 * 片元还要采样 Sampler2（光照贴图）。玩家整合包里实体着色器被重新编译坏了，
 * 采样器一对不上，贴图就被采成别的图、颜色变成随机值——屏幕上是和场景毫不相干的彩色色块，
 * 而且实体层<b>写深度</b>，几百片重叠的半透明几何换个角度就会互相切出硬边。
 * 现在顶点只带位置、颜色、UV，图元恒定四顶点，既不会错位也不会互相掏洞。</p>
 *
 * <p>几何分两类，写法只有一处差别：</p>
 * <ul>
 *   <li>带贴图的（黑洞、吸积盘、传送面、激光、光斑）——UV 按需给；</li>
 *   <li>纯色几何（光环、色带、亮芯）——统一采样 {@link #FLAT} 那张纯白图，颜色全交给顶点色。</li>
 * </ul>
 */
public final class RenderFx {
    public static final ResourceLocation BLACK_HOLE = rl("textures/effect/black_hole.png");
    public static final ResourceLocation ACCRETION = rl("textures/effect/accretion_disk.png");
    public static final ResourceLocation PORTAL = rl("textures/effect/portal_swirl.png");
    /** 星门内缘的等离子环：一张「环」形状的贴图，靠半径映射铺到环带上。 */
    public static final ResourceLocation GATE_RIM = rl("textures/effect/stargate_rim.png");
    public static final ResourceLocation BEAM = rl("textures/effect/beam.png");
    public static final ResourceLocation LASER = rl("textures/effect/laser_beam.png");
    /**
     * 柔光点：中心亮、alpha 向外平滑衰减到 0 的单个圆光斑，做光斑、炮口闪光、符文珠。
     *
     * <p>旧的 {@code laser_spark.png} 其实是「中心 + 四角」五个光点，整张铺到面片上
     * 会多出四个亮点，远处看像方块点阵；这里换成真正的径向渐变图。</p>
     */
    public static final ResourceLocation GLOW = rl("textures/effect/soft_glow.png");
    /** 16×16 纯白图：纯色几何（光环、色带、亮芯）都采样它，颜色由顶点色决定。 */
    public static final ResourceLocation FLAT = rl("textures/effect/flat_white.png");

    /** XY 平面（法线 +Z）的基向量，浑天仪 / 星门的正面朝这里。 */
    public static final Vec3 XY_U = new Vec3(1.0D, 0.0D, 0.0D);
    public static final Vec3 XY_V = new Vec3(0.0D, 1.0D, 0.0D);
    /** XZ 平面（法线 +Y），水平面。 */
    public static final Vec3 XZ_U = new Vec3(1.0D, 0.0D, 0.0D);
    public static final Vec3 XZ_V = new Vec3(0.0D, 0.0D, 1.0D);

    private RenderFx() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArcaneSingularity.MOD_ID, path);
    }

    // ------------------------------------------------------------------
    // 层：全部来自 FxLayers（原版 Core Shader + 原版顶点格式）
    // ------------------------------------------------------------------

    /** 线段层（位置 + 颜色 + 法线，原版 {@code POSITION_COLOR}）。 */
    public static RenderType lines() {
        return FxLayers.LINES;
    }

    /** 半透明贴图层：常规 alpha 混合，图案色在亮背景前也看得清。 */
    public static RenderType emissive(ResourceLocation texture) {
        return FxLayers.blended(texture);
    }

    /** 加法混合贴图层：光斑、光核、粒子这类「光」，越叠越亮。 */
    public static RenderType additive(ResourceLocation texture) {
        return FxLayers.additive(texture);
    }

    /** 多方块投影虚影用的方块图集层（原版 Core Shader + 方块图集）。 */
    public static RenderType blockAtlas() {
        return FxLayers.atlas();
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /** 矩阵里如果有 NaN，后面所有顶点都会飞出去，这一帧干脆不画。 */
    public static boolean usable(PoseStack pose) {
        org.joml.Vector3f probe = pose.last().pose().transformPosition(new org.joml.Vector3f(0.0F, 0.0F, 0.0F));
        return Float.isFinite(probe.x) && Float.isFinite(probe.y) && Float.isFinite(probe.z);
    }

    /**
     * 光斑的「贴脸收敛」系数。
     *
     * <p>方块实体渲染时位姿以摄像机为原点，所以 {@code pose} 平移出来的位置就是它离摄像机的距离。
     * 一张正对镜头的大面片如果贴到脸上，会整屏铺成一块彩色半透明色块，看着像花屏。</p>
     *
     * <p>但收敛不等于消失：最低保留 {@code 0.45}，所以就算把脸贴到光斑上，那里依然有一团光，
     * 只是不会糊满整个屏幕。贴得越近越淡、越远越实，任何时候都不会「凭空不见」。</p>
     *
     * @param near 小于这个距离收到最低亮度
     * @param far  大于这个距离完全不加收敛
     */
    public static float nearFade(PoseStack.Pose pose, double near, double far) {
        if (pose == null || !(far > near)) {
            return 1.0F;
        }
        org.joml.Vector3f point = new org.joml.Vector3f();
        pose.pose().transformPosition(point);
        double distance = Math.sqrt(point.x * point.x + point.y * point.y + point.z * point.z);
        if (!Double.isFinite(distance)) {
            return 0.45F;
        }
        float ramp = (float) net.minecraft.util.Mth.clamp((distance - near) / (far - near), 0.0D, 1.0D);
        return 0.45F + 0.55F * ramp;
    }

    /** 让接下来画的面片正对摄像机（黑洞辉光、镜头光斑这类叠加层）。 */
    public static void billboard(PoseStack pose) {
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
    }

    // ------------------------------------------------------------------
    // 顶点写入
    // ------------------------------------------------------------------

    /**
     * 贴图顶点：位置 + 颜色 + UV，正好填满 {@code POSITION_COLOR_TEX}。
     *
     * <p>{@code normal} 参数保留下来只是为了几何代码可读（面朝向仍然是算出来的），
     * 新格式里没有法线元素，所以不写进顶点数据——少写一个字段就少一处可能对不上的地方。</p>
     */
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 pos, float u, float v,
                               Vec3 normal, int red, int green, int blue, int alpha) {
        consumer.vertex(pose.pose(), (float) pos.x, (float) pos.y, (float) pos.z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .endVertex();
    }

    /**
     * 线段顶点：位置 + 颜色 + 法线（{@code FxLayers.LINES} 用的原版 {@code POSITION_COLOR} 格式）。
     *
     * <p>{@code POSITION_COLOR} 的元素是「位置 + 颜色 + 法线」，三个都必须写满，
     * 否则 {@code endVertex()} 会判定顶点没填完。</p>
     */
    private static void lineVertex(VertexConsumer consumer, PoseStack.Pose pose,
                                   double x, double y, double z, int red, int green, int blue, int alpha) {
        consumer.vertex(pose.pose(), (float) x, (float) y, (float) z)
                .color(red, green, blue, alpha)
                .normal(pose.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    // ------------------------------------------------------------------
    // 线段几何
    // ------------------------------------------------------------------

    public static void line(VertexConsumer consumer, PoseStack.Pose pose,
                            double x1, double y1, double z1, double x2, double y2, double z2,
                            int red, int green, int blue, int alpha) {
        if (!finite(x1, y1, z1, x2, y2, z2)) {
            return;
        }
        lineVertex(consumer, pose, x1, y1, z1, red, green, blue, alpha);
        lineVertex(consumer, pose, x2, y2, z2, red, green, blue, alpha);
    }

    public static void circle(VertexConsumer consumer, PoseStack.Pose pose, double radius, int segments,
                              int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            line(consumer, pose, Math.cos(a0) * radius, Math.sin(a0) * radius, 0.0D,
                    Math.cos(a1) * radius, Math.sin(a1) * radius, 0.0D, red, green, blue, alpha);
        }
    }

    public static void circleXZ(VertexConsumer consumer, PoseStack.Pose pose, double radius, int segments,
                                int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            line(consumer, pose, Math.cos(a0) * radius, 0.0D, Math.sin(a0) * radius,
                    Math.cos(a1) * radius, 0.0D, Math.sin(a1) * radius, red, green, blue, alpha);
        }
    }

    public static void box(VertexConsumer consumer, PoseStack.Pose pose, double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ, int red, int green, int blue, int alpha) {
        line(consumer, pose, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(consumer, pose, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(consumer, pose, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    /** 沿光束缠绕的螺旋（高能粒子流的轨迹）。{@code phase} 让螺旋往前走。 */
    public static void helix(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end, double radius,
                             double turns, int segments, double phase,
                             int red, int green, int blue, int alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 unit = direction.normalize();
        Vec3 up = Math.abs(unit.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side0 = unit.cross(up).normalize().scale(radius);
        Vec3 side1 = unit.cross(unit.cross(up)).normalize().scale(radius);
        Vec3 previous = null;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            double angle = phase + t * turns * Math.PI * 2.0D;
            Vec3 point = start.add(direction.scale(t))
                    .add(side0.scale(Math.cos(angle)))
                    .add(side1.scale(Math.sin(angle)));
            if (previous != null) {
                line(consumer, pose, previous.x, previous.y, previous.z, point.x, point.y, point.z,
                        red, green, blue, alpha);
            }
            previous = point;
        }
    }

    // ------------------------------------------------------------------
    // 面片几何
    // ------------------------------------------------------------------

    /** 带贴图的四边形；{@code (u0,v1) (u1,v1) (u1,v0) (u0,v0)} 顺序写入。 */
    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                            float u0, float v0, float u1, float v1,
                            int red, int green, int blue, int alpha) {
        if (!finite(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z)) {
            return;
        }
        Vec3 normal = normalOf(a, b, c);
        vertex(consumer, pose, a, u0, v1, normal, red, green, blue, alpha);
        vertex(consumer, pose, b, u1, v1, normal, red, green, blue, alpha);
        vertex(consumer, pose, c, u1, v0, normal, red, green, blue, alpha);
        vertex(consumer, pose, d, u0, v0, normal, red, green, blue, alpha);
    }

    /** 纯色四边形：UV 固定采样纯白图中心，颜色全部来自顶点色。 */
    public static void coloredQuad(VertexConsumer consumer, PoseStack.Pose pose,
                                   Vec3 a, int colorA, Vec3 b, int colorB,
                                   Vec3 c, int colorC, Vec3 d, int colorD) {
        if (!finite(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z)) {
            return;
        }
        Vec3 normal = normalOf(a, b, c);
        vertex(consumer, pose, a, 0.5F, 0.5F, normal, red(colorA), green(colorA), blue(colorA), alpha(colorA));
        vertex(consumer, pose, b, 0.5F, 0.5F, normal, red(colorB), green(colorB), blue(colorB), alpha(colorB));
        vertex(consumer, pose, c, 0.5F, 0.5F, normal, red(colorC), green(colorC), blue(colorC), alpha(colorC));
        vertex(consumer, pose, d, 0.5F, 0.5F, normal, red(colorD), green(colorD), blue(colorD), alpha(colorD));
    }

    private static Vec3 normalOf(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 cross = b.subtract(a).cross(c.subtract(a));
        return cross.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 1.0D, 0.0D) : cross.normalize();
    }

    /**
     * 圆盘（静态 UV）：贴图的内切圆一一对应地铺满整张圆盘，方形贴图的四个角不会跑出来。
     *
     * <p>和 {@link #disc} 的区别在于 UV 不参与「按角度旋转」：这里每个顶点的 UV 就是它自己在
     * 圆盘里的位置，整个盘面采样的是完整的贴图。要让它转，就把位姿绕盘面法线转
     * （{@code pose.mulPose}），几何和贴图一起转。这样无论转多快，贴图都不会被按扇形
     * 拉伸成一条一条的条纹 —— 星门的传送膜就是用它画的。</p>
     */
    public static void discMapped(VertexConsumer consumer, PoseStack.Pose pose, double radius, int segments,
                                  double z, int red, int green, int blue, int centerAlpha, int edgeAlpha) {
        if (radius <= 0.001D || !Double.isFinite(radius)) {
            return;
        }
        float zf = (float) z;
        Vec3 front = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 back = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 center = new Vec3(0.0D, 0.0D, zf);
        Vec3 eye = FxView.eye(pose);
        float face = FxView.plane(eye, center, front, radius);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            Vec3 p0 = new Vec3(c0 * radius, s0 * radius, zf);
            Vec3 p1 = new Vec3(c1 * radius, s1 * radius, zf);
            // UV 与几何一一对应：贴图内切圆正好铺满整张圆盘，怎么转都不会被拉成条
            float u0 = (float) (0.5D + c0 * 0.5D);
            float v0 = (float) (0.5D + s0 * 0.5D);
            float u1 = (float) (0.5D + c1 * 0.5D);
            float v1 = (float) (0.5D + s1 * 0.5D);
            float fade = FxView.mul(face, FxView.near(eye,
                    center.add(p0).add(p1).scale(1.0D / 3.0D),
                    Math.min(radius * 2.0D * Math.PI / segments, radius)));
            int nearCenter = scaleAlpha(centerAlpha, fade);
            int nearEdge = scaleAlpha(edgeAlpha, fade);
            // 扇形三角也必须凑成「四个顶点一个图元」：这些 RenderType 都是 QUADS 模式，
            // 写三个顶点会让后面所有顶点错位重组，屏幕上的彩色乱三角就是这么来的。
            vertex(consumer, pose, center, 0.5F, 0.5F, front, red, green, blue, nearCenter);
            vertex(consumer, pose, p0, u0, v0, front, red, green, blue, nearEdge);
            vertex(consumer, pose, p1, u1, v1, front, red, green, blue, nearEdge);
            vertex(consumer, pose, center, 0.5F, 0.5F, front, red, green, blue, nearCenter);
            vertex(consumer, pose, center, 0.5F, 0.5F, back, red, green, blue, nearCenter);
            vertex(consumer, pose, p1, u1, v1, back, red, green, blue, nearEdge);
            vertex(consumer, pose, p0, u0, v0, back, red, green, blue, nearEdge);
            vertex(consumer, pose, center, 0.5F, 0.5F, back, red, green, blue, nearCenter);
        }
    }

    /**
     * 圆盘（静态 UV + 外缘渐隐）：和 {@link #discMapped} 一样把贴图的内切圆一一铺满圆盘，
     * 但最外圈那一带的顶点色 alpha 收成 {@code edgeAlpha}，让膜的边界柔和地化进背景里。
     *
     * <p>星门的传送膜用它。贴图本身已经在边缘淡出，几何再叠一层淡出，从任何角度看都只有
     * 一张边缘散开的圆形膜，不会出现一条硬邦邦的圆边（之前那版就是被一条白亮的硬边框住的）。</p>
     *
     * @param fadeStart 开始淡出的半径比例（0.2..0.98），例如 0.88 表示最外圈 12% 淡出
     */
    public static void discMappedSoft(VertexConsumer consumer, PoseStack.Pose pose, double radius,
                                      double fadeStart, int segments, double z,
                                      int red, int green, int blue, int centerAlpha, int edgeAlpha) {
        if (radius <= 0.001D || !Double.isFinite(radius) || !Double.isFinite(fadeStart) || segments < 3) {
            return;
        }
        double plate = Math.max(0.2D, Math.min(0.98D, fadeStart));
        double inner = radius * plate;
        float zf = (float) z;
        Vec3 front = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 back = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 center = new Vec3(0.0D, 0.0D, zf);
        Vec3 eye = FxView.eye(pose);
        float face = FxView.plane(eye, center, front, radius);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            Vec3 in0 = new Vec3(c0 * inner, s0 * inner, zf);
            Vec3 in1 = new Vec3(c1 * inner, s1 * inner, zf);
            Vec3 out0 = new Vec3(c0 * radius, s0 * radius, zf);
            Vec3 out1 = new Vec3(c1 * radius, s1 * radius, zf);
            // UV 与几何按同一比例对应，贴图不会被压缩，也不会露出方角
            float iu0 = (float) (0.5D + c0 * 0.5D * plate);
            float iv0 = (float) (0.5D + s0 * 0.5D * plate);
            float iu1 = (float) (0.5D + c1 * 0.5D * plate);
            float iv1 = (float) (0.5D + s1 * 0.5D * plate);
            float ou0 = (float) (0.5D + c0 * 0.5D);
            float ov0 = (float) (0.5D + s0 * 0.5D);
            float ou1 = (float) (0.5D + c1 * 0.5D);
            float ov1 = (float) (0.5D + s1 * 0.5D);
            float fade = FxView.mul(face, FxView.near(eye,
                    in0.add(in1).add(out0).add(out1).scale(0.25D),
                    Math.min(radius * 2.0D * Math.PI / segments, radius - inner)));
            int nearCenter = scaleAlpha(centerAlpha, fade);
            int nearEdge = scaleAlpha(edgeAlpha, fade);
            // 圆心到淡出圈：整块保持 centerAlpha（正面 / 背面各一遍；三角同样补成四顶点图元）
            vertex(consumer, pose, center, 0.5F, 0.5F, front, red, green, blue, nearCenter);
            vertex(consumer, pose, in0, iu0, iv0, front, red, green, blue, nearCenter);
            vertex(consumer, pose, in1, iu1, iv1, front, red, green, blue, nearCenter);
            vertex(consumer, pose, center, 0.5F, 0.5F, front, red, green, blue, nearCenter);
            vertex(consumer, pose, center, 0.5F, 0.5F, back, red, green, blue, nearCenter);
            vertex(consumer, pose, in1, iu1, iv1, back, red, green, blue, nearCenter);
            vertex(consumer, pose, in0, iu0, iv0, back, red, green, blue, nearCenter);
            vertex(consumer, pose, center, 0.5F, 0.5F, back, red, green, blue, nearCenter);
            // 淡出带：从 centerAlpha 收到 edgeAlpha，边界就此化开
            vertex(consumer, pose, in0, iu0, iv0, front, red, green, blue, nearCenter);
            vertex(consumer, pose, out0, ou0, ov0, front, red, green, blue, nearEdge);
            vertex(consumer, pose, out1, ou1, ov1, front, red, green, blue, nearEdge);
            vertex(consumer, pose, in1, iu1, iv1, front, red, green, blue, nearCenter);
            vertex(consumer, pose, in1, iu1, iv1, back, red, green, blue, nearCenter);
            vertex(consumer, pose, out1, ou1, ov1, back, red, green, blue, nearEdge);
            vertex(consumer, pose, out0, ou0, ov0, back, red, green, blue, nearEdge);
            vertex(consumer, pose, in0, iu0, iv0, back, red, green, blue, nearCenter);
        }
    }

    /**
     * 圆盘（极坐标 UV）：UV 只取贴图里内切圆的范围，并可以按 {@code uvRotation} 旋转。
     *
     * <p>吸积盘用它：圆盘几何不动，贴图在圆盘里面转。逐扇形的 UV 旋转在贴图花纹细碎时
     * 会把图案扯成放射状的条带，所以星门的整张传送膜改用 {@link #discMapped}。</p>
     */
    public static void disc(VertexConsumer consumer, PoseStack.Pose pose, double radius, int segments,
                            float uvRadius, float uvRotation, double z,
                            int red, int green, int blue, int centerAlpha, int edgeAlpha) {
        if (radius <= 0.001D || !Double.isFinite(radius) || !Float.isFinite(uvRadius)) {
            return;
        }
        float zf = (float) z;
        Vec3 front = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 back = new Vec3(0.0D, 0.0D, -1.0D);
        Vec3 center = new Vec3(0.0D, 0.0D, zf);
        Vec3 eye = FxView.eye(pose);
        float face = FxView.plane(eye, center, front, radius);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            float x0 = (float) (Math.cos(a0) * radius);
            float y0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float y1 = (float) (Math.sin(a1) * radius);
            float u0 = (float) (0.5D + Math.cos(a0 + uvRotation) * uvRadius);
            float v0 = (float) (0.5D + Math.sin(a0 + uvRotation) * uvRadius);
            float u1 = (float) (0.5D + Math.cos(a1 + uvRotation) * uvRadius);
            float v1 = (float) (0.5D + Math.sin(a1 + uvRotation) * uvRadius);
            Vec3 p0 = new Vec3(x0, y0, zf);
            Vec3 p1 = new Vec3(x1, y1, zf);
            float fade = FxView.mul(face, FxView.near(eye,
                    center.add(p0).add(p1).scale(1.0D / 3.0D),
                    Math.min(radius * 2.0D * Math.PI / segments, radius)));
            int nearCenter = scaleAlpha(centerAlpha, fade);
            int nearEdge = scaleAlpha(edgeAlpha, fade);
            // 正面、背面各一遍（背面反绕序，剔除开着也看得见），从门的另一侧也有门面
            vertex(consumer, pose, center, 0.5F, 0.5F, front, red, green, blue, nearCenter);
            vertex(consumer, pose, p0, u0, v0, front, red, green, blue, nearEdge);
            vertex(consumer, pose, p1, u1, v1, front, red, green, blue, nearEdge);
            vertex(consumer, pose, center, 0.5F, 0.5F, front, red, green, blue, nearCenter);
            vertex(consumer, pose, center, 0.5F, 0.5F, back, red, green, blue, nearCenter);
            vertex(consumer, pose, p1, u1, v1, back, red, green, blue, nearEdge);
            vertex(consumer, pose, p0, u0, v0, back, red, green, blue, nearEdge);
            vertex(consumer, pose, center, 0.5F, 0.5F, back, red, green, blue, nearCenter);
        }
    }

    /** 任意平面上的环带，内缘与外缘各一种颜色（{@code u}、{@code v} 是平面内两个互相垂直的单位向量）。 */
    public static void ringBand(VertexConsumer consumer, PoseStack.Pose pose, Vec3 u, Vec3 v,
                                double innerRadius, double outerRadius, int segments,
                                int innerColor, int outerColor) {
        if (segments < 3 || !finite(innerRadius, outerRadius) || innerRadius < 0.0D
                || outerRadius <= innerRadius) {
            return;
        }
        Vec3 eye = FxView.eye(pose);
        float face = FxView.plane(eye, Vec3.ZERO, u.cross(v), outerRadius);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 in0 = u.scale(Math.cos(a0) * innerRadius).add(v.scale(Math.sin(a0) * innerRadius));
            Vec3 out0 = u.scale(Math.cos(a0) * outerRadius).add(v.scale(Math.sin(a0) * outerRadius));
            Vec3 in1 = u.scale(Math.cos(a1) * innerRadius).add(v.scale(Math.sin(a1) * innerRadius));
            Vec3 out1 = u.scale(Math.cos(a1) * outerRadius).add(v.scale(Math.sin(a1) * outerRadius));
            Vec3 mid = in0.add(in1).add(out0).add(out1).scale(0.25D);
            float fade = FxView.mul(face, FxView.near(eye, mid,
                    Math.min(outerRadius * 2.0D * Math.PI / segments, outerRadius - innerRadius)));
            int depthIn = FxView.fade(innerColor, fade);
            int depthOut = FxView.fade(outerColor, fade);
            coloredQuad(consumer, pose, in0, depthIn, out0, depthOut, out1, depthOut, in1, depthIn);
            coloredQuad(consumer, pose, in1, depthIn, out1, depthOut, out0, depthOut, in0, depthIn);
        }
    }

    /** 一圈柔光光环：内侧透明、正中 {@code radius} 处最亮、外侧再透明。 */
    public static void glowRing(VertexConsumer consumer, PoseStack.Pose pose, Vec3 u, Vec3 v,
                                double radius, double thickness, int segments, int color) {
        double half = Math.max(0.01D, thickness * 0.5D);
        int clear = color & 0x00FFFFFF;
        ringBand(consumer, pose, u, v, Math.max(0.0D, radius - half), radius, segments, clear, color);
        ringBand(consumer, pose, u, v, radius, radius + half, segments, color, clear);
    }

    /**
     * 贴图环带：把「环」形状的贴图按半径铺到环带上（星门内缘的等离子环用它）。
     *
     * <p>顶点 UV 沿圆周取 <code>(0.5 + cos·uv, 0.5 + sin·uv)</code>：贴图里半径
     * {@code uvInner}..{@code uvOuter} 的那一圈花纹，正好铺满几何上的
     * {@code innerRadius}..{@code outerRadius}。{@code uvSpin} 让花纹沿圆周自转，
     * 所以环带会「流」起来，而不是一张静止的图。</p>
     */
    public static void radialBand(VertexConsumer consumer, PoseStack.Pose pose, Vec3 u, Vec3 v,
                                  double innerRadius, double outerRadius, int segments,
                                  float uvInner, float uvOuter, double uvSpin,
                                  int innerColor, int outerColor) {
        if (segments < 3 || !finite(innerRadius, outerRadius, uvSpin)
                || !Float.isFinite(uvInner) || !Float.isFinite(uvOuter)
                || innerRadius < 0.0D || outerRadius <= innerRadius) {
            return;
        }
        Vec3 normal = u.cross(v);
        if (normal.lengthSqr() < 1.0E-8D) {
            return;
        }
        normal = normal.normalize();
        Vec3 back = normal.scale(-1.0D);
        Vec3 eye = FxView.eye(pose);
        float face = FxView.plane(eye, Vec3.ZERO, normal, outerRadius);
        int innerRed = red(innerColor);
        int innerGreen = green(innerColor);
        int innerBlue = blue(innerColor);
        int innerAlpha = alpha(innerColor);
        int outerRed = red(outerColor);
        int outerGreen = green(outerColor);
        int outerBlue = blue(outerColor);
        int outerAlpha = alpha(outerColor);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 in0 = u.scale(Math.cos(a0) * innerRadius).add(v.scale(Math.sin(a0) * innerRadius));
            Vec3 out0 = u.scale(Math.cos(a0) * outerRadius).add(v.scale(Math.sin(a0) * outerRadius));
            Vec3 in1 = u.scale(Math.cos(a1) * innerRadius).add(v.scale(Math.sin(a1) * innerRadius));
            Vec3 out1 = u.scale(Math.cos(a1) * outerRadius).add(v.scale(Math.sin(a1) * outerRadius));
            Vec3 mid = in0.add(in1).add(out0).add(out1).scale(0.25D);
            float fade = FxView.mul(face, FxView.near(eye, mid,
                    Math.min(outerRadius * 2.0D * Math.PI / segments, outerRadius - innerRadius)));
            int fadeIn = scaleAlpha(innerAlpha, fade);
            int fadeOut = scaleAlpha(outerAlpha, fade);
            float iu0 = (float) (0.5D + Math.cos(a0 + uvSpin) * uvInner);
            float iv0 = (float) (0.5D + Math.sin(a0 + uvSpin) * uvInner);
            float ou0 = (float) (0.5D + Math.cos(a0 + uvSpin) * uvOuter);
            float ov0 = (float) (0.5D + Math.sin(a0 + uvSpin) * uvOuter);
            float iu1 = (float) (0.5D + Math.cos(a1 + uvSpin) * uvInner);
            float iv1 = (float) (0.5D + Math.sin(a1 + uvSpin) * uvInner);
            float ou1 = (float) (0.5D + Math.cos(a1 + uvSpin) * uvOuter);
            float ov1 = (float) (0.5D + Math.sin(a1 + uvSpin) * uvOuter);
            // 正面（法线朝向 u×v）
            vertex(consumer, pose, in0, iu0, iv0, normal,
                    innerRed, innerGreen, innerBlue, fadeIn);
            vertex(consumer, pose, out0, ou0, ov0, normal,
                    outerRed, outerGreen, outerBlue, fadeOut);
            vertex(consumer, pose, out1, ou1, ov1, normal,
                    outerRed, outerGreen, outerBlue, fadeOut);
            vertex(consumer, pose, in1, iu1, iv1, normal,
                    innerRed, innerGreen, innerBlue, fadeIn);
            // 背面（反绕序，从门的另一侧也看得见）
            vertex(consumer, pose, in1, iu1, iv1, back,
                    innerRed, innerGreen, innerBlue, fadeIn);
            vertex(consumer, pose, out1, ou1, ov1, back,
                    outerRed, outerGreen, outerBlue, fadeOut);
            vertex(consumer, pose, out0, ou0, ov0, back,
                    outerRed, outerGreen, outerBlue, fadeOut);
            vertex(consumer, pose, in0, iu0, iv0, back,
                    innerRed, innerGreen, innerBlue, fadeIn);
        }
    }

    /** 分段换色的环：转起来一眼就能看出环在动。 */
    public static void bandRing(VertexConsumer consumer, PoseStack.Pose pose, Vec3 u, Vec3 v,
                                double innerRadius, double outerRadius, int segments, int period,
                                int colorA, int colorB) {
        if (segments < 3 || period <= 0) {
            return;
        }
        Vec3 eye = FxView.eye(pose);
        float face = FxView.plane(eye, Vec3.ZERO, u.cross(v), outerRadius);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            int color = (i / period) % 2 == 0 ? colorA : colorB;
            Vec3 in0 = u.scale(Math.cos(a0) * innerRadius).add(v.scale(Math.sin(a0) * innerRadius));
            Vec3 out0 = u.scale(Math.cos(a0) * outerRadius).add(v.scale(Math.sin(a0) * outerRadius));
            Vec3 in1 = u.scale(Math.cos(a1) * innerRadius).add(v.scale(Math.sin(a1) * innerRadius));
            Vec3 out1 = u.scale(Math.cos(a1) * outerRadius).add(v.scale(Math.sin(a1) * outerRadius));
            Vec3 mid = in0.add(in1).add(out0).add(out1).scale(0.25D);
            int depth = FxView.fade(color, FxView.mul(face, FxView.near(eye, mid,
                    Math.min(outerRadius * 2.0D * Math.PI / segments, outerRadius - innerRadius))));
            coloredQuad(consumer, pose, in0, depth, out0, depth, out1, depth, in1, depth);
            coloredQuad(consumer, pose, in1, depth, out1, depth, out0, depth, in0, depth);
        }
    }

    /** 球（黑洞视界）。 */
    public static void sphere(VertexConsumer consumer, PoseStack.Pose pose, double radius, int latitudes,
                              int longitudes, int red, int green, int blue, int alpha) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return;
        }
        Vec3 eye = FxView.eye(pose);
        for (int lat = 0; lat < latitudes; lat++) {
            double phi0 = Math.PI * lat / latitudes;
            double phi1 = Math.PI * (lat + 1) / latitudes;
            for (int lon = 0; lon < longitudes; lon++) {
                double theta0 = Math.PI * 2.0D * lon / longitudes;
                double theta1 = Math.PI * 2.0D * (lon + 1) / longitudes;
                Vec3 a = spherePoint(radius, phi0, theta0);
                Vec3 b = spherePoint(radius, phi0, theta1);
                Vec3 c = spherePoint(radius, phi1, theta1);
                Vec3 d = spherePoint(radius, phi1, theta0);
                float u0 = (float) lon / longitudes;
                float u1 = (float) (lon + 1) / longitudes;
                float v0 = (float) lat / latitudes;
                float v1 = (float) (lat + 1) / latitudes;
                int near = scaleAlpha(alpha, FxView.near(eye, a.add(b).add(c).add(d).scale(0.25D),
                        radius * Math.min(2.0D * Math.PI / longitudes, Math.PI / latitudes)));
                quad(consumer, pose, a, b, c, d, u0, v0, u1, v1, red, green, blue, near);
            }
        }
    }

    private static Vec3 spherePoint(double radius, double phi, double theta) {
        double sinPhi = Math.sin(phi);
        return new Vec3(radius * sinPhi * Math.cos(theta), radius * Math.cos(phi),
                radius * sinPhi * Math.sin(theta));
    }

    /** 圆柱形光束（两端同宽）。 */
    public static void beam(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end, double radius,
                            int red, int green, int blue, int alpha) {
        beamTapered(consumer, pose, start, end, radius, radius, red, green, blue, alpha);
    }

    /** 锥形光束：两端半径不同，激光从炮口粗、撞到黑洞收细，看起来才是「射出去」的。 */
    public static void beamTapered(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
                                   double startRadius, double endRadius,
                                   int red, int green, int blue, int alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 unit = direction.normalize();
        Vec3 up = Math.abs(unit.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side0 = unit.cross(up).normalize();
        Vec3 side1 = unit.cross(side0).normalize();
        int nearAlpha = scaleAlpha(alpha, FxView.beam(FxView.eye(pose), start, end,
                Math.max(startRadius, endRadius)));
        Vec3 a0 = start.add(side0.scale(startRadius));
        Vec3 b0 = start.subtract(side0.scale(startRadius));
        Vec3 c0 = end.subtract(side0.scale(endRadius));
        Vec3 d0 = end.add(side0.scale(endRadius));
        Vec3 a1 = start.add(side1.scale(startRadius));
        Vec3 b1 = start.subtract(side1.scale(startRadius));
        Vec3 c1 = end.subtract(side1.scale(endRadius));
        Vec3 d1 = end.add(side1.scale(endRadius));
        quad(consumer, pose, a0, b0, c0, d0, 0.0F, 0.0F, 1.0F, 1.0F, red, green, blue, nearAlpha);
        quad(consumer, pose, b0, a0, d0, c0, 0.0F, 0.0F, 1.0F, 1.0F, red, green, blue, nearAlpha);
        quad(consumer, pose, a1, b1, c1, d1, 0.0F, 0.0F, 1.0F, 1.0F, red, green, blue, nearAlpha);
        quad(consumer, pose, b1, a1, d1, c1, 0.0F, 0.0F, 1.0F, 1.0F, red, green, blue, nearAlpha);
    }

    /** 贴图光束：UV 沿光束长度铺开并按 {@code vScroll} 滚动，所以能看到激光材质本身在流。 */
    public static void beamStrip(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
                                 double startHalfWidth, double endHalfWidth,
                                 float uRepeat, float vScroll, float vScale,
                                 int red, int green, int blue, int alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 unit = direction.normalize();
        Vec3 up = Math.abs(unit.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = unit.cross(up).normalize();
        Vec3 vertical = unit.cross(side).normalize();
        float v0 = vScroll;
        float v1 = vScroll + vScale;
        int nearAlpha = scaleAlpha(alpha, FxView.beam(FxView.eye(pose), start, end,
                Math.max(startHalfWidth, endHalfWidth)));
        if (nearAlpha <= 1) {
            return;
        }
        for (Vec3 across : new Vec3[]{side, vertical}) {
            Vec3 s0 = across.scale(startHalfWidth);
            Vec3 e0 = across.scale(endHalfWidth);
            Vec3 a = start.add(s0);
            Vec3 b = start.subtract(s0);
            Vec3 c = end.subtract(e0);
            Vec3 d = end.add(e0);
            quad(consumer, pose, a, b, c, d, 0.0F, v0, uRepeat, v1, red, green, blue, nearAlpha);
        }
    }

    /** 纯色光束：颜色完全来自顶点色，画出来一定有一条明确的亮芯。 */
    public static void beamStripFlat(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
                                     double startHalfWidth, double endHalfWidth,
                                     int red, int green, int blue, int alpha) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        int faded = scaleAlpha(alpha, FxView.beam(FxView.eye(pose), start, end,
                Math.max(startHalfWidth, endHalfWidth)));
        int color = ((faded & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
        if (faded <= 1) {
            return;
        }
        Vec3 unit = direction.normalize();
        Vec3 up = Math.abs(unit.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = unit.cross(up).normalize();
        Vec3 vertical = unit.cross(side).normalize();
        for (Vec3 across : new Vec3[]{side, vertical}) {
            Vec3 s0 = across.scale(startHalfWidth);
            Vec3 e0 = across.scale(endHalfWidth);
            coloredQuad(consumer, pose, start.add(s0), color, start.subtract(s0), color,
                    end.subtract(e0), color, end.add(e0), color);
        }
    }

    /**
     * 一片正对 +Z 的柔光面：整张柔光贴图铺在面片上，贴图四角本来就是全透明的，
     * 所以画出来永远是一团软光，边缘不会有方角。
     *
     * <p>配合 {@link #billboard(PoseStack)} 使用时，它就是「永远正对镜头的一团光」——
     * 不像 {@link #glowCross} 的三向硬面片那样，在某些角度会切成一条亮边。</p>
     */
    public static void glowQuad(VertexConsumer consumer, PoseStack.Pose pose, double half,
                                int red, int green, int blue, int alpha) {
        if (!Double.isFinite(half) || half <= 0.0D) {
            return;
        }
        int near = scaleAlpha(alpha, FxView.thin(FxView.eye(pose), Vec3.ZERO, half));
        quad(consumer, pose, new Vec3(-half, -half, 0.0D), new Vec3(half, -half, 0.0D),
                new Vec3(half, half, 0.0D), new Vec3(-half, half, 0.0D), 0.0F, 0.0F, 1.0F, 1.0F,
                red, green, blue, near);
    }

    /** 以原点为中心的三向发光片，用来画激光稳定器的核心、符文珠、炮口闪光。 */
    public static void glowCross(VertexConsumer consumer, PoseStack.Pose pose, double half,
                                 int red, int green, int blue, int alpha) {
        int near = scaleAlpha(alpha, FxView.thin(FxView.eye(pose), Vec3.ZERO, half));
        quad(consumer, pose, new Vec3(-half, -half, 0.0D), new Vec3(half, -half, 0.0D),
                new Vec3(half, half, 0.0D), new Vec3(-half, half, 0.0D), 0.0F, 0.0F, 1.0F, 1.0F,
                red, green, blue, near);
        quad(consumer, pose, new Vec3(-half, 0.0D, -half), new Vec3(half, 0.0D, -half),
                new Vec3(half, 0.0D, half), new Vec3(-half, 0.0D, half), 0.0F, 0.0F, 1.0F, 1.0F,
                red, green, blue, near);
        quad(consumer, pose, new Vec3(0.0D, -half, -half), new Vec3(0.0D, -half, half),
                new Vec3(0.0D, half, half), new Vec3(0.0D, half, -half), 0.0F, 0.0F, 1.0F, 1.0F,
                red, green, blue, near);
    }

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    /** 只缩放 alpha（0..255），用于「贴到镜头前就淡下去」。 */
    private static int scaleAlpha(int value, float factor) {
        float f = net.minecraft.util.Mth.clamp(factor, 0.0F, 1.0F);
        return net.minecraft.util.Mth.clamp((int) (value * f), 0, 255);
    }

    private static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }
}
