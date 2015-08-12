package nettyexample;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class WebServer {
    private static final CharSequence TYPE_PLAIN = HttpHeaders.newEntity("text/plain; charset=UTF-8");
    private static final CharSequence TYPE_JSON = HttpHeaders.newEntity("application/json; charset=UTF-8");
    private static final CharSequence SERVER_NAME = HttpHeaders.newEntity("Netty");
    private static final CharSequence CONTENT_TYPE_ENTITY = HttpHeaders.newEntity(HttpHeaders.Names.CONTENT_TYPE);
    private static final CharSequence DATE_ENTITY = HttpHeaders.newEntity(HttpHeaders.Names.DATE);
    private static final CharSequence CONTENT_LENGTH_ENTITY = HttpHeaders.newEntity(HttpHeaders.Names.CONTENT_LENGTH);
    private static final CharSequence SERVER_ENTITY = HttpHeaders.newEntity(HttpHeaders.Names.SERVER);
    private final int port;


    /**
     * Creates a new web server.
     *
     * @param port
     */
    public WebServer(final int port) {
        this.port = port;
    }


    public void run() throws Exception {
        if (Epoll.isAvailable()) {
            doRun(new EpollEventLoopGroup(), EpollServerSocketChannel.class);
        } else {
            doRun(new NioEventLoopGroup(), NioServerSocketChannel.class);
        }
    }


    private void doRun(
            final EventLoopGroup loupGroup,
            final Class<? extends ServerChannel> serverChannelClass)
                    throws InterruptedException {

        try {
            final InetSocketAddress inet = new InetSocketAddress(port);

            final ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.option(ChannelOption.SO_REUSEADDR, true);
            b.group(loupGroup).channel(serverChannelClass).childHandler(new Initializer());
            b.option(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE);
            b.childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(true));
            b.childOption(ChannelOption.SO_REUSEADDR, true);
            b.childOption(ChannelOption.MAX_MESSAGES_PER_READ, Integer.MAX_VALUE);

            final Channel ch = b.bind(inet).sync().channel();
            ch.closeFuture().sync();

        } finally {
            loupGroup.shutdownGracefully().sync();
        }
    }


    /**
     * The Initializer class initializes the HTTP channel.
     */
    private static class Initializer extends ChannelInitializer<SocketChannel> {

        /**
         * Initializes the channel pipeline with the HTTP response handlers.
         *
         * @param ch The Channel which was registered.
         */
        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            final ChannelPipeline p = ch.pipeline();
            p.addLast("encoder", new HttpResponseEncoder());
            p.addLast("decoder", new HttpRequestDecoder(4096, 8192, 8192, false));
            p.addLast("handler", new Handler());
        }
    }


    /**
     * The Handler class handles all inbound channel messages.
     */
    private static class Handler extends SimpleChannelInboundHandler<Object> {

        @Override
        public void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
            if (!(msg instanceof HttpRequest)) {
                return;
            }

            final HttpRequest request = (HttpRequest) msg;
            final String uri = request.getUri();

            if (uri.equals("/404")) {
                writeResponse(ctx, request, HttpResponseStatus.NOT_FOUND, TYPE_PLAIN, "not found what what");
            } else if (uri.startsWith("/json")) {
                writeResponse(ctx, request, HttpResponseStatus.OK, TYPE_JSON, "{\"ok\":true,\"test\":{\"foo\":\"bar\"}}");
            } else {
                writeResponse(ctx, request, HttpResponseStatus.OK, TYPE_PLAIN, "You requested: " + uri);
            }
        }

        private static void writeResponse(
                ChannelHandlerContext ctx,
                HttpRequest request,
                HttpResponseStatus status,
                CharSequence contentType,
                String content) {

            final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            final ByteBuf entity = Unpooled.wrappedBuffer(bytes);
            final CharSequence length = HttpHeaders.newEntity(Integer.toString(bytes.length));
            writeResponse(ctx, request, status, entity, contentType, length);
        }

        private static void writeResponse(
                ChannelHandlerContext ctx,
                HttpRequest request,
                HttpResponseStatus status,
                ByteBuf buf,
                CharSequence contentType,
                CharSequence contentLength) {

            // Decide whether to close the connection or not.
            final boolean keepAlive = HttpHeaders.isKeepAlive(request);

            // Build the response object.
            final FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    status,
                    buf,
                    false);

            final ZonedDateTime dateTime = ZonedDateTime.now();
            final DateTimeFormatter formatter = DateTimeFormatter.RFC_1123_DATE_TIME;
            final CharSequence date = HttpHeaders.newEntity(dateTime.format(formatter));

            final HttpHeaders headers = response.headers();
            headers.set(CONTENT_TYPE_ENTITY, contentType);
            headers.set(SERVER_ENTITY, SERVER_NAME);
            headers.set(DATE_ENTITY, date);
            headers.set(CONTENT_LENGTH_ENTITY, contentLength);

            // Close the non-keep-alive connection after the write operation is done.
            if (!keepAlive) {
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } else {
                ctx.writeAndFlush(response, ctx.voidPromise());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }
    }
}