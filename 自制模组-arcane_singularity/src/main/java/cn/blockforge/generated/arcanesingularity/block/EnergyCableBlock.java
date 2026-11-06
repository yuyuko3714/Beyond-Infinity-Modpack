package cn.blockforge.generated.arcanesingularity.block;

import cn.blockforge.generated.arcanesingularity.blockentity.EnergyCableBlockEntity;
import cn.blockforge.generated.arcanesingularity.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class EnergyCableBlock extends BaseEntityBlock {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = createConnections();
    private static final VoxelShape CORE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D);
    private static final Map<Direction, VoxelShape> ARMS = createArms();

    public EnergyCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ArcaneProperties.OVERLOADED, false));
    }

    private static Map<Direction, BooleanProperty> createConnections() {
        Map<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, BooleanProperty.create("north"));
        map.put(Direction.EAST, BooleanProperty.create("east"));
        map.put(Direction.SOUTH, BooleanProperty.create("south"));
        map.put(Direction.WEST, BooleanProperty.create("west"));
        map.put(Direction.UP, BooleanProperty.create("up"));
        map.put(Direction.DOWN, BooleanProperty.create("down"));
        return Map.copyOf(map);
    }

    private static Map<Direction, VoxelShape> createArms() {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 8.0D));
        map.put(Direction.SOUTH, Block.box(6.0D, 6.0D, 8.0D, 10.0D, 10.0D, 16.0D));
        map.put(Direction.WEST, Block.box(0.0D, 6.0D, 6.0D, 8.0D, 10.0D, 10.0D));
        map.put(Direction.EAST, Block.box(8.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D));
        map.put(Direction.UP, Block.box(6.0D, 8.0D, 6.0D, 10.0D, 16.0D, 10.0D));
        map.put(Direction.DOWN, Block.box(6.0D, 0.0D, 6.0D, 10.0D, 8.0D, 10.0D));
        return Map.copyOf(map);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTIONS.values().toArray(new BooleanProperty[0]));
        builder.add(ArcaneProperties.OVERLOADED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), canConnect(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(CONNECTIONS.get(direction), isConnectable(neighbourState));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            refreshConnections(level, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbourBlock,
                                BlockPos neighbourPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbourBlock, neighbourPos, movedByPiston);
        if (!level.isClientSide) {
            refreshConnections(level, pos);
        }
    }

    public void refreshConnections(Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof EnergyCableBlock)) {
            return;
        }
        BlockState next = current;
        for (Direction direction : Direction.values()) {
            next = next.setValue(CONNECTIONS.get(direction), canConnect(level, pos, direction));
        }
        if (next != current) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean canConnect(BlockGetter level, BlockPos pos, Direction direction) {
        return isConnectable(level.getBlockState(pos.relative(direction)));
    }

    private static boolean isConnectable(BlockState state) {
        return state.getBlock() instanceof EnergyCableBlock || state.getBlock() instanceof EnergyPortBlock;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyCableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.ENERGY_CABLE.get(), EnergyCableBlockEntity::serverTick);
    }
}
