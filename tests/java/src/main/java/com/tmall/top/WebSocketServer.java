package com.tmall.top;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;

public class WebSocketServer {
//	private static List<Channel> Frontends = (List<Channel>) Collections
//			.synchronizedList(new ArrayList<Channel>());
//	private static Channel Backend = null;

	public void Run() {
		//fontend
//		ServerBootstrap bootstrap_front = new ServerBootstrap(
//				new NioServerSocketChannelFactory(
//						Executors.newCachedThreadPool(),
//						Executors.newCachedThreadPool()));
//		bootstrap_front.setPipelineFactory(new WebSocketServerPipelineFactory());
//		bootstrap_front.bind(new InetSocketAddress(8080));

		//backend
		ServerBootstrap bootstrap_back = new ServerBootstrap(
				new NioServerSocketChannelFactory(
						Executors.newCachedThreadPool(),
						Executors.newCachedThreadPool()));
		bootstrap_back.setPipelineFactory(new WebSocketServerPipelineFactory());
		bootstrap_back.bind(new InetSocketAddress(9090));
		
		System.out.println("server running at *:8080/*:9090...");
	}

	public class WebSocketServerPipelineFactory implements
			ChannelPipelineFactory {

		public ChannelPipeline getPipeline() throws Exception {
			ChannelPipeline pipeline = Channels.pipeline();
			pipeline.addLast("decoder", new HttpRequestDecoder());
			pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
			pipeline.addLast("encoder", new HttpResponseEncoder());
			pipeline.addLast("handler", new WebSocketServerHandler());
			return pipeline;
		}

	}

	public class WebSocketServerHandler extends SimpleChannelUpstreamHandler {
		private InternalLogger logger = InternalLoggerFactory
				.getInstance(WebSocketServerHandler.class);
		private WebSocketServerHandshaker handshaker;
		private int total = 0;

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
				throws Exception {
			Object msg = e.getMessage();
			if (msg instanceof HttpRequest) {
				handleHttpRequest(ctx, (HttpRequest) msg);
			} else if (msg instanceof WebSocketFrame) {
				handleWebSocketFrame(ctx, (WebSocketFrame) msg);
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
				throws Exception {
			Throwable error = e.getCause();
			// e.getFuture().cancel();
			logger.error("Error", error);
			// error.printStackTrace();
			// if(error instanceof WebSocketHandshakeException)
			// e.getChannel().close();
		}

		private void handleHttpRequest(ChannelHandlerContext ctx,
				HttpRequest req) throws Exception {
			if (req.getMethod() != HttpMethod.GET) {
				sendHttpResponse(ctx, req, new DefaultHttpResponse(
						HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN));
				return;
			}

			if (logger.isDebugEnabled())
				for (java.util.Map.Entry<String, String> h : req.getHeaders()) {
					logger.debug(h.getKey() + " : " + h.getValue());
				}

			// Handshake
			// Subprotocols is null
			// eg: Sec-WebSocket-Protocol: chat, xxx
			// if special subprotocol, handshake will return supportted version.
			// http://tools.ietf.org/html/rfc6455#section-1.9
			// http://static.netty.io/3.5/api/org/jboss/netty/handler/codec/http/websocketx/WebSocketServerHandshakerFactory.html
			String subprotocols = null;
			WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
					getWebSocketLocation(req), subprotocols, false);
			handshaker = wsFactory.newHandshaker(req);

			if (handshaker == null) {
				wsFactory.sendUnsupportedWebSocketVersionResponse(ctx
						.getChannel());
			} else {
				handshaker.handshake(ctx.getChannel(), req).addListener(
						WebSocketServerHandshaker.HANDSHAKE_LISTENER);
			}
		}

		private void handleWebSocketFrame(ChannelHandlerContext ctx,
				WebSocketFrame frame) {
			// deal with Control Frames
			// http://tools.ietf.org/html/rfc6455#section-5.5
			if (frame instanceof CloseWebSocketFrame) {
				// CLose Frame
				handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
				// hangup
				ctx.getChannel().close();
				return;
			} else if (frame instanceof PingWebSocketFrame) {
				// about ping pong
				// http://tools.ietf.org/html/rfc6455#section-5.5.2
				// ctx.getChannel().write(new
				// PongWebSocketFrame(frame.getBinaryData()));
				logger.info("receive ping");
				return;
			} else if (frame instanceof TextWebSocketFrame) {
				// deal with Data Frames
				// http://tools.ietf.org/html/rfc6455#section-5.6
				String request = ((TextWebSocketFrame) frame).getText();
				if (logger.isInfoEnabled()) {
					logger.info(String.format("Channel %s received %s", ctx
							.getChannel().getId(), request));
				}
				// HACK:push messages to client
				// 100W raise OOM?
				// -Xmx1024m or more
				if (this.total == 0) {
					this.total = Integer.parseInt(request);
					return;
				}
				Channel channel = ctx.getChannel();
				TextWebSocketFrame r = new TextWebSocketFrame(request);
				for (int i = 0; i < this.total; i++) {
					// many message will course OOM, data pinned whill
					// unwritable.but will resue as buffer.
					channel.write(r);
				}
				logger.info(String.format(
						"Channel %s send %s meesages: length=%s", ctx
								.getChannel().getId(), this.total, request
								.length()));
			} else if (frame instanceof BinaryWebSocketFrame) {
				throw new UnsupportedOperationException(String.format(
						"%s frame types not supported", frame.getClass()
								.getName()));
			} else if (frame instanceof ContinuationWebSocketFrame) {
				return;
			} else {
				throw new UnsupportedOperationException(String.format(
						"%s frame types not supported", frame.getClass()
								.getName()));
			}
		}

		private void sendHttpResponse(ChannelHandlerContext ctx,
				HttpRequest req, HttpResponse res) {
			if (res.getStatus().getCode() != 200) {
				res.setContent(ChannelBuffers.copiedBuffer(res.getStatus()
						.toString(), CharsetUtil.UTF_8));
				HttpHeaders.setContentLength(res, res.getContent()
						.readableBytes());
			}

			ChannelFuture f = ctx.getChannel().write(res);

			if (res.getStatus().getCode() != 200) {
				f.addListener(ChannelFutureListener.CLOSE);
			}
		}

		private String getWebSocketLocation(HttpRequest req) {
			return "ws://" + req.getHeader(HttpHeaders.Names.HOST)
					+ "websocket";
		}
	}
}
