package com.palaneogenesis.capability;

/** Implementación default de {@link ITransformationData}: un solo booleano en memoria, false al crear la instancia. */
public class TransformationData implements ITransformationData {

	private boolean transformed = false;

	private int recentToggleCount = 0;
	private int lastToggleTick = Integer.MIN_VALUE;
	private int maxHealthPenaltyHearts = 0;

	@Override
	public boolean isTransformed() {
		return transformed;
	}

	@Override
	public void setTransformed(boolean transformed) {
		this.transformed = transformed;
	}

	@Override
	public int getRecentToggleCount() {
		return recentToggleCount;
	}

	@Override
	public void setRecentToggleCount(int count) {
		this.recentToggleCount = count;
	}

	@Override
	public int getLastToggleTick() {
		return lastToggleTick;
	}

	@Override
	public void setLastToggleTick(int tick) {
		this.lastToggleTick = tick;
	}

	@Override
	public int getMaxHealthPenaltyHearts() {
		return maxHealthPenaltyHearts;
	}

	@Override
	public void setMaxHealthPenaltyHearts(int hearts) {
		this.maxHealthPenaltyHearts = hearts;
	}
}