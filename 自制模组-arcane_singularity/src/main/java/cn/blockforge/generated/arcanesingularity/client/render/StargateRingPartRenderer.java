package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.entity.StargateRingPartEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 星门环碰撞实体的渲染器：实体本身不画任何东西，门框方块由星门控制器的方块实体渲染器统一绘制，
 * 这里只保留类型注册。
 */
public class StargateRingPartRenderer extends EntityRenderer<StargateRingPartEntity> {
    public StargateRingPartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(StargateRingPartEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(StargateRingPartEntity entity) {
        return RenderFx.BEAM;
    }
}
