package com.example.demo.server;


import com.example.demo.handler.HeartbeatServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import sun.misc.BASE64Decoder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

/**
 * Created by ly on 2018/4/3.
 */
public class Server {


        private final static InternalLogger log = InternalLoggerFactory.getInstance(Server.class);
        private static double coefficient = 0.8;
        private static int numberOfCores = Runtime.getRuntime().availableProcessors();
        private static int poolSize = (int) (numberOfCores / (1 - coefficient));
        private static final EventExecutorGroup eventExecutorGroup = new DefaultEventExecutorGroup(poolSize);
        private final AcceptorIdleStateTrigger idleStateTrigger = new AcceptorIdleStateTrigger();
        private int port;

        public Server(int port) {
            this.port = port;
        }

        public void start() {
            String osName = System.getProperty("os.name");
            log.info("服务器的操作系统=" + osName);
            if (osName.equals("Linux")) {
                EventLoopGroup bossGroup = new EpollEventLoopGroup(1);
                EventLoopGroup workerGroup = new EpollEventLoopGroup(4);
                try {
                    ServerBootstrap serverBootstrap = new ServerBootstrap().group(bossGroup, workerGroup)
                            .channel(EpollServerSocketChannel.class)
                            .handler(new LoggingHandler(LogLevel.INFO))
                            .option(ChannelOption.TCP_NODELAY, true)
                            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .option(ChannelOption.RCVBUF_ALLOCATOR, AdaptiveRecvByteBufAllocator.DEFAULT)
                            .localAddress(new InetSocketAddress(port))
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                protected void initChannel(SocketChannel ch) throws Exception {
                                    initSocketChannel(ch);
                                }
                            }).option(ChannelOption.SO_BACKLOG, 128)
                            .childOption(ChannelOption.SO_KEEPALIVE, true);
                    serverBootstrap.bind(port).sync();
                    log.info("服务器开始监听端口：" + port);
                    clearPushCache();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            } else {
                EventLoopGroup bossGroup = new NioEventLoopGroup();
                EventLoopGroup workerGroup = new NioEventLoopGroup();
                try {
                    ServerBootstrap serverBootstrap = new ServerBootstrap()
                            .group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class)
                            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                            .option(ChannelOption.RCVBUF_ALLOCATOR, AdaptiveRecvByteBufAllocator.DEFAULT)
//                        .option(ChannelOption.SO_SNDBUF,32*1024)
                            .handler(new LoggingHandler(LogLevel.INFO))
                            .localAddress(new InetSocketAddress(port))
                            .childHandler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel socketChannel) throws Exception {
                                    initSocketChannel(socketChannel);
                                }
                            }).option(ChannelOption.SO_BACKLOG, 128)
                            .childOption(ChannelOption.SO_KEEPALIVE, true)
                            .childOption(ChannelOption.TCP_NODELAY, true);//禁用nagle算法
                    serverBootstrap.bind(port).sync();
                    log.info("服务器开始监听端口：" + port);
                    clearPushCache();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }

        private void clearPushCache() {
           //TODO 防止pushserver意外被kill掉，清除redis里保存的数据。
        }

        private void initSocketChannel(SocketChannel socketChannel) {
            SSLEngine sslEngine = initSSLContext().createSSLEngine();
            sslEngine.setUseClientMode(false); //服务器端模式
            sslEngine.setNeedClientAuth(false); //不需要验证客户端
            socketChannel.pipeline().addFirst("ssl", new SslHandler(sslEngine));
            ByteBuf delimiter = Unpooled.copiedBuffer("\r\n".getBytes());
            socketChannel.pipeline().addLast(new DelimiterBasedFrameDecoder(1024, delimiter));
            socketChannel.pipeline().addLast("logging", new LoggingHandler(LogLevel.INFO));
            socketChannel.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
            socketChannel.pipeline().addLast("encoder", new StringEncoder());
            socketChannel.pipeline().addLast(new IdleStateHandler(120, 0, 0, TimeUnit.SECONDS));
            socketChannel.pipeline().addLast(eventExecutorGroup, new HeartbeatServerHandler());
            socketChannel.pipeline().addLast(idleStateTrigger);
        }

        private SSLContext initSSLContext() {
            SSLContext sslContext = null;
            try {
                final BASE64Decoder decoder = new BASE64Decoder();
                String pass = new String(decoder.decodeBuffer("asdfdgkjmdnv"), "UTF-8");
                KeyStore ks = KeyStore.getInstance("JKS");
                InputStream ksInputStream = Server.class.getResourceAsStream("server.jks");
                ks.load(ksInputStream, pass.toCharArray());
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                kmf.init(ks, pass.toCharArray());
                sslContext = SSLContext.getInstance("TLSV1.2");
                sslContext.init(kmf.getKeyManagers(), null, null);
            } catch (Exception e) {
                log.error(e.toString());
            }
            if (sslContext != null) {
                log.info("sslContext初始化成功");
            }
            return sslContext;
        }


}
