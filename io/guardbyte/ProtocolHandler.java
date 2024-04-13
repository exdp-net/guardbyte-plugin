package io.guardbyte;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import com.google.common.collect.Lists;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;

public class ProtocolHandler {
    private static final Class<Object> minecraftServerClass = ReflectionUtils.getUntypedClass("{nms}.MinecraftServer");
    private static final Class<Object> serverConnectionClass = ReflectionUtils.getUntypedClass("{nms}" + (ReflectionUtils.isNewerPackage() ? ".network" : "") + ".ServerConnection");
    private static final ReflectionUtils.FieldAccessor<Object> getMinecraftServer = ReflectionUtils.getField("{obc}.CraftServer", minecraftServerClass, 0);
    private static final ReflectionUtils.FieldAccessor<Object> getServerConnection = ReflectionUtils.getField(minecraftServerClass, serverConnectionClass, 0);
    private static final ReflectionUtils.MethodInvoker getNetworkMarkers;
    @SuppressWarnings("rawtypes")
    private static final ReflectionUtils.FieldAccessor<List> networkManagersFieldAccessor;
    static {
        getNetworkMarkers = !ReflectionUtils.isNewerPackage() ? ReflectionUtils.getTypedMethod(serverConnectionClass, null, List.class, serverConnectionClass) : null;
        networkManagersFieldAccessor = ReflectionUtils.isNewerPackage() ? ReflectionUtils.getField(serverConnectionClass, List.class, 0) : null;
    }
    private static final Class<Object> networkManager = ReflectionUtils.getUntypedClass(ReflectionUtils.isNewerPackage() ? "net.minecraft.network.NetworkManager" : "{nms}.NetworkManager");
    private static final ReflectionUtils.FieldAccessor<SocketAddress> socketAddressFieldAccessor = ReflectionUtils.getField(networkManager, SocketAddress.class, 0);
    private List<Object> networkManagers;
    private List<Channel> serverChannels = Lists.newArrayList();
    private ChannelInboundHandlerAdapter serverChannelHandler;
    private ChannelInitializer<Channel> beginInitProtocol;
    private ChannelInitializer<Channel> endInitProtocol;
    protected Plugin plugin;

    public ProtocolHandler(final Plugin plugin) {
        this.plugin = plugin;
        try {
            registerChannelHandler();
        } catch (IllegalArgumentException ex) {

            new BukkitRunnable() {
                @Override
                public void run() {
                    registerChannelHandler();
                }
            }.runTask(plugin);
        }
    }

    private void createServerChannelHandler() {
        endInitProtocol = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                try {
                    if (ReflectionUtils.isNewerPackage()){
                        synchronized (networkManagers) {
                            channel.pipeline().addFirst("haproxy-decoder", new HAProxyMessageDecoder());
                            channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
                        }
                        return;
                    }

                    channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
                    channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Cannot inject incoming channel " + channel, e);
                }
            }
        };

        beginInitProtocol = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                channel.pipeline().addLast(endInitProtocol);
            }
        };

        serverChannelHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Channel channel = (Channel) msg;

                channel.pipeline().addFirst(beginInitProtocol);
                ctx.fireChannelRead(msg);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void registerChannelHandler() {
        Object mcServer = getMinecraftServer.get(Bukkit.getServer());
        Object serverConnection = getServerConnection.get(mcServer);
        boolean looking = true;

        networkManagers = ReflectionUtils.isNewerPackage() ? networkManagersFieldAccessor.get(serverConnection) : (List<Object>) getNetworkMarkers.invoke(null, serverConnection);
        createServerChannelHandler();

        for (int i = 0; looking; i++) {
            List<Object> list = ReflectionUtils.getField(serverConnection.getClass(), List.class, i).get(serverConnection);

            for (Object item : list) {
                if (!ChannelFuture.class.isInstance(item))
                    break;

                Channel serverChannel = ((ChannelFuture) item).channel();

                serverChannels.add(serverChannel);
                serverChannel.pipeline().addFirst(serverChannelHandler);
                looking = false;

                plugin.getLogger().info("Server channel located and handler added successfully.");
            }
        }
    }

    private final HAProxyMessageHandler HAPROXY_MESSAGE_HANDLER = new HAProxyMessageHandler();

    @ChannelHandler.Sharable
    public class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if(!(msg instanceof HAProxyMessage)){
                super.channelRead(ctx, msg);
                return;
            }

            try {
                final HAProxyMessage message = (HAProxyMessage) msg;
                socketAddressFieldAccessor.set(ctx.channel().pipeline().get("packet_handler"), new InetSocketAddress(message.sourceAddress(), message.sourcePort()));
            }catch (Exception exception){
                ctx.channel().close();
                plugin.getLogger().warning("An error occurred: Unable to set the IP address from the 'HAProxyMessage'.");
                exception.printStackTrace();
            }
        }
    };
}
