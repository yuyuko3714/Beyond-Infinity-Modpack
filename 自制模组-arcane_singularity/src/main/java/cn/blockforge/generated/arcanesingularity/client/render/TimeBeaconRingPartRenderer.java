package cn.blockforge.generated.arcanesingularity.client.render;

import cn.blockforge.generated.arcanesingularity.entity.TimeBeaconRingPartEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 时间信标汇聚环碰撞实体的渲染器：实体本身不画任何东西，环方块、能量带与激光柱都由控制器的
 * 方块实体渲染器统一绘制，这里只保留类型注册。
 */
public class TimeBeaconRingPartRenderer extends EntityRenderer<TimeBeaconRingPartEntity> {
    public TimeBeaconRingPartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public boolean shouldRender(TimeBeaconRingPartEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(TimeBeaconRingPartEntity entity) {
        return RenderFx.BEAM;
    }
}
