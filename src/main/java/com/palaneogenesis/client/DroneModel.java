package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Dónde están los archivos del modelo del dron, tal como salen de Blockbench (formato Bedrock, que GeckoLib lee
 * directo): geo/drone.geo.json, animations/drone.animation.json y textures/entity/drone.png (128x128; el geo
 * declara 32x32 y GeckoLib normaliza las UV, así que la diferencia de tamaño no importa).
 */
public final class DroneModel extends GeoModel<DroneAnimatable> {

	private static final ResourceLocation MODEL = new ResourceLocation(Palaneogenesis.MOD_ID, "geo/drone.geo.json");
	private static final ResourceLocation TEXTURE = new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/drone.png");
	private static final ResourceLocation ANIMATION = new ResourceLocation(Palaneogenesis.MOD_ID, "animations/drone.animation.json");

	@Override
	public ResourceLocation getModelResource(DroneAnimatable animatable) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(DroneAnimatable animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(DroneAnimatable animatable) {
		return ANIMATION;
	}
}
