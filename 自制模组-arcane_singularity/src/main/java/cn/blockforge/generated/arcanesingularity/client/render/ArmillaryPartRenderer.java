package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.entity.ArmillaryPartEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 浑天仪碰撞实体的渲染器：实体本身不画任何东西，环、稳定器和激光都由控制器的
 * 方块实体渲染器统一绘制，这里只保留类型注册。
 */
public class ArmillaryPartRenderer extends EntityRenderer<ArmillaryPartEntity> {
    public ArmillaryPartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(ArmillaryPartEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(ArmillaryPartEntity entity) {
        return RenderFx.BEAM;
    }
}
