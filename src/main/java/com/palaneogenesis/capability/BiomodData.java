package com.palaneogenesis.capability;

import com.palaneogenesis.biomod.ability.AbilityTimers;
import com.palaneogenesis.biomod.target.TargetList;

public class BiomodData implements IBiomodData {

	private boolean injected = false;
	private boolean sessionOpen = false;
	private int sessionContainerId = -1;
	private final AbilityTimers timers = new AbilityTimers();
	private final TargetList targets = TargetList.defaults();

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

	@Override
	public AbilityTimers getTimers() {
		return timers;
	}

	@Override
	public TargetList getTargets() {
		return targets;
	}
}
