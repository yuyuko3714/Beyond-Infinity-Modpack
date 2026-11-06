package cn.blockforge.generated.arcanesingularity.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全模组唯一的光影绘制层（r16 那一版写法，玩家机上验证过不再花屏）。
 *
 * <p><b>为什么不用 {@code RenderType.entityTranslucentEmissive} 这一类层：</b>
 * 那些层跑的是 {@code rendertype_entity_*} 系列着色器，顶点格式是 {@code NEW_ENTITY}，
 * 需要 overlay / lightmap / 法线三组额外数据，片元还要采样 Sampler2（光照贴图）。
 * 在玩家的整合包里这套实体着色器被重新编译坏了（日志里就是
 * {@code Shader rendertype_entity_translucent_emissive could not find sampler named Sampler2}），
 * 于是贴图被采成别的图、颜色变成随机的值，屏幕上是和场景毫不相干的彩色色块；
 * 再加上实体层会<b>写深度</b>，几百片互相重叠的半透明几何在特定角度会互相切出硬边，
 * 看起来就是「换个角度就花屏」。换多少个叠加层都修不好。</p>
 *
 * <p><b>这里只用原版 Core Shader + 原版顶点格式，而且只有两种，边界清清楚楚：</b></p>
 * <ul>
 *     <li>所有<b>四边形</b>——{@code position_color_tex} + {@code POSITION_COLOR_TEX}，
 *         顶点写「位置 + 颜色 + UV」。要纯色就采样 {@code flat_white.png}，颜色全交给顶点色；</li>
 *     <li>所有<b>线段</b>——{@code position_color} + {@code POSITION_COLOR}，
 *         顶点写「位置 + 颜色 + 法线」。</li>
 * </ul>
 * <p>两个程序都不带光照、不带 overlay、不需要额外的采样器；贴图由层自己
 * （{@link RenderStateShard.TextureStateShard}）直接绑 PNG，不经过方块图集。
 * 顶点里没有的字段一个都不会写，{@code endVertex()} 也就永远不可能因为漏字段而错位——
 * 之前「顶点被按错误步长解读、位置飞出去」的那一类花屏从此不存在。</p>
 *
 * <p><b>深度策略：</b>所有叠加层都<b>只写颜色、不写深度</b>（{@code COLOR_ONLY}）。
 * 光晕、光子环、吸积盘、激光、魔法阵之间无论谁先画，都不会把彼此切掉，
 * 换成任何角度都不会出现互相掏洞、条纹或硬边。深度测试仍然是 {@code <=}，
 * 所以被墙挡住的几何照样挡得住。</p>
 */
public final class FxLayers {
    /** 单个层一次能装下的字节数；不够时缓冲会自动扩容，给宽一点省得频繁扩。 */
    private static final int BUFFER_SIZE = 32768;

    /** 半透明：常规 alpha 混合，图案在亮背景前也能看清本来的颜色。 */
    private static final RenderStateShard.TransparencyStateShard BLEND =
            new RenderStateShard.TransparencyStateShard("arcane_blend", () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }, RenderSystem::disableBlend);

    /** 加法混合：颜色乘 alpha 后直接叠上去，是「光」的感觉。 */
    private static final RenderStateShard.TransparencyStateShard ADDITIVE =
            new RenderStateShard.TransparencyStateShard("arcane_additive", () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE);
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });

    private static final RenderStateShard.DepthTestStateShard DEPTH_LEQUAL =
            new RenderStateShard.DepthTestStateShard("<= ", 515);
    private static final RenderStateShard.CullStateShard NO_CULL =
            new RenderStateShard.CullStateShard(false);
    private static final RenderStateShard.LightmapStateShard NO_LIGHTMAP =
            new RenderStateShard.LightmapStateShard(false);
    private static final RenderStateShard.OverlayStateShard NO_OVERLAY =
            new RenderStateShard.OverlayStateShard(false);
    /** 只写颜色：叠加层之间互不遮挡。 */
    private static final RenderStateShard.WriteMaskStateShard COLOR_ONLY =
            new RenderStateShard.WriteMaskStateShard(true, false);
    private static final RenderStateShard.ShaderStateShard POSITION_COLOR_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader);
    private static final RenderStateShard.ShaderStateShard POSITION_COLOR_TEX_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader);

    /** 线段层：原版 {@code POSITION_COLOR}（位置 + 颜色 + 法线）+ {@code DEBUG_LINES}。 */
    public static final RenderType LINES = RenderType.create("arcane_lines",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.DEBUG_LINES, BUFFER_SIZE, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(BLEND)
                    .setDepthTestState(DEPTH_LEQUAL)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_ONLY)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> BLENDED = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> ADDITIVES = new ConcurrentHashMap<>();
    private static RenderType ATLAS;

    private FxLayers() {
    }

    /** 半透明贴图层：只写颜色、不写深度，叠加层不会互相吞掉。 */
    public static RenderType blended(ResourceLocation texture) {
        return BLENDED.computeIfAbsent(texture, key -> create("arcane_blend", key,
                VertexFormat.Mode.QUADS, BLEND, true));
    }

    /** 加法混合贴图层：激光、火花、光斑这类「光」。 */
    public static RenderType additive(ResourceLocation texture) {
        return ADDITIVES.computeIfAbsent(texture, key -> create("arcane_add", key,
                VertexFormat.Mode.QUADS, ADDITIVE, true));
    }

    /**
     * 方块图集层：多方块投影虚影用，顶点由调用方自己从烘焙模型里摊出来。
     *
     * <p>这一层<b>不做线性过滤</b>：方块图集的过滤方式由原版按视频设置里的「mipmap 级别」管，
     * 我们要是把它改成线性、又不带 mip，远处整个世界的方块都会开始闪；下一帧原版自己还会把它改回去，
     * 一帧一个样更糟。所以图集层保持原版默认。</p>
     */
    public static RenderType atlas() {
        if (ATLAS == null) {
            ATLAS = create("arcane_atlas", TextureAtlas.LOCATION_BLOCKS,
                    VertexFormat.Mode.QUADS, BLEND, false);
        }
        return ATLAS;
    }

    /**
     * {@code blur} = 线性过滤。魔法阵图是 1024 的细线花纹、光带贴图是细条纹，
     * 侧着看或离得远时会被缩到很小：最近邻取样会把细纹采成一块块跳动的方块（看起来就是花屏），
     * 线性过滤做一次四邻域平均，细纹化开就不闪了。
     *
     * <p>不开 mipmap：独立 PNG 没有 mip 链，开了会采到不完整的贴图（整片变黑）。</p>
     */
    private static RenderType create(String prefix, ResourceLocation texture,
                                     VertexFormat.Mode mode,
                                     RenderStateShard.TransparencyStateShard transparency,
                                     boolean blur) {
        return RenderType.create(layerName(prefix, texture), DefaultVertexFormat.POSITION_COLOR_TEX,
                mode, BUFFER_SIZE, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, blur, false))
                        .setTransparencyState(transparency)
                        .setDepthTestState(DEPTH_LEQUAL)
                        .setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP)
                        .setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(COLOR_ONLY)
                        .createCompositeState(false));
    }

    private static String layerName(String prefix, ResourceLocation texture) {
        return prefix + "_" + texture.getNamespace() + "_"
                + texture.getPath().replace('/', '_').replace('.', '_');
    }
}
