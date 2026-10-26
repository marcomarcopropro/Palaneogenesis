package com.palaneogenesis.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Siluetas en blanco y negro de los ítems, para la línea de ensamblaje de la Estación Bio-Tech: cuando
 * un ingrediente se está fundiendo, la pantalla deja de dibujar el ítem y dibuja su png (la textura del
 * ítem) sin color. Se arma una vez por ítem leyendo la textura del paquete de recursos, se pasa a grises
 * conservando la transparencia y se guarda como textura dinámica. Solo cliente.
 */
final class GhostTextures {

	private record Ghost(ResourceLocation texture, int size) {
	}

	/** null = no se pudo armar (se dibuja nada en vez de reintentar cada fotograma). */
	private static final Map<Item, Ghost> CACHE = new HashMap<>();

	private GhostTextures() {
	}

	/** Dibuja la silueta de 16 x 16 del ítem en (x, y). */
	static void draw(GuiGraphics g, ItemStack stack, int x, int y) {
		Ghost ghost = get(stack);
		if (ghost == null) {
			return;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.85F);
		g.blit(ghost.texture(), x, y, 16, 16, 0.0F, 0.0F, ghost.size(), ghost.size(), ghost.size(), ghost.size());
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
	}

	/** Libera las texturas (al cerrar la pantalla): así también se rehacen tras recargar los recursos. */
	static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (Ghost ghost : CACHE.values()) {
			if (ghost != null) {
				mc.getTextureManager().release(ghost.texture());
			}
		}
		CACHE.clear();
	}

	private static Ghost get(ItemStack stack) {
		Item item = stack.getItem();
		if (CACHE.containsKey(item)) {
			return CACHE.get(item);
		}
		Ghost ghost = null;
		try {
			ghost = build(stack);
		} catch (Exception e) {
			// Sin silueta: la ranura queda vacía en vez de romper la pantalla.
		}
		CACHE.put(item, ghost);
		return ghost;
	}

	private static Ghost build(ItemStack stack) throws java.io.IOException {
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);

		// Ítems planos: una textura por capa (ej. la poción tiene frasco y líquido). Bloques: la textura de partícula.
		List<TextureAtlasSprite> sprites = new ArrayList<>();
		if (!model.isGui3d()) {
			RandomSource random = RandomSource.create();
			for (BakedQuad quad : model.getQuads(null, null, random)) {
				if (!sprites.contains(quad.getSprite())) {
					sprites.add(quad.getSprite());
				}
			}
		}
		if (sprites.isEmpty()) {
			sprites.add(model.getParticleIcon());
		}

		NativeImage out = null;
		int size = 0;
		for (TextureAtlasSprite sprite : sprites) {
			ResourceLocation name = sprite.contents().name();
			ResourceLocation file = new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
			Optional<Resource> resource = mc.getResourceManager().getResource(file);
			if (resource.isEmpty()) {
				continue;
			}
			try (InputStream in = resource.get().open(); NativeImage img = NativeImage.read(in)) {
				int w = img.getWidth();
				if (out == null) {
					size = w;
					out = new NativeImage(size, size, true);
				}
				// Muestreo al tamaño de la primera capa; si la textura es animada se usa el primer cuadro.
				for (int py = 0; py < size; py++) {
					for (int px = 0; px < size; px++) {
						int pixel = img.getPixelRGBA(px * w / size, py * w / size);
						int alpha = (pixel >>> 24) & 0xFF;
						if (alpha == 0) {
							continue;
						}
						int r = pixel & 0xFF;
						int gr = (pixel >> 8) & 0xFF;
						int b = (pixel >> 16) & 0xFF;
						int luma = Math.round(0.299F * r + 0.587F * gr + 0.114F * b);
						out.setPixelRGBA(px, py, (alpha << 24) | (luma << 16) | (luma << 8) | luma);
					}
				}
			}
		}
		if (out == null) {
			return null;
		}

		ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
		ResourceLocation id = new ResourceLocation("palaneogenesis", "ghost/" + key.getNamespace() + "/" + key.getPath());
		mc.getTextureManager().register(id, new DynamicTexture(out));
		return new Ghost(id, size);
	}
}
