package com.example.demo.handler;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import com.example.demo.data.RequestHead;

/**
 * Created by ly on 2018/4/3.
 */
public class HeartbeatServerHandler extends ChannelInboundHandlerAdapter{

    private final static InternalLogger log = InternalLoggerFactory.getInstance(HeartbeatServerHandler.class);
    private static final ByteBuf HEARTBEAT_SEQUENCE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("HB\r\n",
            CharsetUtil.UTF_8));
    private Gson gson = new Gson();
    static final String REGISTER = "register";

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        SocketChannel channel = (SocketChannel) ctx.channel();
        log.info("客户端"+channel+"与pushServer建立长连接！");
      //TODO 数据的保存
        ctx.fireChannelActive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //TODO 数据的删除
        cause.printStackTrace();
        SocketChannel channel = (SocketChannel) ctx.channel();
        log.info("客户端"+channel+"与服务器连接发生异常，异常原因："+cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ctx.close();
        SocketChannel channel = (SocketChannel) ctx.channel();
        log.info("客户端"+channel+"与pushServer的长连接断开！");
        //TODO 数据的删除
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = msg.toString();
        if ("HB".equals(message)) {
            SocketChannel session = (SocketChannel) ctx.channel();
            log.info("服务端收到对应的的session心跳："+session);
            ctx.channel().writeAndFlush(HEARTBEAT_SEQUENCE.duplicate());
           log.info("给对应的session返回心跳："+session);
        } else {
            String result = analyzeData(message, ctx);
            log.info("pushServer给客户端的回复:----" + result);
            if (result != null && !"".equals(result.trim())) {
                ByteBuf RESULT = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer(result+"\r\n" ,
                        CharsetUtil.UTF_8));
                ctx.channel().writeAndFlush(RESULT.duplicate());
            }
        }
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    private String analyzeData(String text, ChannelHandlerContext ctx) {
        String result = "";
        try {
            JSONObject request = null;
            try {
                request = new JSONObject(text);
            } catch (JSONException e) {
                log.error("============>Data analyze error:: " +text);
                e.printStackTrace();
            }
            RequestHead head;
            if (request != null) {
                head = gson.fromJson(request.getString("head"), RequestHead.class);
                String body = request.getString("body");
                String msgType = head.getMsgType();

                switch (msgType) {
                    case REGISTER:
                        result = register(head, body, ctx);
                        break;
                    default:
                        result = control(head, body, ctx, msgType);
                        break;
                }
            }
        } catch (JSONException e) {
            log.error("============>Data analyze error:: " +text);
            e.printStackTrace();
        }
        return result;
    }

    private String control(RequestHead head, String body, ChannelHandlerContext ctx, String msgType) {
        return "control";
    }

    private String register(RequestHead head, String body, ChannelHandlerContext ctx) {
        return "register";
    }

}

