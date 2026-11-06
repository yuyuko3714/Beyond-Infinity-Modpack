package cn.blockforge.generated.arcanesingularity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 世界之核专属管道。
 *
 * <p>它和符文导管是两种东西：两种管道<b>互不连接</b>，能量网络也永远不会从它这里抄近路。
 * 它只认自己这种管道、世界之核的三个专属接口，以及前三座结构的控制器方块——
 * 也就是「用来检测多方块结构开启状态」的那根信号线。</p>
 */
public class WorldCoreCableBlock extends Block {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = createConnections();
    private static final VoxelShape CORE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D);
    private static final Map<Direction, VoxelShape> ARMS = createArms();

    public WorldCoreCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState());
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
            state = state.setValue(CONNECTIONS.get(direction),
                    canConnect(context.getLevel(), context.getClickedPos(), direction));
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

    private void refreshConnections(Level level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof WorldCoreCableBlock)) {
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

    /** 只认同类管道、专属接口和前三座结构的控制器——符文导管绝不接入。 */
    private static boolean isConnectable(BlockState state) {
        return state.getBlock() instanceof WorldCoreCableBlock
                || state.getBlock() instanceof WorldCoreInterfaceBlock
                || state.getBlock() instanceof SingularityControllerBlock
                || state.getBlock() instanceof StargateControllerBlock
                || state.getBlock() instanceof TimeBeaconControllerBlock
                || state.getBlock() instanceof WorldCoreControllerBlock;
    }
}
