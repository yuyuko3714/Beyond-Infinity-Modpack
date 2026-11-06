package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 世界之核用到的贴图，以及它们的绘制层（层的定义统一在 {@link FxLayers}）。
 *
 * <p>这一版不再自己拼 {@code RenderType.CompositeState}、不再要求顶点里带光照：
 * 之前的写法用的是 {@code POSITION_COLOR_TEX_LIGHTMAP} + {@code position_color_tex_lightmap}，
 * 片元要多采一个 Sampler2（光照贴图）。玩家整合包里这套采样器对不上时，贴图就会被采成别的图，
 * 屏幕上就是一片和场景无关的彩色色块——也就是他一直说的「花屏」。</p>
 *
 * <p>现在只走原版 Core Shader 的两个最简单程序：</p>
 * <ul>
 *     <li>{@link FxLayers#additive} —— 加法混合，用于激光、火花、能量核；</li>
 *     <li>{@link FxLayers#blended} —— 常规半透明，用于魔法阵本体，
 *         在明亮的天空前也能看清金色 / 青色的花纹。</li>
 * </ul>
 * <p>两个层都是 {@code position_color_tex} + 原版 {@code POSITION_COLOR_TEX} 顶点格式，
 * 不写深度、双面，所以魔法阵与激光从任何角度看都是完整的，不会互相切出硬边。</p>
 */
public final class FxRenderTypes {
    /** 外阵：双环 + 96 刻度 + 24 符文 + 六芒星 + 花瓣带。 */
    public static final ResourceLocation GLYPH_OUTER = rl("textures/effect/world_core_glyph_outer.png");
    /** 中阵：反向转的青色法环。 */
    public static final ResourceLocation GLYPH_MID = rl("textures/effect/world_core_glyph_mid.png");
    /** 心阵：最中心的小符印。 */
    public static final ResourceLocation GLYPH_CORE = rl("textures/effect/world_core_glyph_core.png");
    /** 竖条纹光带：激光柱、中轴光柱用。 */
    public static final ResourceLocation BEAM = rl("textures/effect/laser_beam.png");
    /**
     * 一团柔光：光斑、炮口闪光、阵心辉光用。
     *
     * <p>这里必须是「中心亮、alpha 平滑衰减到 0」的单个圆光斑。以前用的是
     * {@code laser_spark.png}——那张其实是「中心 + 四角」共五个光点，整张铺到面片上
     * 就会在光球旁边多出四个亮点、阵底多出一块方形的点阵板，看起来就是花屏。</p>
     */
    public static final ResourceLocation SPARK = rl("textures/effect/soft_glow.png");
    /** 纯白：纯色几何（细环、亮芯、能量丝）采样它，颜色全交给顶点色。 */
    public static final ResourceLocation FLAT = rl("textures/effect/flat_white.png");

    private FxRenderTypes() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ArcaneSingularity.MOD_ID, path);
    }

    /** 加法混合层：激光、火花、能量核。 */
    public static RenderType additive(ResourceLocation texture) {
        return FxLayers.additive(texture);
    }

    /** 常规半透明层：魔法阵本体。 */
    public static RenderType blended(ResourceLocation texture) {
        return FxLayers.blended(texture);
    }
}
