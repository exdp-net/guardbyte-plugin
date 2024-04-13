package io.guardbyte.haproxy.tinyprotocol;

import io.guardbyte.haproxy.SpigotImplementor;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.sql.Ref;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.collect.Lists;
public class TinyProtocol {
	private static final Class<Object> minecraftServerClass = Reflection.getUntypedClass("{nms}.MinecraftServer");
	private static final Class<Object> serverConnectionClass = Reflection.getUntypedClass("{nms}" + (Reflection.isNewerPackage() ? ".network" : "") + ".ServerConnection");
	private static final Reflection.FieldAccessor<Object> getMinecraftServer = Reflection.getField("{obc}.CraftServer", minecraftServerClass, 0);
	private static final Reflection.FieldAccessor<Object> getServerConnection = Reflection.getField(minecraftServerClass, serverConnectionClass, 0);
	private static final Reflection.MethodInvoker getNetworkMarkers;
	private static final Reflection.FieldAccessor<List> networkManagersFieldAccessor;
	static {
		getNetworkMarkers = !Reflection.isNewerPackage() ? Reflection.getTypedMethod(serverConnectionClass, null, List.class, serverConnectionClass) : null;
		networkManagersFieldAccessor = Reflection.isNewerPackage() ? Reflection.getField(serverConnectionClass, List.class, 0) : null;
	}
	private static final Class<Object> networkManager = Reflection.getUntypedClass(Reflection.isNewerPackage() ? "net.minecraft.network.NetworkManager" : "{nms}.NetworkManager");
	private static final Reflection.FieldAccessor<SocketAddress> socketAddressFieldAccessor = Reflection.getField(networkManager, SocketAddress.class, 0);
	private List<Object> networkManagers;
	private List<Channel> serverChannels = Lists.newArrayList();
	private ChannelInboundHandlerAdapter serverChannelHandler;
	private ChannelInitializer<Channel> beginInitProtocol;
	private ChannelInitializer<Channel> endInitProtocol;
	protected Plugin plugin;
	public TinyProtocol(final Plugin plugin) {
		this.plugin = plugin;

		plugin.getLogger().info(Reflection.isNewerPackage() + " | " + Reflection.NMS_PREFIX);

		try {
			plugin.getLogger().info("Proceeding with the server channel injection...");
			registerChannelHandler();
		} catch (IllegalArgumentException ex) {
			plugin.getLogger().info("Delaying server channel injection due to late bind.");

			new BukkitRunnable() {
				@Override
				public void run() {
					registerChannelHandler();
					plugin.getLogger().info("Late bind injection successful.");
				}
			}.runTask(plugin);
		}
	}

	private void createServerChannelHandler() {
		// Handle connected channels
		endInitProtocol = new ChannelInitializer<Channel>() {

			@Override
			protected void initChannel(Channel channel) throws Exception {
				try {
					if (Reflection.isNewerPackage()){
						synchronized (networkManagers) {
							// Adding the decoder to the pipeline
							channel.pipeline().addFirst("haproxy-decoder", new HAProxyMessageDecoder());
							// Adding the proxy message handler to the pipeline too
							channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
						}
						return;
					}

					// Adding the decoder to the pipeline
					channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
					// Adding the proxy message handler to the pipeline too
					channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
				} catch (Exception e) {
					plugin.getLogger().log(Level.SEVERE, "Cannot inject incoming channel " + channel, e);
				}
			}

		};

		// This is executed before Minecraft's channel handler
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

				// Prepare to initialize ths channel
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

		// We need to synchronize against this list
		networkManagers = Reflection.isNewerPackage() ? networkManagersFieldAccessor.get(serverConnection) : (List<Object>) getNetworkMarkers.invoke(null, serverConnection);
		createServerChannelHandler();

		// Find the correct list, or implicitly throw an exception
		for (int i = 0; looking; i++) {
			List<Object> list = Reflection.getField(serverConnection.getClass(), List.class, i).get(serverConnection);

			for (Object item : list) {
				if (!ChannelFuture.class.isInstance(item))
					break;

				// Channel future that contains the server connection
				Channel serverChannel = ((ChannelFuture) item).channel();

				serverChannels.add(serverChannel);
				serverChannel.pipeline().addFirst(serverChannelHandler);
				looking = false;

				plugin.getLogger().info("Found the server channel and added the handler. Injection successfully!");
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

				// Set the SocketAddress field of the NetworkManager ("packet_handler" handler) to the client address
				socketAddressFieldAccessor.set(ctx.channel().pipeline().get("packet_handler"), new InetSocketAddress(message.sourceAddress(), message.sourcePort()));
			}catch (Exception exception){
				// Closing the channel because we do not want people on the server with a proxy ip
				ctx.channel().close();

				// Logging for the lovely server admins :)
				plugin.getLogger().warning("Error: The server was unable to set the IP address from the 'HAProxyMessage'. Therefore we closed the channel.");
				exception.printStackTrace();
			}
		}
	};
}
