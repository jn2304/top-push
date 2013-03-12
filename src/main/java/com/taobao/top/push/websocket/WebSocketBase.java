package com.taobao.top.push.websocket;

import org.eclipse.jetty.websocket.WebSocket;

import com.taobao.top.push.Client;
import com.taobao.top.push.MessageTooLongException;
import com.taobao.top.push.NoMessageBufferException;
import com.taobao.top.push.PushManager;
import com.taobao.top.push.UnauthorizedException;
import com.taobao.top.push.messages.Message;

public abstract class WebSocketBase implements WebSocket.OnTextMessage, 
	WebSocket.OnBinaryMessage, 
	WebSocket.OnControl, 
	WebSocket.OnFrame {

	protected FrameConnection frameConnection;
	protected Connection connection;

	protected PushManager manager;
	protected Client client;
	protected WebSocketClientConnection clientConnection;

	public WebSocketBase(PushManager manager, 
			Client client, 
			WebSocketClientConnection clientConnection) {
		this.manager = manager;
		this.client = client;
		this.clientConnection = clientConnection;
	}

	@Override
	public void onClose(int closeCode, String message) {
		this.manager.disconnectClient(this.client, this.clientConnection);
		this.clear();
		System.out.println(String.format("websocket close: %s | %s", closeCode, message));
	}

	@Override
	public void onHandshake(FrameConnection frameConnection) {
		if (this.manager.isReachMaxConnectionCount()) {
			this.clear();
			frameConnection.close(403, "reach max connections");
			System.out.println(String.format("websocket close: %s | %s", 403, "reach max connections"));
			return;
		}
		
		try {
			this.manager.connectClient(this.client, this.clientConnection);
		} catch (UnauthorizedException e) {
			this.clear();
			frameConnection.close(401, e.getMessage());
			System.out.println(String.format("websocket close: %s | %s", 401, e.getMessage()));
			return;
		}
		
		this.frameConnection = frameConnection;
		this.clientConnection.init(this.frameConnection);
	}

	@Override
	public void onOpen(Connection connection) {
		this.connection = connection;
		this.clientConnection.init(connection);
	}

	@Override
	public boolean onControl(byte arg0, byte[] arg1, int arg2, int arg3) {
		if (this.frameConnection.isPing(arg0)) {
			this.receivePing();
		}
		return false;
	}

	@Override
	public abstract void onMessage(String message);

	@Override
	public void onMessage(byte[] data, int offset, int length) {
		// any message use as ping
		this.receivePing();

		try {
			Message msg = this.clientConnection.parse(data, offset, length);

			// TODO:make this judgment understandably, command or not
			if (msg.to == null || msg.to == "") {
				// maybe command message like CONNECT/DISCONNECT
				this.manager.getProcessor().process(msg, this.clientConnection);
			} else {
				// forward message
				// deliver to target client
				this.manager.pendingMessage(msg);
			}
		} catch (MessageTooLongException e) {
			e.printStackTrace();
			this.frameConnection.close(400, e.getMessage());
		} catch (NoMessageBufferException e) {
			e.printStackTrace();
			// https://github.com/wsky/top-push/issues/23
			// ignore no buffer and drop it
			// this.frameConnection.close(400, e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean onFrame(byte arg0, byte arg1, byte[] arg2, int arg3, int arg4) {
		return false;
	}

	// telling client is alive
	private void receivePing() {
		this.clientConnection.receivePing();
		this.client.receivePing();
	}
	
	private void clear() {
		Utils.getClientConnectionPool().release(this.clientConnection);
	}
}