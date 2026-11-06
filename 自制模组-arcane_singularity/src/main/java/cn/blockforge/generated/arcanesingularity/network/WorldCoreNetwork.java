package cn.blockforge.generated.arcanesingularity.network;

import cn.blockforge.generated.arcanesingularity.ArcaneSingularity;
import cn.blockforge.generated.arcanesingularity.blockentity.WorldCoreControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 世界之核的客户端 → 服务端通道：把界面里输入的命令送到控制器。
 *
 * <p>原版菜单按钮只能传一个整数编号，而命令是一整行文本，所以这里补一个很小的网络包。
 * 服务端收到后会复核玩家与控制器之间的距离，避免有人隔着半个世界发命令。</p>
 */
public final class WorldCoreNetwork {
    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ArcaneSingularity.MOD_ID, "world_core"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId;

    private WorldCoreNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, WorldCoreCommandPacket.class,
                WorldCoreCommandPacket::encode, WorldCoreCommandPacket::decode,
                WorldCoreCommandPacket::handle);
    }

    /** 客户端调用：把命令文本与「是否立刻执行」交给服务端。 */
    public static void sendCommand(BlockPos pos, String command, boolean execute) {
        CHANNEL.sendToServer(new WorldCoreCommandPacket(pos, command, execute));
    }

    public record WorldCoreCommandPacket(BlockPos pos, String command, boolean execute) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeUtf(command == null ? "" : command, 512);
            buf.writeBoolean(execute);
        }

        public static WorldCoreCommandPacket decode(FriendlyByteBuf buf) {
            return new WorldCoreCommandPacket(buf.readBlockPos(), buf.readUtf(512), buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                if (!(player.level().getBlockEntity(pos) instanceof WorldCoreControllerBlockEntity be)) {
                    return;
                }
                if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 256.0D) {
                    return;
                }
                be.submitCommand(player, command);
            });
            context.setPacketHandled(true);
        }
    }
}
