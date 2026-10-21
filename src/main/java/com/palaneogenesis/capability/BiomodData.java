package com.palaneogenesis.capability;

public class BiomodData implements IBiomodData {

	private boolean injected = false;
	private boolean sessionOpen = false;
	private int sessionContainerId = -1;

	@Override
	public boolean isInjected() {
		return injected;
	}

	@Override
	public void setInjected(boolean injected) {
		this.injected = injected;
	}

	@Override
	public boolean isSessionOpen() {
		return sessionOpen;
	}

	@Override
	public void setSessionOpen(boolean sessionOpen) {
		this.sessionOpen = sessionOpen;
	}

	@Override
	public int getSessionContainerId() {
		return sessionContainerId;
	}

	@Override
	public void setSessionContainerId(int containerId) {
		this.sessionContainerId = containerId;
	}
}
