package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Tooltip of the "Book of Palaneogénesis" (Patchouli guide book item with our book id in its NBT).
 *
 * - The title (first line) is drawn with a blue gradient instead of plain white.
 * - Internal labels are hidden: the "Book ID: ..." line Patchouli adds, and the vanilla advanced-tooltip
 *   lines (registry id and NBT tag count) that only show up with F3+H. The player only sees the
 *   book's name and subtitle.
 *
 * Client only, purely cosmetic. Any other Patchouli book keeps its normal tooltip.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class BookTooltipEvents {

	private static final ResourceLocation PATCHOULI_GUIDE_BOOK = new ResourceLocation("patchouli", "guide_book");
	private static final String BOOK_NBT_KEY = "patchouli:book";
	private static final String OUR_BOOK_ID = Palaneogenesis.MOD_ID + ":palaneogenesis_book";

	/** Gradient ends (RGB): a vivid medium blue to a soft sky blue - not dark navy, not near-white. */
	private static final int COLOR_START = 0x2F6BFF;
	private static final int COLOR_END = 0x5BB8FF;

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		if (!PATCHOULI_GUIDE_BOOK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
			return;
		}
		CompoundTag tag = stack.getTag();
		if (tag == null || !OUR_BOOK_ID.equals(tag.getString(BOOK_NBT_KEY))) {
			return;
		}
		List<Component> lines = event.getToolTip();
		if (lines.isEmpty()) {
			return;
		}
		lines.set(0, gradient(lines.get(0).getString()));
		lines.removeIf(BookTooltipEvents::isInternalLine);
	}

	private static boolean isInternalLine(Component line) {
		String text = line.getString();
		if (text.startsWith("Book ID: ") || text.equals(PATCHOULI_GUIDE_BOOK.toString())) {
			return true;
		}
		return line.getContents() instanceof TranslatableContents translatable
			&& translatable.getKey().equals("item.nbt_tags");
	}

	private static MutableComponent gradient(String text) {
		MutableComponent out = Component.empty();
		int length = text.length();
		for (int i = 0; i < length; i++) {
			float t = length <= 1 ? 0.0F : (float) i / (float) (length - 1);
			out.append(Component.literal(String.valueOf(text.charAt(i)))
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerpColor(COLOR_START, COLOR_END, t)))));
		}
		return out;
	}

	private static int lerpColor(int from, int to, float t) {
		int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerp(from & 0xFF, to & 0xFF, t);
		return (r << 16) | (g << 8) | b;
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}
}
