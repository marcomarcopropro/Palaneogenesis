package com.palaneogenesis.capability;

/** Implementación default de {@link ITransformationData}: un solo booleano en memoria, false al crear la instancia. */
public class TransformationData implements ITransformationData {

	private boolean transformed = false;

	@Override
	public boolean isTransformed() {
		return transformed;
	}

	@Override
	public void setTransformed(boolean transformed) {
		this.transformed = transformed;
	}
}
