package com.example.demo.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;


/**
 * Created by ly on 2017/10/23.
 */

@ChannelHandler.Sharable
public class AcceptorIdleStateTrigger extends ChannelInboundHandlerAdapter {

    private int lose_connect_time = 0;
    private final static InternalLogger log = InternalLoggerFactory.getInstance(AcceptorIdleStateTrigger.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.READER_IDLE) {
                lose_connect_time++;
                SocketChannel channel = (SocketChannel) ctx.channel();
                log.info("240秒没有接收到客户端:"+channel+"的信息了");
                if (lose_connect_time > 2) {
                    ctx.channel().close();
                }
            }else {
                super.userEventTriggered(ctx, evt);
            }
        }
    }
}
