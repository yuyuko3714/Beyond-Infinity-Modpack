package cn.blockforge.generated.arcanesingularity.registry;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.item.ArcaneCodexItem;
import cn.blockforge.generated.arcanesingularity.item.FormationTerminalItem;
import cn.blockforge.generated.arcanesingularity.item.LoreItem;
import cn.blockforge.generated.arcanesingularity.item.LoreBlockItem;
import cn.blockforge.generated.arcanesingularity.item.PairingRodItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneSingularity.MOD_ID);

    public static final RegistryObject<Item> RUNE_BRICKS = blockItem("rune_bricks", ModBlocks.RUNE_BRICKS);
    public static final RegistryObject<Item> CHISELED_RUNE_BRICKS =
            blockItem("chiseled_rune_bricks", ModBlocks.CHISELED_RUNE_BRICKS);
    public static final RegistryObject<Item> ARCANE_CRYSTAL_BLOCK =
            blockItem("arcane_crystal_block", ModBlocks.ARCANE_CRYSTAL_BLOCK);
    public static final RegistryObject<Item> RUNE_PILLAR = blockItem("rune_pillar", ModBlocks.RUNE_PILLAR);
    public static final RegistryObject<Item> ARMILLARY_RING = blockItem("armillary_ring", ModBlocks.ARMILLARY_RING);
    public static final RegistryObject<Item> LASER_INJECTOR = blockItem("laser_injector", ModBlocks.LASER_INJECTOR);
    public static final RegistryObject<Item> LASER_STABILIZER =
            blockItem("laser_stabilizer", ModBlocks.LASER_STABILIZER);
    public static final RegistryObject<Item> ENERGY_CABLE = blockItem("energy_cable", ModBlocks.ENERGY_CABLE);
    public static final RegistryObject<Item> ENERGY_OUTPUT_PORT =
            blockItem("energy_output_port", ModBlocks.ENERGY_OUTPUT_PORT);
    public static final RegistryObject<Item> ENERGY_INPUT_PORT =
            blockItem("energy_input_port", ModBlocks.ENERGY_INPUT_PORT);
    public static final RegistryObject<Item> SINGULARITY_CONTROLLER =
            blockItem("singularity_controller", ModBlocks.SINGULARITY_CONTROLLER);
    public static final RegistryObject<Item> STARGATE_FRAME = blockItem("stargate_frame", ModBlocks.STARGATE_FRAME);
    public static final RegistryObject<Item> STARGATE_CONTROLLER =
            blockItem("stargate_controller", ModBlocks.STARGATE_CONTROLLER);
    public static final RegistryObject<Item> ARCANE_LAMP = blockItem("arcane_lamp", ModBlocks.ARCANE_LAMP);
    public static final RegistryObject<Item> TIME_BEACON_CONTROLLER =
            ITEMS.register("time_beacon_controller", () -> new LoreBlockItem(
                    ModBlocks.TIME_BEACON_CONTROLLER.get(), new Item.Properties(),
                    "tooltip.arcane_singularity.beacon", "tooltip.arcane_singularity.beacon2",
                    "tooltip.arcane_singularity.beacon3"));
    public static final RegistryObject<Item> CHRONO_RING = blockItem("chrono_ring", ModBlocks.CHRONO_RING);
    public static final RegistryObject<Item> TIME_BARREL = blockItem("time_barrel", ModBlocks.TIME_BARREL);

    public static final RegistryObject<Item> WORLD_CORE_CONTROLLER = ITEMS.register("world_core_controller",
            () -> new LoreBlockItem(ModBlocks.WORLD_CORE_CONTROLLER.get(), new Item.Properties(),
                    "tooltip.arcane_singularity.world_core", "tooltip.arcane_singularity.world_core2",
                    "tooltip.arcane_singularity.world_core3"));
    public static final RegistryObject<Item> WORLD_CORE_INTERFACE =
            blockItem("world_core_interface", ModBlocks.WORLD_CORE_INTERFACE);
    public static final RegistryObject<Item> WORLD_CORE_CABLE =
            blockItem("world_core_cable", ModBlocks.WORLD_CORE_CABLE);
    public static final RegistryObject<Item> WORLD_CORE_EMITTER =
            blockItem("world_core_emitter", ModBlocks.WORLD_CORE_EMITTER);
    public static final RegistryObject<Item> WORLD_CORE_APEX =
            blockItem("world_core_apex", ModBlocks.WORLD_CORE_APEX);

    public static final RegistryObject<Item> ARCANE_SHARD = ITEMS.register("arcane_shard",
            () -> new LoreItem(new Item.Properties().rarity(Rarity.UNCOMMON),
                    "tooltip.arcane_singularity.shard"));
    public static final RegistryObject<Item> ECHO_OF_THE_END = ITEMS.register("echo_of_the_end",
            () -> new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    "tooltip.arcane_singularity.echo", "tooltip.arcane_singularity.echo2"));
    public static final RegistryObject<Item> FORMATION_TERMINAL = ITEMS.register("formation_terminal",
            () -> new FormationTerminalItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.arcane_singularity.terminal", "tooltip.arcane_singularity.terminal2",
                    "tooltip.arcane_singularity.terminal3"));
    public static final RegistryObject<Item> PAIRING_ROD = ITEMS.register("pairing_rod",
            () -> new PairingRodItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    /** 世界核心：放进世界之核界面的核心槽才允许启动仪式。 */
    public static final RegistryObject<Item> WORLD_CORE = ITEMS.register("world_core",
            () -> new LoreItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant(),
                    "tooltip.arcane_singularity.world_core_item",
                    "tooltip.arcane_singularity.world_core_item2"));
    public static final RegistryObject<Item> ARCANE_CODEX = ITEMS.register("arcane_codex",
            () -> new ArcaneCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    private ModItems() {
    }

    private static RegistryObject<Item> blockItem(String name, RegistryObject<? extends net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
