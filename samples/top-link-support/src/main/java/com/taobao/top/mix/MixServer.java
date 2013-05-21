package com.taobao.top.mix;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.taobao.top.link.channel.ServerChannelSender;
import com.taobao.top.link.channel.websocket.WebSocketServerChannel;
import com.taobao.top.link.endpoint.Endpoint;
import com.taobao.top.link.endpoint.EndpointProxy;
import com.taobao.top.link.endpoint.StateHandler;
import com.taobao.top.link.schedule.Scheduler;
import com.taobao.top.push.Client;
import com.taobao.top.push.ClientConnection;
import com.taobao.top.push.ClientStateHandler;
import com.taobao.top.push.Identity;
import com.taobao.top.push.PushManager;

public class MixServer {
	private static PushManager pushManager;
	private static Endpoint serverEndpoint;

	public static void main(String[] args) {
		start();
	}

	public static void start() {
		// whatever, log first
		ServerLoggerFactory loggerFactory = new ServerLoggerFactory();

		// for push support
		pushManager = new PushManager(loggerFactory, 50000, 4, 1000, 1000);
		pushManager.setClientStateHandler(new ClientStateHandler() {
			@Override
			public void onClientPending(Client client) {
			}

			@Override
			public void onClientOffline(Client client) {
			}

			@Override
			public void onClientIdle(Client client) {
			}

			@Override
			public void onClientDisconnect(Client client, ClientConnection clientConnection) {
			}

			@Override
			public Identity onClientConnecting(Map<String, String> headers) throws Exception {
				return new ClientIdentity(headers.get("appkey"));
			}
		});

		// init base server
		// scheduler for business process and loadbalance/attack-prevent
		Scheduler<com.taobao.top.link.endpoint.Identity> scheduler = new Scheduler<com.taobao.top.link.endpoint.Identity>();
		scheduler.setUserMaxPendingCount(100);
		// biz-threadpool
		scheduler.setThreadPool(new ThreadPoolExecutor(20, 300, 300, TimeUnit.SECONDS, new SynchronousQueue<Runnable>()));

		// extended low-level channelHandler
		ServerEndpointChannelHandler channelHandler = new ServerEndpointChannelHandler(loggerFactory);
		channelHandler.setScheduler(scheduler);
		channelHandler.setStateHandler(new StateHandler() {
			@Override
			public void onConnected(EndpointProxy endpoint, ServerChannelSender sender) {
				// build pushClient here, and will call to onClientConnecting
				try {
					Map<String, String> headers = new HashMap<String, String>();
					headers.put("appkey", endpoint.getIdentity().toString());
					pushManager.connectClient(headers, new ClientEndpointConnection(endpoint, sender));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		// your server for talking
		serverEndpoint = new Endpoint(loggerFactory, new ServerIdentity());
		serverEndpoint.setChannelHandler(channelHandler);
		serverEndpoint.setMessageHandler(new ServerMessageHandler());

		// websocket serverchannel
		WebSocketServerChannel serverChannel = new WebSocketServerChannel(loggerFactory, 8080, true);
		// if you want SSL/TLS
		// serverChannel.setSSLContext(sslContext);
		serverEndpoint.bind(serverChannel);
	}

	public static void stop() {
		pushManager.cancelAll();
		serverEndpoint.unbindAll();
	}
}
