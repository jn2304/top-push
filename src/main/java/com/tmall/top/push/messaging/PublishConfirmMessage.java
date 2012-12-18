package com.tmall.top.push.messaging;

public class PublishConfirmMessage extends Message {
	// support batch
	// "1,2,3"
	public String confirmId;

	@Override
	protected void internalClear() {
		this.confirmId = null;
	}
}
