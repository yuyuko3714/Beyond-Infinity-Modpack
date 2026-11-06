package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.menu.SingularityMenu;
import cn.blockforge.generated.arcanesingularity.menu.StargateMenu;
import cn.blockforge.generated.arcanesingularity.menu.TimeBeaconMenu;
import cn.blockforge.generated.arcanesingularity.menu.WorldCoreMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ArcaneSingularity.MOD_ID);

    @SuppressWarnings("unchecked")
    public static final RegistryObject<MenuType<SingularityMenu>> SINGULARITY = MENUS.register("singularity",
            () -> (MenuType<SingularityMenu>) IForgeMenuType.create(
                    (windowId, inventory, data) -> new SingularityMenu(windowId, inventory, data)));

    @SuppressWarnings("unchecked")
    public static final RegistryObject<MenuType<StargateMenu>> STARGATE = MENUS.register("stargate",
            () -> (MenuType<StargateMenu>) IForgeMenuType.create(
                    (windowId, inventory, data) -> new StargateMenu(windowId, inventory, data)));

    @SuppressWarnings("unchecked")
    public static final RegistryObject<MenuType<TimeBeaconMenu>> TIME_BEACON = MENUS.register("time_beacon",
            () -> (MenuType<TimeBeaconMenu>) IForgeMenuType.create(
                    (windowId, inventory, data) -> new TimeBeaconMenu(windowId, inventory, data)));

    @SuppressWarnings("unchecked")
    public static final RegistryObject<MenuType<WorldCoreMenu>> WORLD_CORE = MENUS.register("world_core",
            () -> (MenuType<WorldCoreMenu>) IForgeMenuType.create(
                    (windowId, inventory, data) -> new WorldCoreMenu(windowId, inventory, data)));

    private ModMenus() {
    }
}